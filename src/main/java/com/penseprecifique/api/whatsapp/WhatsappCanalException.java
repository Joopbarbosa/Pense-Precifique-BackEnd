package com.penseprecifique.api.whatsapp;

/** Falha do canal já classificada; a mensagem nunca carrega chave nem corpo da resposta externa. */
public class WhatsappCanalException extends RuntimeException {

    public enum Tipo { INDISPONIVEL, DESCONECTADO, NUMERO_SEM_WHATSAPP }

    private final Tipo tipo;

    public WhatsappCanalException(Tipo tipo) {
        super(tipo.name());
        this.tipo = tipo;
    }

    public Tipo getTipo() { return tipo; }
}
