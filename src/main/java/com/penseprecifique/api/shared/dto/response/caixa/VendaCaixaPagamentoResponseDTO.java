package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;
import java.util.UUID;

public record VendaCaixaPagamentoResponseDTO(
        UUID id,
        UUID metodoPagamentoId,
        /** #993 (RN-NOVA-30) — forma de pagamento pelo nome (o da artesã, ou o rótulo do tipo), mesmo se foi desativada depois. */
        String metodoPagamentoNome,
        BigDecimal valor,
        Integer parcelas,
        BigDecimal taxaPercentualAplicada
) {}
