package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.15.0 (ajuste do teste manual de #560/#451) — linha padronizada da modal de listagem do detalhe do
 * cadastro: as mesmas colunas servem aos pedidos do cliente (orçamento/venda do Caixa) e às compras
 * do fornecedor.
 *
 * <p>{@code tipo}: ORCAMENTO | VENDA_CAIXA | COMPRA. {@code data}: data que conta como compra quando
 * {@code contaComoCompra} (entrega do orçamento ENTREGUE, data da venda, data da compra); nos demais
 * pedidos, a data de criação do orçamento / da venda. {@code valor}: numa compra com vários
 * fornecedores, só a parte deste fornecedor. {@code quantidadeItens}: linhas do pedido/compra (não
 * unidades). {@code pago}: só em COMPRA (nulo nos pedidos).
 */
public record RegistroCadastroResponse(
        UUID id,
        String tipo,
        String identificador,
        LocalDate data,
        String status,
        BigDecimal valor,
        int quantidadeItens,
        String resumoItens,
        boolean contaComoCompra,
        Boolean pago
) {}
