package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.OrigemCompra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** #541 (V0.15.0) — detalhe da compra. {@code total} = soma dos preços totais informados. */
public record CompraResponse(
        UUID id,
        Integer numero,
        String identificador,
        StatusCompra status,
        LocalDate dataCompra,
        boolean multiplosFornecedores,
        CadastroRefResponse fornecedor,
        boolean pago,
        MetodoPagamentoRefResponse metodoPagamento,
        String observacoes,
        OrigemCompra origem,
        BigDecimal total,
        List<CompraItemResponse> itens,
        LocalDateTime confirmadaEm,
        LocalDateTime canceladaEm,
        String observacaoCancelamento,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
