package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.InsumoProposto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VinculoNotaResponse(UUID id, String nomeItem, String emitenteCnpj, String emitenteNome,
        UUID fornecedorId, String fornecedorNome, InsumoProposto insumo, BigDecimal fator, boolean ignorar,
        OrigemVinculoItemNota origem, LocalDateTime createdAt, LocalDateTime updatedAt,
        UUID compraId, String compraIdentificador) {}
