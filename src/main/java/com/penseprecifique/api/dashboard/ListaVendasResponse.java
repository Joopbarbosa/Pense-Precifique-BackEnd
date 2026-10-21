package com.penseprecifique.api.dashboard;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * #719/RN-NOVA-17 — aba Vendas: todas as vendas do Caixa e todos os orçamentos, de qualquer status. {@code totalFiltrado} e
 * {@code quantidadeFiltrada} valem para o conjunto inteiro filtrado (não só a página), para a conferência com o número de origem.
 * #988 (DT-NOVA-25): quando a lista é filtrada por um produto, {@code unidadesDoProduto} traz a soma das unidades **desse produto** nas
 * vendas listadas (uma venda de 3 unidades é 1 venda e 3 unidades); sem filtro de produto vem nulo.
 */
public record ListaVendasResponse(Page<Linha> vendas, BigDecimal totalFiltrado, long quantidadeFiltrada, BigDecimal unidadesDoProduto) {

    /** {@code origem}: VENDA_CAIXA ou ORCAMENTO; {@code contaNosNumeros}: venda concluída/entregue (RN-NOVA-12). */
    public record Linha(UUID id, String origem, String identificador, LocalDate data, String cliente, String itens,
                        BigDecimal total, String status, boolean contaNosNumeros) {}
}
