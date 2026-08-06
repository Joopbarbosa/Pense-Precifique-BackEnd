package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoRequest {

    @NotNull(message = "O cliente é obrigatório")
    private UUID clienteId;

    @NotEmpty(message = "O orçamento deve ter pelo menos um item")
    @Valid
    private List<OrcamentoItemRequest> itens;

    @NotNull(message = "O método de pagamento é obrigatório")
    private MetodoPagamento metodoPagamento;

    private String metodoPagamentoObs;

    @NotNull(message = "O prazo de produção é obrigatório")
    @Min(value = 1, message = "O prazo deve ser de pelo menos 1 dia útil")
    private Integer prazoProducaoDias;

    private boolean inicioAssimQueAprovado = true;
    private LocalDate dataInicioEstimada;

    private boolean sinalAtivo = false;
    private BigDecimal percentualSinal;
    private BigDecimal valorSinal;

    private String tipoDesconto = "PERCENTUAL";
    private BigDecimal descontoValor = BigDecimal.ZERO;

    private String observacoes;
    private LocalDateTime dataValidade;
}
