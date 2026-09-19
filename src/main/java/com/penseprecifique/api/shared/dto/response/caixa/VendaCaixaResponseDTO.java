package com.penseprecifique.api.shared.dto.response.caixa;

import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VendaCaixaResponseDTO(
        UUID id,
        Integer numero,
        String identificador,
        UUID clienteId,
        LocalDateTime dataVenda,
        UUID caixaTurnoId,
        StatusVendaCaixa status,
        BigDecimal subtotal,
        TipoDesconto descontoTipo,
        BigDecimal descontoValor,
        BigDecimal total,
        BigDecimal troco,
        String cancelamentoMotivo,
        /** #487 (V0.12.0) — só preenchido em venda cancelada: se o estoque voltou ou não. */
        Boolean estoqueRetornado,
        List<VendaCaixaItemResponseDTO> itens,
        List<VendaCaixaPagamentoResponseDTO> pagamentos
) {}
