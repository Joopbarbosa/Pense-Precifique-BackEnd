package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AvancaStatusRequest {

    private MetodoPagamento metodoSinalRecebido;
    private String metodoSinalRecebidoObs;

    private String motivoCancelamento;
    private TipoCancelamento tipoCancelamento;
    private BigDecimal percentualMulta;
    private boolean estornarSinal;
    private LocalDateTime dataEstornoSinal;

    @Size(min = 30, message = "A justificativa deve ter no mínimo 30 caracteres")
    private String justificativa;

    // RN-052 — mesma semântica de IniciarProducaoRequest.confirmarEstoqueNegativoInsumoIds, usada ao
    // avançar EM_PRODUCAO → FINALIZADO: ids dos produtos cujo estoque resultaria negativo
    // (permitirEstoqueNegativo=true) e cuja baixa o usuário já confirmou.
    private List<UUID> confirmarEstoqueNegativoProdutoIds;

    // RN-NOVA-2 (revisada, V0.8.2, P-B012) — permite ao chamador recusar o atalho de aprovação
    // direta mesmo quando as 3 condições batem: true força o case ENVIADO a seguir para APROVADO
    // (fluxo normal), ignorando a checagem de elegibilidade. Default false/ausente preserva o
    // comportamento existente (atalho aplica automaticamente quando elegível).
    private boolean ignorarAtalhoAprovacaoDireta;

    // RN-NOVA-20 (V0.8.3, #375+308, P-B004) — ciência de vínculo(s) órfão(s) (produção CANCELADA/
    // NAO_REALIZADA ainda referenciada em orcamento_producoes) ao avançar EM_PRODUCAO → FINALIZADO.
    // Aviso agregado (todos os vínculos órfãos de uma vez, não sequencial) — diferente de
    // confirmarEstoqueNegativoProdutoIds (lista de ids), aqui é um único boolean porque não há
    // decisão por vínculo, só ciência. Default false/ausente preserva o comportamento de sempre
    // devolver o aviso na 1ª chamada quando houver vínculo órfão.
    private boolean confirmarVinculosOrfaos;
}
