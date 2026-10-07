package com.penseprecifique.api.shared.texto;

import java.text.Normalizer;
import java.util.Locale;

/**
 * #750 (V0.16.0) — normalização de texto para comparação e busca: sem acento, sem maiúscula e sem espaço nas
 * pontas. Um só lugar para o histórico de clientes e para o casamento/vínculo de itens de nota; função pura.
 */
public final class TextoNormalizado {

    private TextoNormalizado() {
    }

    /** "  Açúcar Refinado " → "acucar refinado"; nulo vira vazio. Espaços do meio ficam como estão. */
    public static String semAcentoMinusculo(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Como {@link #semAcentoMinusculo}, e sequências de espaços viram um só. */
    public static String semAcentoMinusculoEspacoUnico(String texto) {
        return semAcentoMinusculo(texto).replaceAll("\\s+", " ");
    }
}
