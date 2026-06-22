package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoInsumo;

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
        boolean estornada,
        LocalDateTime createdAt
) {}
