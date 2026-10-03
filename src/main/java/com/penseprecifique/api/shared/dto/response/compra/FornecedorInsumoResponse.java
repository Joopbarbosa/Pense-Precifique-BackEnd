package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.RegraPrecoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #540/RN-NOVA-6 (V0.15.0) — vínculo Fornecedor↔Insumo com preço de referência opcional.
 * #590/#586 (adendo 2): {@code regraPrecoReferencia} do insumo e a última compra CONFIRMADA do par
 * (nula se o vínculo foi criado à mão e nunca houve compra).
 */
public record FornecedorInsumoResponse(
        UUID id,
        CadastroRefResponse fornecedor,
        InsumoRefResponse insumo,
        BigDecimal precoReferencia,
        LocalDateTime updatedAt,
        RegraPrecoReferencia regraPrecoReferencia,
        UltimaCompra ultimaCompra
) {
    public record UltimaCompra(UUID compraId, String identificador, LocalDate data, BigDecimal precoUnitario) {}
}
