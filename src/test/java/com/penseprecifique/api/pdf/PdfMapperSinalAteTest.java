package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** V0.18.0 #1105: a data "Sinal até" chega formatada ao payload do PDF e do preview; sem data, vai nula. */
class PdfMapperSinalAteTest {

    private final PdfMapper pdfMapper = new PdfMapper();

    @Test
    void sinalAteChegaFormatadoAoPayload() {
        Orcamento orcamento = Orcamento.builder().numero(1).sinalAte(LocalDate.of(2026, 11, 5)).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertEquals("05/11/2026", dados.getSinalAte());
        assertEquals("05/11/2026", pdfMapper.toMicroservicoPayload(dados).getDocumento().getSinalAte());
    }

    @Test
    void semSinalAteVaiNulo() {
        Orcamento orcamento = Orcamento.builder().numero(2).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertNull(dados.getSinalAte());
        assertNull(pdfMapper.toMicroservicoPayload(dados).getDocumento().getSinalAte());
    }
}
