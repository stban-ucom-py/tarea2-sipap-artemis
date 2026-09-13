package py.edu.ucom.sipap.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResultadoTransferencia(
        @JsonProperty("id_transaccion") String idTransaccion,
        @JsonProperty("estado") String estado,
        @JsonProperty("mensaje") String mensaje) {

    public static ResultadoTransferencia aceptada(String id) {
        return new ResultadoTransferencia(id, "ACEPTADA_PARA_PROCESAMIENTO",
                "Transferencia enviada a la cola");
    }

    public static ResultadoTransferencia procesada(String id, String banco) {
        return new ResultadoTransferencia(id, "PROCESADA",
                "Transferencia procesada exitosamente por " + banco);
    }

    public static ResultadoTransferencia rechazada(String id, String motivo) {
        return new ResultadoTransferencia(id, "RECHAZADA", motivo);
    }

    public static ResultadoTransferencia rechazadaFecha(String id) {
        return new ResultadoTransferencia(id, "RECHAZADA_FECHA",
                "La fecha de transacción no coincide con la fecha actual");
    }

    public static ResultadoTransferencia duplicada(String id) {
        return new ResultadoTransferencia(id, "DUPLICADA",
                "La transferencia ya fue recibida anteriormente");
    }

    public static ResultadoTransferencia errorBanco(String id, String detalle) {
        return new ResultadoTransferencia(id, "ERROR_BANCO", detalle);
    }
}
