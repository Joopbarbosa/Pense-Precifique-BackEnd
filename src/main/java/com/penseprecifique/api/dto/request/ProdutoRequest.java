package com.penseprecifique.api.dto.request;

import com.penseprecifique.api.domain.enums.TipoProduto;
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

    @NotNull
    @Valid
    private List<FichaTecnicaItemRequest> fichaTecnica = new ArrayList<>();
}
