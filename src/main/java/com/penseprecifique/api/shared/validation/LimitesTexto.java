package com.penseprecifique.api.shared.validation;

/**
 * #559/RN-NOVA-18 (V0.15.0, DT-NOVA-11) — limite global de texto livre de descrição, observação e
 * justificativa. Validado na aplicação ({@code @Size} nos DTOs de request); as colunas continuam
 * TEXT, para não falhar com textos antigos maiores. Exceção: {@code itens_catalogo.descricao}
 * mantém 150 (limite menor prevalece).
 */
public final class LimitesTexto {

    public static final int DESCRICAO_MAX = 500;
    public static final String DESCRICAO_MAX_MENSAGEM = "Máximo de 500 caracteres";

    /**
     * #483 — caractere nulo (\u0000) é recusado pelo Postgres em TEXT/VARCHAR e virava 500. Usado
     * com {@code @Pattern} nos campos de texto do request.
     */
    public static final String SEM_CARACTERE_NULO = "[^\\x00]*";
    public static final String SEM_CARACTERE_NULO_MENSAGEM = "Contém caractere inválido";

    private LimitesTexto() {
    }
}
