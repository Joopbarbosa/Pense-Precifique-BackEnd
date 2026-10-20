package com.penseprecifique.api.util;

import java.text.Normalizer;
import java.util.Locale;

/** #1017 (RN-NOVA-36) — marca opcional e chave de unicidade nome + marca (sem maiúsculas, acentos e espaços das pontas). */
public final class NomeMarca {

    public static final int MARCA_MAX = 100;

    private NomeMarca() {
    }

    /** Marca como gravada: espaços das pontas removidos; vazia vira nula. */
    public static String limparMarca(String marca) {
        if (marca == null) return null;
        String limpa = marca.strip();
        return limpa.isEmpty() ? null : limpa;
    }

    /** Chave de comparação de um texto: sem acentos, minúsculo e espaços das pontas removidos; espaços internos continuam distintos (RN-NOVA-36). */
    public static String chave(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Mesma identidade: nome e marca iguais pela chave (marca ausente igual a marca ausente). */
    public static boolean mesmaIdentidade(String nomeA, String marcaA, String nomeB, String marcaB) {
        return chave(nomeA).equals(chave(nomeB)) && chave(marcaA).equals(chave(marcaB));
    }

    /** Rótulo de exibição: "Nome — Marca" ou só o nome. */
    public static String rotulo(String nome, String marca) {
        return marca == null || marca.isBlank() ? nome : nome + " — " + marca;
    }
}
