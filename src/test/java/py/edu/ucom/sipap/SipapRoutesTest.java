package py.edu.ucom.sipap;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.domain.SolicitudTransferencia;
import py.edu.ucom.sipap.routes.SipapRoutes;
import py.edu.ucom.sipap.samples.QrSamples;
import py.edu.ucom.sipap.service.TransactionStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SipapRoutesTest extends CamelTestSupport {
    private static final String TODAY = "2026-08-13";
    private TransactionStore store;

    @Override
    protected RouteBuilder createRouteBuilder() {
        store = new TransactionStore();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-13T15:00:00Z"),
                ZoneId.of("America/Asuncion"));
        return new SipapRoutes(store, fixedClock, false,
                "direct:entry", "mock:audit",
                "direct:itau", "direct:atlas", "direct:familiar",
                "mock:bank");
    }

    @BeforeEach
    void configureBankMock() {
        getMockEndpoint("mock:bank").whenAnyExchangeReceived(exchange -> {
            String id = exchange.getMessage().getHeader("X-Transaction-Id", String.class);
            int status = id != null && id.startsWith("TX-REJECT") ? 422 : 200;
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, status);
            exchange.getMessage().setBody(status == 200
                    ? "{\"estado\":\"APROBADA\"}" : "{\"estado\":\"RECHAZADA\"}");
        });
    }

    @Test
    void acceptsValidRestRequestAndPublishesCanonicalMessage() throws Exception {
        MockEndpoint bank = getMockEndpoint("mock:bank");
        bank.expectedMessageCount(1);
        bank.expectedHeaderReceived("X-Transaction-Id", "TX000001");

        ResultadoTransferencia response = send("TX000001", TODAY,
                QrSamples.validDynamic("0015", "1234567890", 10_000_000), null);

        assertEquals("ACEPTADA_PARA_PROCESAMIENTO", response.estado());
        assertEquals("Transferencia enviada a la cola", response.mensaje());
        bank.assertIsSatisfied();
        assertEquals("PROCESADA", waitForResult("TX000001").estado());
    }

    @Test
    void rejectsAmountAboveMaximumBeforeBroker() throws Exception {
        getMockEndpoint("mock:bank").expectedMessageCount(0);
        ResultadoTransferencia response = send("TX-AMOUNT", TODAY,
                QrSamples.validDynamic("0015", "1234567890", 10_000_001), null);

        assertEquals("RECHAZADA", response.estado());
        assertEquals("El monto supera máximo permitido", response.mensaje());
        getMockEndpoint("mock:bank").assertIsSatisfied();
    }

    @Test
    void staticQrUsesAmountFromRestRequest() throws Exception {
        MockEndpoint bank = getMockEndpoint("mock:bank");
        bank.expectedMessageCount(1);
        send("TX-STATIC", TODAY, QrSamples.validStatic("0020", "5555555555"), 5000L);
        bank.assertIsSatisfied();
        String json = bank.getExchanges().get(0).getIn().getBody(String.class);
        assertTrue(json.contains("\"transaction_amount\":5000"), json);
    }

    @Test
    void rejectsTransferWithDifferentDateWithoutCallingBank() throws Exception {
        getMockEndpoint("mock:bank").expectedMessageCount(0);
        ResultadoTransferencia response = send("TX-OLD", "2026-08-12",
                QrSamples.validDynamic("0007", "9876543210", 25000), null);

        assertEquals("ACEPTADA_PARA_PROCESAMIENTO", response.estado());
        assertEquals("RECHAZADA_FECHA", waitForResult("TX-OLD").estado());
        getMockEndpoint("mock:bank").assertIsSatisfied();
    }

    @Test
    void convertsRejectedBankResponseToFinalError() throws Exception {
        send("TX-REJECT-01", TODAY,
                QrSamples.validDynamic("0015", "1234567890", 20000), null);
        ResultadoTransferencia result = waitForResult("TX-REJECT-01");
        assertEquals("ERROR_BANCO", result.estado());
        assertTrue(result.mensaje().contains("HTTP 422"));
    }

    @Test
    void duplicateTransactionIsNotProcessedTwice() throws Exception {
        MockEndpoint bank = getMockEndpoint("mock:bank");
        bank.expectedMessageCount(1);
        send("TX-DUP", TODAY, QrSamples.validDynamic("0015", "1234567890", 100), null);
        ResultadoTransferencia duplicate = send("TX-DUP", TODAY,
                QrSamples.validDynamic("0015", "1234567890", 100), null);

        assertEquals("DUPLICADA", duplicate.estado());
        assertEquals(1, store.receivedCount());
        bank.assertIsSatisfied();
    }

    @Test
    void preservesCorrelationIdAcrossBrokerAndMockRequest() throws Exception {
        MockEndpoint audit = getMockEndpoint("mock:audit");
        MockEndpoint bank = getMockEndpoint("mock:bank");
        audit.expectedMessageCount(1);
        audit.message(0).body().contains("TX-CORRELATION");
        bank.expectedHeaderReceived("X-Transaction-Id", "TX-CORRELATION");
        send("TX-CORRELATION", TODAY,
                QrSamples.validDynamic("0020", "5555555555", 300), null);
        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(waitForResult("TX-CORRELATION"));
    }

    @Test
    void rejectsInvalidQrAndUnknownBank() {
        ResultadoTransferencia malformed = send("TX-BAD-QR", TODAY, "0005ABC", null);
        ResultadoTransferencia unknown = send("TX-BAD-BANK", TODAY, QrSamples.unknownBank(), null);
        assertEquals("RECHAZADA", malformed.estado());
        assertTrue(malformed.mensaje().startsWith("QR inválido:"));
        assertEquals("RECHAZADA", unknown.estado());
        assertTrue(unknown.mensaje().contains("Banco destino desconocido"));
    }

    private ResultadoTransferencia send(String id, String date, String qr, Long amount) {
        SolicitudTransferencia request = new SolicitudTransferencia(id, date, qr, amount);
        return template.requestBody("direct:api-intake", request, ResultadoTransferencia.class);
    }

    private ResultadoTransferencia waitForResult(String id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        ResultadoTransferencia result;
        while ((result = store.find(id)) == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return result;
    }
}
