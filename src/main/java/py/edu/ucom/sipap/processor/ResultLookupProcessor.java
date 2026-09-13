package py.edu.ucom.sipap.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import py.edu.ucom.sipap.domain.ResultadoTransferencia;
import py.edu.ucom.sipap.service.TransactionStore;

public class ResultLookupProcessor implements Processor {
    private final TransactionStore store;

    public ResultLookupProcessor(TransactionStore store) {
        this.store = store;
    }

    @Override
    public void process(Exchange exchange) {
        String id = exchange.getMessage().getHeader("id", String.class);
        ResultadoTransferencia result = store.find(id);
        if (result == null) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            result = new ResultadoTransferencia(id, "NO_ENCONTRADA",
                    "No existe un resultado para la transacción indicada");
        } else {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        }
        exchange.getMessage().setBody(result);
    }
}
