package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.StatusCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #541 (V0.15.0) — linha de Minhas compras. {@code fornecedores}: nomes distintos das linhas (ou do
 * cabeçalho), em ordem alfabética; vazio quando a compra não tem fornecedor.
 */
public record CompraResumoResponse(
        UUID id,
        Integer numero,
        String identificador,
        StatusCompra status,
        LocalDate dataCompra,
        List<String> fornecedores,
        boolean pago,
        BigDecimal total,
        int quantidadeItens
) {}
