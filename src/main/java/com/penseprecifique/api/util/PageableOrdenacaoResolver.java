package com.penseprecifique.api.util;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.Map;

/**
 * #354 — extraído de ProducaoService#resolverPageableOrdenado (#158/RN-NOVA-6), único ponto do
 * sistema que já validava o Sort do cliente contra uma allowlist antes de repassar pro Hibernate.
 * Os outros 4 endpoints paginados (orçamento, produto, cliente, insumo) repassavam o Pageable cru —
 * um campo de sort inexistente na entidade derrubava a request com 500
 * (UnknownPathException/InvalidDataAccessApiUsageException) em vez de 400. Campo fora da allowlist
 * é sempre rejeitado com BusinessException, nunca ignorado em silêncio nem repassado cru pro Sort.
 */
public final class PageableOrdenacaoResolver {

    private PageableOrdenacaoResolver() {
    }

    /**
     * Para allowlists cujo valor é uma propriedade JPA de verdade (simples ou aninhada, ex.
     * "cliente.nome") — resolvida com {@code Sort.by} comum, validada normalmente pelo Spring Data
     * contra os metadados da entidade.
     */
    public static Pageable resolver(Pageable pageable, Map<String, String> camposPermitidos,
                                     String descricaoCamposPermitidos) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort sortResolvido = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String expressao = validarCampo(order, camposPermitidos, descricaoCamposPermitidos);
            sortResolvido = sortResolvido.and(Sort.by(order.getDirection(), expressao));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortResolvido);
    }

    /**
     * Para allowlists cujo nome público não corresponde a uma propriedade JPA real (agregação, ex.
     * MIN/SUM em @Query com GROUP BY) — resolvida com {@code JpaSort.unsafe}, expressão JPQL crua.
     */
    public static Pageable resolverExpressaoJpql(Pageable pageable, Map<String, String> camposPermitidos,
                                                  String descricaoCamposPermitidos) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort sortResolvido = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String expressao = validarCampo(order, camposPermitidos, descricaoCamposPermitidos);
            sortResolvido = sortResolvido.and(JpaSort.unsafe(order.getDirection(), expressao));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortResolvido);
    }

    private static String validarCampo(Sort.Order order, Map<String, String> camposPermitidos,
                                        String descricaoCamposPermitidos) {
        String expressao = camposPermitidos.get(order.getProperty());
        if (expressao == null) {
            throw new BusinessException("Campo de ordenação inválido: '" + order.getProperty()
                    + "'. Permitidos: " + descricaoCamposPermitidos + ".");
        }
        return expressao;
    }
}
