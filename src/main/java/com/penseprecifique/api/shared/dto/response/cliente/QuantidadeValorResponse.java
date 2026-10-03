package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;

/** #560 (V0.15.0) — par quantidade + valor dos indicadores (ex.: orçamentos em aberto). */
public record QuantidadeValorResponse(long quantidade, BigDecimal valor) {}
