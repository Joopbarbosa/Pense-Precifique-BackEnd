package com.penseprecifique.api.compra.nota;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * V0.16.0 (#681, RN-NOVA-13, DT-NOVA-11) — casamento do item da nota com os insumos, sem IA. Função pura.
 *
 * <ul>
 *   <li>Compara sem maiúscula, sem acento e sem pontuação.</li>
 *   <li>O insumo casa quando todas as palavras do nome dele aparecem no nome do item; "de, da, do, das,
 *       dos, e, com, para" não contam no nome do insumo.</li>
 *   <li>Insumo com marca preenchida só casa se as palavras da marca também aparecem no item; sem marca
 *       (ou com "não validar marca", que exige marca vazia) casa só pelo nome.</li>
 *   <li>Liga sozinho apenas com um único candidato; dois ou mais ficam para a artesã escolher.</li>
 * </ul>
 */
public final class CasamentoPorNome {

    private static final Set<String> LIGACAO = Set.of("de", "da", "do", "das", "dos", "e", "com", "para");

    private CasamentoPorNome() {
    }

    /** Nome do item como chave do vínculo: sem acento, sem maiúscula, espaços repetidos viram um. */
    public static String normalizarChave(String texto) {
        if (texto == null) {
            return "";
        }
        return semAcento(texto).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Palavras para comparação: sem acento, sem maiúscula, pontuação vira separador. */
    static Set<String> palavras(String texto) {
        if (texto == null || texto.isBlank()) {
            return Set.of();
        }
        String limpo = semAcento(texto).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (limpo.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(limpo.split(" ")));
    }

    /** O insumo ({@code nome}, {@code marca}) casa com o item da nota? */
    public static boolean casa(String nomeItem, String nomeInsumo, String marcaInsumo) {
        Set<String> doItem = palavras(nomeItem);
        Set<String> doInsumo = new LinkedHashSet<>(palavras(nomeInsumo));
        doInsumo.removeAll(LIGACAO);
        if (doInsumo.isEmpty() || !doItem.containsAll(doInsumo)) {
            return false;
        }
        Set<String> daMarca = palavras(marcaInsumo);
        return daMarca.isEmpty() || doItem.containsAll(daMarca);
    }

    /** Candidatos que casam, na ordem recebida. */
    public static <T> List<T> candidatos(String nomeItem, List<T> insumos, Function<T, String> nome, Function<T, String> marca) {
        return insumos.stream().filter(i -> casa(nomeItem, nome.apply(i), marca.apply(i))).toList();
    }

    /** Tem alguma palavra (fora as de ligação) em comum? Pré-filtro dos candidatos enviados à IA. */
    public static boolean temPalavraEmComum(String nomeItem, String nomeInsumo) {
        Set<String> doInsumo = new LinkedHashSet<>(palavras(nomeInsumo));
        doInsumo.removeAll(LIGACAO);
        Set<String> doItem = palavras(nomeItem);
        return doInsumo.stream().anyMatch(doItem::contains);
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
