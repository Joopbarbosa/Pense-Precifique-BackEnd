package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * #560/#451 (V0.15.0) — item mais comprado pelo cliente: item de catálogo ou produto avulso,
 * agrupado pelo id, somando quantidade e valor dos pedidos que contam como compra.
 * {@code tipo}: ITEM_CATALOGO | PRODUTO.
 */
public record ItemCompradoResponse(UUID id, String tipo, String nome, BigDecimal quantidade, BigDecimal valor) {}
