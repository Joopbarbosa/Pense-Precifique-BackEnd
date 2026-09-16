package com.penseprecifique.api.shared.dto.request.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * RN-NOVA-5 (V0.10.0, #450) — desagrupar uma produção com tipoOrigem=AGRUPAMENTO. 1 requisição
 * final, com a fila "uma pergunta por vez" já resolvida no frontend (DT-NOVA-2) — nunca N
 * chamadas sequenciais ao backend.
 */
@Getter
@Setter
public class DesagruparProducaoRequest {

    @NotEmpty(message = "Informe ao menos 1 produto para desagrupar")
    @Valid
    private List<ItemDesagrupar> itens;

    @Getter
    @Setter
    public static class ItemDesagrupar {

        @NotNull(message = "produtoId é obrigatório em cada item")
        private UUID produtoId;

        @NotNull(message = "estadoDestino é obrigatório em cada item")
        private EstadoProducao estadoDestino;

        // RN-052 — só relevante quando estadoDestino=EM_ANDAMENTO, mesma semântica de
        // IniciarProducaoRequest.confirmarEstoqueNegativoInsumoIds, mas por item desagrupado.
        private List<UUID> confirmarEstoqueNegativoInsumoIds;
    }
}
