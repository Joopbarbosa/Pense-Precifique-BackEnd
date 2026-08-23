package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * RN-NOVA-2 (revisada, V0.8.2, P-B012) — resultado de simular {@code avancar-status} sem persistir
 * nada, para o frontend decidir se mostra a modal de confirmação do atalho antes de aplicar de
 * verdade. {@code atalhoAplicavel=true} só é possível quando o status atual é {@code ENVIADO} e as
 * 3 condições da RN-NOVA-2 batem; {@code avisosEstoque} não-vazio significa que o atalho pularia
 * para {@code FINALIZADO}, mas com baixa que deixaria algum produto negativo, ainda não confirmada
 * (mesmo contrato de {@link com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse}).
 */
@Getter
@Setter
public class SimulacaoAvancoStatusResponse {

    private StatusOrcamento statusAtual;
    private StatusOrcamento statusResultante;
    private boolean atalhoAplicavel;
    private List<AvisoEstoqueNegativoResponse> avisosEstoque;
}
