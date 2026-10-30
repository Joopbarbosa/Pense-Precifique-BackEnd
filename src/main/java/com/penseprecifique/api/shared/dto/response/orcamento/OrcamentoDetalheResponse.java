package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;

@Getter
@Setter
public class OrcamentoDetalheResponse {

    private UUID id;
    private Integer numero;
    private UUID clienteId;
    private String nomeCliente;
    private StatusOrcamento status;
    private MetodoPagamento metodoPagamento;
    private String metodoPagamentoObs;
    private Integer prazoProducaoDias;
    private boolean inicioAssimQueAprovado;
    private LocalDate dataInicioEstimada;
    private LocalDateTime dataAprovacao;
    /** RN-NOVA-12 (V0.18.0) — data limite combinada para o sinal. */
    private LocalDate sinalAte;
    /** RN-NOVA-14 (V0.18.0) — passos do histórico; nulos nos orçamentos anteriores (sem registro retroativo). */
    private LocalDateTime dataEnvio;
    private LocalDateTime dataFinalizacao;
    private LocalDateTime dataCancelamento;
    /** RN-NOVA-8 (V0.18.0, #444) — entrega estimada em dias corridos a partir do sinal pago (com sinal) ou da
     * aprovação (sem sinal), com a data de início estimada como piso; nulo sem prazo de produção ou com sinal
     * ainda não pago. Usada também contra {@code Producao.dataTerminoPrevista} (RN-ORC-VINC-04). */
    private LocalDate dataEntregaEstimada;
    /** RN-NOVA-11 (V0.18.0) — RASCUNHO ou ENVIADO com a data de validade já passada; calculado no servidor. */
    private boolean vencido;
    private boolean sinalAtivo;
    private BigDecimal percentualSinal;
    private BigDecimal valorSinal;
    private LocalDateTime dataSinalPago;
    /** DT-NOVA-4 (V0.10.0, #466) — achado do teste manual: campo persistido na entidade
     *  (dataPagamento) desde #466, mas nunca chegou a este DTO nem ao mapper — mesmo anti-padrão já
     *  documentado no CLAUDE.md do backend (campo persistido "esquecido" no DTO de resposta). Sem
     *  ele, a condição de exibir o Recibo de Pagamento no Frontend nunca era verdadeira. */
    private LocalDateTime dataPagamento;
    private LocalDateTime dataEntrega; // #560/RN-NOVA-22 (V0.15.0)
    private MetodoPagamento metodoSinalRecebido;
    private String metodoSinalRecebidoObs;
    private BigDecimal subtotal;
    private String tipoDesconto;
    private BigDecimal descontoValor;
    private BigDecimal total;
    private String observacoes;
    private LocalDateTime dataValidade;
    private TipoCancelamento cancelamentoTipo;
    private BigDecimal percentualMulta;
    private BigDecimal valorMulta;
    private BigDecimal valorDevolvidoMulta;
    private Boolean estornoSinal;
    private LocalDateTime dataEstornoSinal;
    private List<OrcamentoItemResponse> itens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** UC-037/#126 — avisos informativos de estoque insuficiente, calculados só na criação (POST /orcamentos). */
    private List<AvisoEstoqueResponse> avisosEstoque;
    /** RN-NOVA-6/ORC-040/#320 (P-B013) — produções já vinculadas (N:N via orcamento_producoes), pra a
     * UI saber mostrar o vínculo sem depender de guardar em memória a resposta de vincular-producao. */
    private List<OrcamentoProducaoResponse> producoesVinculadas;
}
