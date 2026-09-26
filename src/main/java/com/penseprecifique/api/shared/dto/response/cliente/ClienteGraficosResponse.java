package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * #451/RN-NOVA-20 (V0.15.0) — GET /clientes/{id}/graficos. {@code gastoMensal} tem um ponto por
 * mês do período, inclusive meses sem compra (total 0), em ordem cronológica; {@code mes} é o
 * primeiro dia do mês. {@code itensMaisComprados}: até 10, por quantidade.
 */
public record ClienteGraficosResponse(
        LocalDate de,
        LocalDate ate,
        List<GastoMensal> gastoMensal,
        List<ItemCompradoResponse> itensMaisComprados
) {
    public record GastoMensal(LocalDate mes, BigDecimal total) {}
}
