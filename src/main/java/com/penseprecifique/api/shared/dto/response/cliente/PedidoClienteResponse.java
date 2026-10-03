package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #560 (V0.15.0) — linha do histórico do cliente e "última compra". {@code tipo}: ORCAMENTO |
 * VENDA_CAIXA. {@code data}: data de criação do orçamento / data da venda. {@code dataCompra}:
 * data que conta como compra (data de entrega do orçamento ENTREGUE / data da venda CONCLUIDA);
 * nula quando o pedido não conta como compra ({@code contaComoCompra = false}).
 */
public record PedidoClienteResponse(
        UUID id,
        String tipo,
        String identificador,
        LocalDateTime data,
        LocalDateTime dataCompra,
        String status,
        BigDecimal valor,
        boolean contaComoCompra
) {}
