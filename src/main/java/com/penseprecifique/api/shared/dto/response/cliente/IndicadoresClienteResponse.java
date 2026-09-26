package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * #560/RN-NOVA-19 (V0.15.0) — indicadores do papel Cliente. Compra = orçamento ENTREGUE + venda do
 * Caixa CONCLUIDA (Decisão 13). Campos nulos = sem dado (o frontend mostra "—").
 */
public record IndicadoresClienteResponse(
        PedidoClienteResponse ultimaCompra,
        BigDecimal totalGasto,
        BigDecimal ticketMedio,
        long numeroPedidos,
        ItemCompradoResponse itemMaisComprado,
        LocalDateTime clienteDesde,
        QuantidadeValorResponse orcamentosEmAberto,
        QuantidadeValorResponse orcamentosCancelados
) {}
