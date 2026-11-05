package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.exception.BusinessException;

import java.text.Normalizer;
import java.util.Locale;

/** V0.19.0 (RN-NOVA-23) — nome de categoria: 2 a 40 caracteres; a unicidade ignora maiúsculas, acentos e espaços nas pontas. */
final class NomeCategoria {

    static final int MINIMO = 2;
    static final int MAXIMO = 40;
    static final String MENSAGEM = "O nome da categoria deve ter de 2 a 40 caracteres e não pode conter caracteres inválidos.";

    private NomeCategoria() {}

    /** Devolve o nome aparado ou lança bloqueio no campo {@code nome}. Antes de aparar, rejeita texto enorme sem copiá-lo. */
    static String exigir(String nome) {
        if (nome == null || nome.length() > 1000) {
            if (nome == null) throw BusinessException.noCampo("nome", "Informe o nome da categoria.");
            throw BusinessException.noCampo("nome", MENSAGEM);
        }
        String texto = nome.strip();
        if (texto.isEmpty()) {
            throw BusinessException.noCampo("nome", "Informe o nome da categoria.");
        }
        if (texto.length() < MINIMO || texto.length() > MAXIMO || texto.codePoints().anyMatch(Character::isISOControl)) {
            throw BusinessException.noCampo("nome", MENSAGEM);
        }
        return texto;
    }

    static String normalizar(String nome) {
        String semAcento = Normalizer.normalize(nome.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).strip();
    }
}
