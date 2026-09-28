package com.penseprecifique.api.shared.exception;

import java.util.List;

/**
 * Erro de regra de negócio (BLOQUEIO) → 400. V0.15.0 (#602, RN-NOVA-32/DT-NOVA-21): pode carregar a
 * explicação da modal de erro padrão — use {@link #explicado(String, String, String, String)} e,
 * quando houver vários problemas, {@link #comItens(List)}. O construtor simples continua valendo para
 * os erros ainda não convertidos.
 */
public class BusinessException extends RuntimeException {

    private final String titulo;
    private final String motivo;
    private final String comoResolver;
    private final List<String> itens;

    public BusinessException(String message) {
        this(message, null, null, null, null);
    }

    private BusinessException(String message, String titulo, String motivo, String comoResolver, List<String> itens) {
        super(message);
        this.titulo = titulo;
        this.motivo = motivo;
        this.comoResolver = comoResolver;
        this.itens = itens;
    }

    /** {@code oQueAconteceu} vira a {@code message}. */
    public static BusinessException explicado(String titulo, String oQueAconteceu, String motivo, String comoResolver) {
        return new BusinessException(oQueAconteceu, titulo, motivo, comoResolver, null);
    }

    public BusinessException comItens(List<String> itens) {
        return new BusinessException(getMessage(), titulo, motivo, comoResolver, itens == null ? null : List.copyOf(itens));
    }

    public String getTitulo() { return titulo; }
    public String getMotivo() { return motivo; }
    public String getComoResolver() { return comoResolver; }
    public List<String> getItens() { return itens; }
}
