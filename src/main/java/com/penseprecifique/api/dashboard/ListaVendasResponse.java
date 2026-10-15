package com.penseprecifique.api.dashboard;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * #719/RN-NOVA-17 — aba Vendas: todas as vendas do Caixa e todos os orçamentos, de qualquer status. {@code totalFiltrado} e
 * {@code quantidadeFiltrada} valem para o conjunto inteiro filtrado (não só a página), para a conferência com o número de origem.
 */
public record ListaVendasResponse(Page<Linha> vendas, BigDecimal totalFiltrado, long quantidadeFiltrada) {

    /** {@code origem}: VENDA_CAIXA ou ORCAMENTO; {@code contaNosNumeros}: venda concluída/entregue (RN-NOVA-12). */
    public record Linha(UUID id, String origem, String identificador, LocalDate data, String cliente, String itens,
                        BigDecimal total, String status, boolean contaNosNumeros) {}
}
