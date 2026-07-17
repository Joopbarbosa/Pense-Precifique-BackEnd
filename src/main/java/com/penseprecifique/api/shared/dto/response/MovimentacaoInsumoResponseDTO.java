package com.penseprecifique.api.shared.dto.response;

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
        String observacao,
        UUID referenciaId,
        ReferenciaMovimentacaoTipo referenciaTipo,
        String referencia,
        boolean estornada,
        LocalDateTime createdAt
) {}
