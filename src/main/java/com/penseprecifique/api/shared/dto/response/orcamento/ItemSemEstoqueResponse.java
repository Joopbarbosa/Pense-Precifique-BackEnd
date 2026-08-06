package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #194/RN-NOVA-5 — item de orçamento cujo produto não tem estoque suficiente pra cobrir a
 * quantidade solicitada. Alimenta a condição de exibir o botão "Criar produção" no Detalhe do
 * Orçamento (UC-NOVA-4) — não cria produção nenhuma, é só leitura.
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
}
