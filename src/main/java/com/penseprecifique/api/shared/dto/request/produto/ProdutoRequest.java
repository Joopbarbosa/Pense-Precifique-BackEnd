package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProdutoRequest {

    @NotBlank(message = "O nome do produto é obrigatório")
    private String nome;

    @NotNull(message = "O tipo é obrigatório")
    private TipoProduto tipo;

    private String descricao;

    @NotNull(message = "O tempo de produção é obrigatório")
    @Min(value = 1, message = "O tempo de produção deve ser pelo menos 1 minuto")
    private Integer tempoProducao;

    private BigDecimal precoVenda;

    private BigDecimal margemLucro;

    private BigDecimal rendimento;

    private BigDecimal estoqueAtual;

    private BigDecimal estoqueMinimo;

    private Boolean permitirEstoqueNegativo;

    /**
     * RN-NOVA-2 (V0.10.0, #299, altera PDT-016) — valor exibido/editável de "produto fracionável".
     * Ausente ou igual ao valor derivado da ficha técnica = sem override (segue derivando ao vivo).
     * Diferente do derivado = override manual, congela até nova edição explícita.
     */
    private Boolean fracionavel;

    @NotNull
    @Valid
    private List<FichaTecnicaItemRequest> fichaTecnica = new ArrayList<>();
}
