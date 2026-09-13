package py.edu.ucom.sipap.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SolicitudTransferencia(
        @JsonProperty("id_transaccion") String idTransaccion,
        @JsonProperty("fecha_transaccion") String fechaTransaccion,
        @JsonProperty("qr") String qr,
        @JsonProperty("monto") Long monto) {
}
