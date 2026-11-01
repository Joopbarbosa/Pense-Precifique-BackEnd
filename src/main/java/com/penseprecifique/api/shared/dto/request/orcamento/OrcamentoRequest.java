package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.Size;
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

    // RN-NOVA-37 (V0.19.0, #1185): o orçamento NÃO recebe forma de pagamento ao ser salvo; ela é escolhida no passo
    // Pagamento da aprovação (POST /orcamentos/{id}/pagamento/validar valida; a #1174 grava).

    @NotNull(message = "É obrigatório informar se o orçamento vai ter prazo de produção")
    private Boolean temPrazoProducao;

    @Min(value = 1, message = "O prazo deve ser de pelo menos 1 dia")
    private Integer prazoProducaoDias;

    private boolean inicioAssimQueAprovado = true;
    private LocalDate dataInicioEstimada;

    private boolean sinalAtivo = false;
    /** RN-NOVA-12 (V0.18.0) — "Sinal até"; só com sinal ativo e não anterior a hoje (salvo o valor já gravado). */
    private LocalDate sinalAte;
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "O sinal não pode ser maior que 100% do total.")
    @jakarta.validation.constraints.Digits(integer = 3, fraction = 2, message = "O percentual do sinal deve ter no máximo duas casas decimais.")
    private BigDecimal percentualSinal;
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2, message = "O valor do sinal deve ter no máximo duas casas decimais.")
    private BigDecimal valorSinal;

    private String tipoDesconto = "PERCENTUAL";
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String observacoes;
    private LocalDateTime dataValidade;
}
