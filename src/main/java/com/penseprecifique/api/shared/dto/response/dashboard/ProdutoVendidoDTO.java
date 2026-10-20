package com.penseprecifique.api.shared.dto.response.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** #999 — linha do "Mais vendidos" da Início: tipo (PRODUTO ou CATALOGO) e id do cadastro; quantidade decimal (o Caixa aceita fração). */
@Data
@Builder
public class ProdutoVendidoDTO {
    private String nomeProduto;
    private String marca;
    private BigDecimal quantidade;
    private String tipo;
    private UUID id;
}
