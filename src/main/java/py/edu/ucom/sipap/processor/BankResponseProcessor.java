package py.edu.ucom.sipap.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import py.edu.ucom.sipap.domain.MensajeTransferencia;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.service.TransactionStore;

public class BankResponseProcessor implements Processor {
    private final TransactionStore store;

    public BankResponseProcessor(TransactionStore store) {
        this.store = store;
    }

    @Override
    public void process(Exchange exchange) {
        MensajeTransferencia original = exchange.getProperty(
                DateValidationProcessor.ORIGINAL_MESSAGE_PROPERTY, MensajeTransferencia.class);
        Integer status = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (status == null) {
            status = 200;
        }
        ResultadoTransferencia result;
        if (status >= 200 && status < 300) {
            String bank = exchange.getMessage().getHeader("SipapBankName", String.class);
            result = ResultadoTransferencia.procesada(original.idTransaccion(), bank);
        } else {
            result = ResultadoTransferencia.errorBanco(original.idTransaccion(),
                    "El banco mock respondió con HTTP " + status);
        }
        store.save(result);
        exchange.getMessage().setBody(result);
    }
}
