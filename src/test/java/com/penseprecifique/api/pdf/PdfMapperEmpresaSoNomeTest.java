package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** #339 (DT-NOVA-14) — empresa criada no cadastro só com o nome não pode quebrar o PDF. */
class PdfMapperEmpresaSoNomeTest {

    private final PdfMapper pdfMapper = new PdfMapper();

    @Test
    void orcamentoComEmpresaSoComNomeUsaONomeEDeixaContatoVazio() {
        Empresa empresa = Empresa.builder().nome("Ateliê da Ana").build();
        Orcamento orc = Orcamento.builder().numero(1).subtotal(BigDecimal.TEN).total(BigDecimal.TEN).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orc, empresa, List.of(), Map.of());

        assertEquals("Ateliê da Ana", dados.getNomeEmpresa());
        assertNull(dados.getEmailEmpresa());
        assertNull(dados.getTelefoneEmpresa());
    }
}
