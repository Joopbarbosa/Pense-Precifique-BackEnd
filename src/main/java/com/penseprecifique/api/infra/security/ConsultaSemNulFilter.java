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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * #784 — o caractere NUL ({@code %00}) numa consulta chega ao PostgreSQL, que o recusa ("invalid byte sequence") e a
 * API respondia 500. Nenhum parâmetro legítimo contém NUL: a requisição é recusada logo na entrada, com 400 no
 * formato padrão de erro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ConsultaSemNulFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String consulta = request.getQueryString();
        if (consulta != null && (consulta.indexOf('\u0000') >= 0 || decodificar(consulta).indexOf('\u0000') >= 0)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Os parâmetros da consulta são inválidos.\",\"status\":400,\"timestamp\":\""
                    + LocalDateTime.now() + "\",\"fieldErrors\":null,\"titulo\":null,\"motivo\":null,"
                    + "\"comoResolver\":null,\"itens\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String decodificar(String consulta) {
        try {
            return URLDecoder.decode(consulta, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return consulta;
        }
    }
}
