package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VendaCaixaItemResponseDTO(
        UUID id,
        UUID produtoId,
        String produtoNome,
        UUID itemCatalogoId,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal,
        List<VendaCaixaItemCustomizacaoResponseDTO> customizacoes,
        /** #720/RN-NOVA-14 — PRODUTO ou CATALOGO (item de Catálogo → Catálogo dono). */
        String destinoTipo,
        /** Id do cadastro de destino; nulo quando excluído (sem link). */
        UUID destinoId
) {}
