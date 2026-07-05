package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.TipoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ProdutoResponse {

    private UUID id;
    private String nome;
    private TipoProduto tipo;
    private BigDecimal precoVenda;
    private BigDecimal precoCusto;
    private BigDecimal margemLucro;
    private boolean override;
    /** Calculado no Service — RN-038a, nunca persistido. Pendente de P-005 (RN-039/custoUnitario). */
    private BigDecimal precoSugerido;
    private BigDecimal estoqueAtual;
    private BigDecimal estoqueMinimo;
    private boolean ativo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
