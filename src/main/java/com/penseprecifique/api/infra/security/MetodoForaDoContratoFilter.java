package com.penseprecifique.api.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * #785 — método HTTP que o Spring MVC não despacha (TRACE, PROPFIND, QUERY...) caía no servlet base, que responde
 * com {@code sendError}; o redirecionamento de erro esbarrava na segurança e saía 401 vazio, mesmo com token válido.
 * Aqui o 405 sai no formato padrão de erro, com {@code Allow}, antes da segurança.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MetodoForaDoContratoFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (METODOS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setHeader("Allow", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Esta operação não é permitida para este endereço.\",\"status\":405,"
                + "\"timestamp\":\"" + LocalDateTime.now() + "\",\"fieldErrors\":null,\"titulo\":null,\"motivo\":null,"
                + "\"comoResolver\":null,\"itens\":null}");
    }
}
