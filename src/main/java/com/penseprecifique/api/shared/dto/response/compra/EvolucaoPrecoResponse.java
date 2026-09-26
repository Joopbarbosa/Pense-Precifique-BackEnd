package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #548/RN-NOVA-15 (V0.15.0) — gráfico de evolução do preço pago (até 5 insumos). Um ponto por linha
 * de compra CONFIRMADA no período, em ordem cronológica. {@code variacaoPercentual}: em relação ao
 * primeiro ponto da série no período (modo "Variação %", padrão aprovado na prévia).
 */
public record EvolucaoPrecoResponse(LocalDate de, LocalDate ate, List<Serie> series) {

    public record Serie(InsumoRefResponse insumo, List<Ponto> pontos) {}

    public record Ponto(LocalDate data, UUID compraId, String identificador, BigDecimal precoUnitarioPago,
                        BigDecimal quantidade, String fornecedor, BigDecimal variacaoPercentual) {}
}
