package com.penseprecifique.api.shared.dto.response;

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
    private boolean sinalAtivo;
    private BigDecimal percentualSinal;
    private BigDecimal valorSinal;
    private LocalDateTime dataSinalPago;
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
    private Boolean estornoSinal;
    private LocalDateTime dataEstornoSinal;
    private List<OrcamentoItemResponse> itens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** UC-037/#126 — avisos informativos de estoque insuficiente, calculados só na criação (POST /orcamentos). */
    private List<AvisoEstoqueResponse> avisosEstoque;
}
