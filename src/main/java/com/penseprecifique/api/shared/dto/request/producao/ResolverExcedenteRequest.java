package com.penseprecifique.api.shared.dto.request.producao;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** RN-NOVA-42 — escolha diante do aviso: manter o lote (registra as unidades não cobertas) ou cancelá-lo. */
@Getter
@Setter
public class ResolverExcedenteRequest {

    @NotNull
    private Boolean cancelarLote;
}
