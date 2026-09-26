package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** #540/RN-NOVA-6 (V0.15.0) — vínculo Fornecedor↔Insumo com preço de referência opcional. */
public record FornecedorInsumoResponse(
        UUID id,
        CadastroRefResponse fornecedor,
        InsumoRefResponse insumo,
        BigDecimal precoReferencia,
        LocalDateTime updatedAt
) {}
