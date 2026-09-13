package py.edu.ucom.sipap.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import py.edu.ucom.sipap.domain.MensajeTransferencia;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.service.TransactionStore;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class DateValidationProcessor implements Processor {
    public static final String DATE_VALID_HEADER = "SipapDateValid";
    public static final String ORIGINAL_MESSAGE_PROPERTY = "SipapOriginalMessage";

    private final Clock clock;
    private final TransactionStore store;

    public DateValidationProcessor(Clock clock, TransactionStore store) {
        this.clock = clock;
        this.store = store;
    }

    @Override
    public void process(Exchange exchange) {
        MensajeTransferencia message = exchange.getMessage().getBody(MensajeTransferencia.class);
        exchange.getMessage().setHeader(TransactionIdProcessor.TRANSACTION_ID_HEADER,
                message.idTransaccion());
        exchange.getMessage().setHeader("SipapBankCode", message.bancoDestino());
        exchange.setProperty(ORIGINAL_MESSAGE_PROPERTY, message);

        boolean valid;
        try {
            valid = LocalDate.parse(message.fechaTransaccion()).equals(LocalDate.now(clock));
        } catch (DateTimeParseException exception) {
            valid = false;
        }
        exchange.getMessage().setHeader(DATE_VALID_HEADER, valid);
        if (!valid) {
            ResultadoTransferencia result = ResultadoTransferencia.rechazadaFecha(message.idTransaccion());
            store.save(result);
            exchange.getMessage().setBody(result);
        }
    }
}
