package com.penseprecifique.api.caixa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.17.0 #973 (RN-NOVA-26, DT-NOVA-19) — uma venda do histórico do Caixa. {@code cliente} já vem com "Consumidor final" quando a venda
 * não tem cliente, para a tela e a ordenação usarem o mesmo texto. {@code formas} é a lista das formas (ou "Não informada").
 */
public record HistoricoVendaResponse(
        UUID id,
        String identificador,
        long numero,
        LocalDate data,
        String cliente,
        String itens,
        java.util.List<String> formas,
        BigDecimal total,
        String status
) {}
