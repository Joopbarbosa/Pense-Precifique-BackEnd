package com.penseprecifique.api.shared.dto.response.insumo;

import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovimentacaoInsumoResponseDTO(
        UUID id,
        TipoMovimentacaoInsumo tipo,
        MotivoMovimentacaoInsumo motivo,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        String observacao,
        UUID referenciaId,
        ReferenciaMovimentacaoTipo referenciaTipo,
        String referencia,
        boolean estornada,
        LocalDateTime createdAt,
        /** #959/DT-NOVA-4 — data do recebimento da compra; nula nas movimentações sem ela. */
        java.time.LocalDate dataReferencia,
        /** #672/DT-NOVA-7 — MOVIMENTACAO (estoque) ou PRECO_SUGERIDO (linha agregada das mudanças do preço sugerido). */
        String tipoLinha,
        /** Só na linha PRECO_SUGERIDO. */
        com.penseprecifique.api.insumo.PrecoSugeridoAgregadoResponse precosSugeridos
) {}
