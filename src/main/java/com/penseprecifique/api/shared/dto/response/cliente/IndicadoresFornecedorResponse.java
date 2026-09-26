package com.penseprecifique.api.shared.dto.response.cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #560/RN-NOVA-19 (V0.15.0) — indicadores do papel Fornecedor, sobre compras CONFIRMADAS. Campos
 * nulos = sem dado ("—").
 */
public record IndicadoresFornecedorResponse(
        UltimaCompraFornecedor ultimaCompra,
        BigDecimal totalComprado,
        BigDecimal compraMedia,
        long numeroCompras,
        InsumoCompradoResponse insumoMaisComprado,
        long insumosVinculados,
        QuantidadeValorResponse comprasNaoPagas
) {
    public record UltimaCompraFornecedor(UUID id, String identificador, java.time.LocalDate data) {}

    public record InsumoCompradoResponse(UUID id, String nome, String unidade, BigDecimal quantidade) {}
}
