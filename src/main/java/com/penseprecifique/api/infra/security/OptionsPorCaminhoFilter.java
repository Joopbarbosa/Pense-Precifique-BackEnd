package com.penseprecifique.api.infra.security;

import com.penseprecifique.api.infra.config.MapeamentoDeMetodos;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * #808 — o {@code OPTIONS} automático do Spring anuncia também os métodos de {@code /recurso/{id}} num caminho
 * literal ({@code /recurso/contagens}). Para pedidos {@code OPTIONS} comuns (não preflight de CORS), o {@code Allow}
 * sai com os métodos do endereço mais específico. Roda depois da segurança: só responde quem já passou por ela.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class OptionsPorCaminhoFilter extends OncePerRequestFilter {

    private final MapeamentoDeMetodos metodos;

    public OptionsPorCaminhoFilter(MapeamentoDeMetodos metodos) {
        this.metodos = metodos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean preflight = request.getHeader("Origin") != null && request.getHeader("Access-Control-Request-Method") != null;
        if (!"OPTIONS".equals(request.getMethod()) || preflight) {
            chain.doFilter(request, response);
            return;
        }
        Set<String> permitidos = metodos.metodosDoCaminhoMaisEspecifico(request.getRequestURI());
        if (permitidos.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Set<String> allow = new TreeSet<>(permitidos);
        if (allow.contains("GET")) {
            allow.add("HEAD");
        }
        allow.add("OPTIONS");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Allow", String.join(",", allow));
    }
}
