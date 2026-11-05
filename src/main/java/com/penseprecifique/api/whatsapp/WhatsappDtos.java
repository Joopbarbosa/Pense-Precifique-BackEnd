package com.penseprecifique.api.whatsapp;

import java.time.Instant;
import java.util.UUID;

/** DTOs do módulo WhatsApp (#1170). */
public final class WhatsappDtos {

    private WhatsappDtos() { }

    public record ConectarRequest(Boolean entendiRiscos) { }

    public record StatusResponse(boolean habilitado, EstadoWhatsapp estado, Instant estadoAlteradoEm) { }

    public record QrResponse(EstadoWhatsapp estado, String qrBase64) { }

    public record EnvioRequest(TipoDocumentoWhatsapp tipoDocumento, UUID documentoId, UUID clienteId,
                               String numero, Boolean confirmoAceite, String legenda,
                               Boolean confirmaPrecosFornecedor, Boolean reenviarSegundaParte) {
        public EnvioRequest(TipoDocumentoWhatsapp tipoDocumento, UUID documentoId, UUID clienteId,
                            String numero, Boolean confirmoAceite, String legenda, Boolean confirmaPrecosFornecedor) {
            this(tipoDocumento, documentoId, clienteId, numero, confirmoAceite, legenda, confirmaPrecosFornecedor, false);
        }
    }

    public record EnvioResponse(ResultadoEnvio resultado, Instant enviadoEm, int enviadosHoje, int limiteDiario) { }

    public record HistoricoItem(UUID id, String numero, TipoDocumentoWhatsapp tipoDocumento, UUID documentoId,
                                Instant enviadoEm, ResultadoEnvio resultado, String motivo) { }
}
