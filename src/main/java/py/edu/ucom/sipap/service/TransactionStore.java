package py.edu.ucom.sipap.service;

import py.edu.ucom.sipap.domain.ResultadoTransferencia;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Almacén local de idempotencia y resultados; su alcance es una instancia de la aplicación. */
public class TransactionStore {
    private final Map<String, Boolean> received = new ConcurrentHashMap<>();
    private final Map<String, ResultadoTransferencia> results = new ConcurrentHashMap<>();

    public boolean register(String transactionId) {
        return received.putIfAbsent(transactionId, Boolean.TRUE) == null;
    }

    public void save(ResultadoTransferencia result) {
        results.put(result.idTransaccion(), result);
    }

    public ResultadoTransferencia find(String transactionId) {
        return results.get(transactionId);
    }

    public int receivedCount() {
        return received.size();
    }
}
