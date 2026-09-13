package py.edu.ucom.sipap.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import py.edu.ucom.sipap.domain.MensajeTransferencia;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.domain.SolicitudTransferencia;
import py.edu.ucom.sipap.domain.Transferencia;
import py.edu.ucom.sipap.service.TransactionStore;
import py.edu.ucom.sipap.tlv.QrParser;
import py.edu.ucom.sipap.validation.TransferValidationProcessor;

public class IntakeProcessor implements Processor {
    public static final String ACCEPTED_HEADER = "SipapAccepted";
    public static final String BROKER_PAYLOAD_HEADER = "SipapBrokerPayload";
    public static final String API_RESULT_PROPERTY = "SipapApiResult";

    private final ObjectMapper mapper;
    private final TransactionStore store;
    private final QrParser parser = new QrParser();
    private final TransferValidationProcessor validator = new TransferValidationProcessor();

    public IntakeProcessor(ObjectMapper mapper, TransactionStore store) {
        this.mapper = mapper;
        this.store = store;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SolicitudTransferencia request = exchange.getMessage().getBody(SolicitudTransferencia.class);
        if (request == null || isBlank(request.idTransaccion()) || isBlank(request.fechaTransaccion())
                || isBlank(request.qr())) {
            reject(exchange, request == null ? null : request.idTransaccion(),
                    "id_transaccion, fecha_transaccion y qr son obligatorios");
            return;
        }

        String id = request.idTransaccion();
        exchange.getMessage().setHeader(TransactionIdProcessor.TRANSACTION_ID_HEADER, id);
        try {
            Transferencia transfer = parser.parse(request.qr());
            if ("11".equals(transfer.pointOfInitiationMethod()) && transfer.transactionAmount() == null) {
                transfer = transfer.withTransactionAmount(request.monto());
            }

            String validationError = validator.validate(transfer);
            if (validationError != null) {
                reject(exchange, id, validationError);
                return;
            }
            if (!store.register(id)) {
                complete(exchange, ResultadoTransferencia.duplicada(id), false);
                return;
            }

            String bankCode = transfer.merchantAccountInformation().codigoEntidad();
            MensajeTransferencia message = new MensajeTransferencia(
                    id, request.fechaTransaccion(), bankCode, transfer);
            exchange.getMessage().setHeader(TransferValidationProcessor.BANK_CODE_HEADER, bankCode);
            exchange.getMessage().setHeader(BROKER_PAYLOAD_HEADER, mapper.writeValueAsString(message));
            complete(exchange, ResultadoTransferencia.aceptada(id), true);
        } catch (RuntimeException exception) {
            reject(exchange, id, "QR inválido: " + exception.getMessage());
        }
    }

    private void reject(Exchange exchange, String id, String reason) {
        complete(exchange, ResultadoTransferencia.rechazada(id, reason), false);
    }

    private void complete(Exchange exchange, ResultadoTransferencia result, boolean accepted) {
        exchange.getMessage().setHeader(ACCEPTED_HEADER, accepted);
        exchange.setProperty(API_RESULT_PROPERTY, result);
        exchange.getMessage().setBody(result);
        if (!accepted) {
            store.save(result);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
