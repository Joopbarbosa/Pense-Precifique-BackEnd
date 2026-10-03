package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;

import java.util.UUID;

/**
 * #550 (V0.15.0) — método de pagamento da compra. {@code nome} já vem com o rótulo de exibição
 * (tipos fixos não têm nome gravado: "Dinheiro", "Pix", "Cartão de crédito", "Cartão de débito").
 * Um método inativado depois continua aparecendo nas compras que o usam (RN-NOVA-23).
 */
public record MetodoPagamentoRefResponse(UUID id, TipoMetodoPagamento tipo, String nome, boolean ativo) {}
