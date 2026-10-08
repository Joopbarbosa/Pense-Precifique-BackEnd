package com.penseprecifique.api.compra.nota;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.16.0 (#681, RN-NOVA-13) — casamento por nome, sem banco. */
class CasamentoPorNomeTest {

    record Ins(String nome, String marca) {}

    private static List<Ins> candidatos(String item, Ins... insumos) {
        return CasamentoPorNome.candidatos(item, List.of(insumos), Ins::nome, Ins::marca);
    }

    @Test
    void palavrasDeLigacaoNaoContamNoNomeDoInsumo() {
        assertTrue(CasamentoPorNome.casa("FITA CETIM 10MM", "Fita de cetim", null));
        assertTrue(CasamentoPorNome.casa("PAPEL COUCHE 250G A4 C/100", "Papel couché A4 250g", null), "acento e ordem não importam");
    }

    @Test
    void todasAsPalavrasDoInsumoPrecisamAparecer() {
        assertFalse(CasamentoPorNome.casa("FITA 10MM", "Fita de cetim", null));
        assertFalse(CasamentoPorNome.casa("FITA CETIM", "de", null), "insumo só com palavra de ligação não casa com nada");
    }

    @Test
    void cen15_insumoSemMarcaCasaPeloNomeComQualquerMarcaNaNota() {
        assertEquals(List.of(new Ins("Caneta gel azul", null)),
                candidatos("CANETA GEL AZUL 0.5MM BIC", new Ins("Caneta gel azul", null), new Ins("Caneta preta", null)));
    }

    @Test
    void cen16_insumoComMarcaCasaQuandoAMarcaApareceNoItem() {
        assertTrue(CasamentoPorNome.casa("FITA CETIM PROGRESSO 10MM", "Fita de cetim", "Progresso"));
    }

    @Test
    void cen47_marcaDiferenteNaoCasa() {
        assertFalse(CasamentoPorNome.casa("FITA CETIM 10MM LIDER", "Fita de cetim", "Progresso"));
    }

    @Test
    void cen46_doisInsumosCasamEAmbosViramCandidatos() {
        assertEquals(2, candidatos("PAPEL SULFITE A4 RECICLADO 75G",
                new Ins("Papel sulfite A4", null), new Ins("Papel sulfite A4 reciclado", null)).size());
    }

    @Test
    void cen59_candidatosSaoOsQueTemTodasAsPalavrasNoItem() {
        List<Ins> achados = candidatos("CANETA GEL AZUL",
                new Ins("Caneta azul", null), new Ins("Caneta gel", null), new Ins("Caneta preta", null));
        assertEquals(List.of(new Ins("Caneta azul", null), new Ins("Caneta gel", null)), achados);
    }

    @Test
    void chaveDoVinculoIgnoraMaiusculaAcentoEEspacosRepetidos() {
        assertEquals("papel couche 250g a4 c/100", CasamentoPorNome.normalizarChave("  PAPEL  COUCHÉ 250G A4   C/100 "));
    }
}
