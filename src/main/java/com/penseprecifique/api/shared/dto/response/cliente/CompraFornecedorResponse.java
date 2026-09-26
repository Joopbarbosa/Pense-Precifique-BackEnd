package com.penseprecifique.api.shared.dto.response.cliente;

import com.penseprecifique.api.shared.domain.enums.StatusCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * #560 (V0.15.0) — linha do histórico do fornecedor (aba Histórico, seção Fornecedor). {@code valor}
 * = soma das linhas desta compra com este fornecedor (numa compra com vários fornecedores, só a parte
 * dele).
 */
public record CompraFornecedorResponse(
        UUID id,
        String identificador,
        LocalDate dataCompra,
        StatusCompra status,
        BigDecimal valor,
        boolean pago
) {}
