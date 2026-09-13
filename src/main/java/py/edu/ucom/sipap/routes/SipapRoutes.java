package py.edu.ucom.sipap.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import py.edu.ucom.sipap.domain.MensajeTransferencia;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.domain.SolicitudTransferencia;
import py.edu.ucom.sipap.processor.BankResponseProcessor;
import py.edu.ucom.sipap.processor.DateValidationProcessor;
import py.edu.ucom.sipap.processor.IntakeProcessor;
import py.edu.ucom.sipap.processor.ResultLookupProcessor;
import py.edu.ucom.sipap.processor.TransactionIdProcessor;
import py.edu.ucom.sipap.service.TransactionStore;

import java.time.Clock;

public class SipapRoutes extends RouteBuilder {
    private final TransactionStore store;
    private final Clock clock;
    private final boolean httpEnabled;
    private final String entryEndpoint;
    private final String auditEndpoint;
    private final String itauEndpoint;
    private final String atlasEndpoint;
    private final String familiarEndpoint;
    private final String bankRequestEndpoint;

    public SipapRoutes(TransactionStore store, Clock clock, boolean httpEnabled,
                       String entryEndpoint, String auditEndpoint,
                       String itauEndpoint, String atlasEndpoint, String familiarEndpoint,
                       String bankRequestEndpoint) {
        this.store = store;
        this.clock = clock;
        this.httpEnabled = httpEnabled;
        this.entryEndpoint = entryEndpoint;
        this.auditEndpoint = auditEndpoint;
        this.itauEndpoint = itauEndpoint;
        this.atlasEndpoint = atlasEndpoint;
        this.familiarEndpoint = familiarEndpoint;
        this.bankRequestEndpoint = bankRequestEndpoint;
    }

    @Override
    public void configure() {
        ObjectMapper mapper = new ObjectMapper();

        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "sipap.error",
                        "Error en ${header.SipapTransactionId}: ${exception.message}")
                .process(exchange -> {
                    String id = exchange.getMessage().getHeader(
                            TransactionIdProcessor.TRANSACTION_ID_HEADER, String.class);
                    var result = py.edu.ucom.sipap.domain.ResultadoTransferencia.errorBanco(
                            id, "Error interno de integración");
                    store.save(result);
                    exchange.getMessage().setBody(result);
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                });

        if (httpEnabled) {
            configureHttpApi();
        }

        from("direct:api-intake")
                .routeId("api-intake-and-business-rules")
                .process(new IntakeProcessor(mapper, store))
                .choice()
                    .when(header(IntakeProcessor.ACCEPTED_HEADER).isEqualTo(true))
                        .setBody(header(IntakeProcessor.BROKER_PAYLOAD_HEADER))
                        .to("seda:publish-accepted?waitForTaskToComplete=Never")
                        .setExchangePattern(ExchangePattern.InOut)
                        .setBody(exchangeProperty(IntakeProcessor.API_RESULT_PROPERTY))
                    .endChoice()
                    .otherwise()
                        .setBody(exchangeProperty(IntakeProcessor.API_RESULT_PROPERTY))
                .end();

        from("seda:publish-accepted")
                .routeId("publish-to-artemis-and-audit")
                .multicast().stopOnException()
                    .to(entryEndpoint, auditEndpoint)
                .end();

        from(entryEndpoint)
                .routeId("artemis-entry-dispatcher")
                .unmarshal().json(MensajeTransferencia.class)
                .choice()
                    .when(simple("${body.bancoDestino} == '0015'"))
                        .marshal().json().to(itauEndpoint)
                    .when(simple("${body.bancoDestino} == '0007'"))
                        .marshal().json().to(atlasEndpoint)
                    .when(simple("${body.bancoDestino} == '0020'"))
                        .marshal().json().to(familiarEndpoint)
                .end();

        configureBankConsumer(itauEndpoint, "ITAU", "0015", "itau-consumer");
        configureBankConsumer(atlasEndpoint, "ATLAS", "0007", "atlas-consumer");
        configureBankConsumer(familiarEndpoint, "FAMILIAR", "0020", "familiar-consumer");

        from("direct:consume-bank-transfer")
                .routeId("validate-date-and-call-bank")
                .unmarshal().json(MensajeTransferencia.class)
                .process(new DateValidationProcessor(clock, store))
                .log("VALIDACIÓN FECHA ${header.SipapTransactionId}: ${header.SipapDateValid}")
                .choice()
                    .when(header(DateValidationProcessor.DATE_VALID_HEADER).isEqualTo(true))
                        .setBody(simple("${exchangeProperty.SipapOriginalMessage.transferencia}"))
                        .marshal().json()
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .setHeader("X-Transaction-Id", header(TransactionIdProcessor.TRANSACTION_ID_HEADER))
                        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                        .toD(bankRequestEndpoint)
                        .log("RESPUESTA BANCO ${header.SipapTransactionId}: HTTP ${header.CamelHttpResponseCode} ${body}")
                        .process(new BankResponseProcessor(store))
                    .otherwise()
                        .log(LoggingLevel.WARN, "sipap.consumer", "${body}")
                .end();
    }

    private void configureHttpApi() {
        from("netty-http:http://0.0.0.0:8080/api/transferencias?httpMethodRestrict=POST")
                .routeId("rest-post-transfer")
                .unmarshal().json(SolicitudTransferencia.class)
                .to("direct:api-intake")
                .process(exchange -> {
                    ResultadoTransferencia result = exchange.getMessage()
                            .getBody(ResultadoTransferencia.class);
                    int status = switch (result.estado()) {
                        case "ACEPTADA_PARA_PROCESAMIENTO" -> 202;
                        case "DUPLICADA" -> 409;
                        default -> 422;
                    };
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, status);
                })
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .marshal().json();

        from("netty-http:http://0.0.0.0:8080/api/resultados?httpMethodRestrict=GET")
                .routeId("rest-get-result")
                .process(new ResultLookupProcessor(store))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .marshal().json();
    }

    private void configureBankConsumer(String endpoint, String bankName,
                                       String bankCode, String routeId) {
        from(endpoint)
                .routeId(routeId)
                .setHeader("SipapBankName", constant(bankName))
                .setHeader("SipapBankCode", constant(bankCode))
                .to("direct:consume-bank-transfer");
    }
}
