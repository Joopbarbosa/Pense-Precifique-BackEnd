package com.penseprecifique.api.infra.config;

import com.penseprecifique.api.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * V0.17.0 #994 (RN-NOVA-31, DT-NOVA-27 item 6) — entrada hostil de toda listagem paginada, num só lugar:
 * {@code page} inteiro maior ou igual a 0, {@code size} inteiro de 1 a 100 e {@code busca} com até 255 caracteres.
 * Sem isto o Spring corrige em silêncio ({@code page=-1} vira 0, {@code size=0} vira o padrão e {@code size=101}
 * passa até 2000) — a mesma regra do histórico do Caixa (RN-NOVA-26) passa a valer para os 13 pontos.
 * A ordenação fora da lista permitida continua sendo recusada por {@code PageableOrdenacaoResolver} em cada serviço.
 */
@Component
public class ValidacaoPaginacaoInterceptor implements HandlerInterceptor {

    static final int TAMANHO_MAXIMO = 100;
    static final int BUSCA_MAXIMA = 255;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }
        String page = request.getParameter("page");
        if (page != null && (inteiro(page) == null || inteiro(page) < 0)) {
            throw new BusinessException("Página inválida: informe um número inteiro maior ou igual a 0.");
        }
        String size = request.getParameter("size");
        if (size != null && (inteiro(size) == null || inteiro(size) < 1 || inteiro(size) > TAMANHO_MAXIMO)) {
            throw new BusinessException("Tamanho de página inválido: informe um número inteiro de 1 a " + TAMANHO_MAXIMO + ".");
        }
        String busca = request.getParameter("busca");
        if (busca != null && busca.length() > BUSCA_MAXIMA) {
            throw new BusinessException("Busca muito longa: use no máximo " + BUSCA_MAXIMA + " caracteres.");
        }
        return true;
    }

    private static Integer inteiro(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
