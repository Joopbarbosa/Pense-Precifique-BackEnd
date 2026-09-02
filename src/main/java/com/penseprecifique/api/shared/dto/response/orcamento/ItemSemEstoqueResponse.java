package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #194/ORC-028 (rótulo antigo "RN-NOVA-5" corrigido — ver RN-NOVA-24) — item de orçamento cujo
 * produto não tem estoque suficiente pra cobrir a quantidade solicitada. Alimenta a condição de
 * exibir o checkbox de seleção no card "Estoque insuficiente" do Detalhe (RN-NOVA-25) — não cria
 * produção nenhuma, é só leitura.
 */
@Getter
@Setter
public class ItemSemEstoqueResponse {

    private UUID produtoId;
    private String identificador;
    private String nomeProduto;
    private BigDecimal quantidadeSolicitada;
    private BigDecimal estoqueAtual;
    private BigDecimal quantidadeFaltante;

    /**
     * RN-NOVA-26 (V0.8.3, #319+387) — id da produção vinculada em estado não-terminal
     * (AGUARDANDO_INICIO/EM_ANDAMENTO/TRAVADA) que já cobre especificamente este produto; {@code
     * null} quando não há vínculo ativo (nenhum vínculo, ou só vínculo(s) terminal(is) —
     * FINALIZADA/CANCELADA/NAO_REALIZADA não contam). Frontend troca o checkbox por
     * {@code VinculoAtivoBadge} + "Visualizar produção" quando preenchido.
     */
    private UUID producaoVinculadaId;

    /** RN-NOVA-26 — identificador legível (ex. "PRD-7") da produção de {@code producaoVinculadaId}, mesmo par nulo/preenchido. */
    private String identificadorProducaoVinculada;
}
