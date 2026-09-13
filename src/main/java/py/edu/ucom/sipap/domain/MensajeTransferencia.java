package py.edu.ucom.sipap.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MensajeTransferencia(
        @JsonProperty("id_transaccion") String idTransaccion,
        @JsonProperty("fecha_transaccion") String fechaTransaccion,
        @JsonProperty("banco_destino") String bancoDestino,
        @JsonProperty("transferencia") Transferencia transferencia) {
}
