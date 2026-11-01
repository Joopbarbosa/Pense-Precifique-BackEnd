package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import jakarta.validation.Valid;
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

    /** V0.19.0 (#1185, DT-NOVA-8) — método configurável (de Configurações) em que o sinal foi recebido. */
    private UUID metodoSinalRecebidoId;

    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String motivoCancelamento;
    private TipoCancelamento tipoCancelamento;
    /** V0.19.0 (#692, RN-NOVA-50) — ausente = cancelar sem multa; informado, precisa ser maior que 0 e até 100 (do total). */
    @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Informe uma multa maior que zero ou escolha cancelar sem multa.")
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "A multa não pode ser maior que o total do orçamento.")
    @jakarta.validation.constraints.Digits(integer = 3, fraction = 2, message = "A multa deve ter no máximo duas casas decimais.")
    private BigDecimal percentualMulta;
    private boolean estornarSinal;

    /** V0.19.0 (#692, RN-NOVA-51) — cancelar PAGO/ENTREGUE: estornar o valor recebido? Obrigatório nesses status. */
    private Boolean estornarValorRecebido;

    /** V0.19.0 (#692, RN-NOVA-51) — cancelar PAGO/ENTREGUE: devolver os insumos ao estoque? Obrigatório nesses status. */
    private Boolean devolverEstoque;
    private LocalDateTime dataEstornoSinal;

    @io.swagger.v3.oas.annotations.media.Schema(minLength = 30, maxLength = LimitesTexto.DESCRICAO_MAX) // #788: mínimo no contrato
    @Size(min = 30, message = "A justificativa deve ter no mínimo 30 caracteres")
    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
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

    // RN-NOVA-40 (V0.18.0, #1167) — decisão por produção vinculada ao cancelar o orçamento. Produção
    // sem decisão equivale a "Não, manter". Só vale para /cancelar; ignorado nos outros avanços.
    @Valid
    @Size(max = 50, message = "Decisões de produção em excesso")
    private List<DecisaoProducaoCancelamentoRequest> decisoesProducao;

    /** V0.19.0 (#1174, RN-NOVA-38) — passo Pagamento; obrigatório ao aprovar (APROVADO ou atalho ao FINALIZADO). */
    @Valid
    private PagamentoOrcamentoRequest pagamento;

    /** V0.19.0 (#1174, RN-NOVA-43) — forma realmente recebida no "Recebi" do saldo/total (ORC-007). */
    private UUID metodoRecebidoId;

    /** V0.19.0 (#1174) — data do recebimento no "Recebi"; ausente = hoje. */
    private java.time.LocalDate dataRecebimento;
}
