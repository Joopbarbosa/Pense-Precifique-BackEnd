package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import com.penseprecifique.api.shared.dto.pdf.ReciboPdfData;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoEmpresaPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** V0.17.0 #965 (RN-NOVA-20, CEN-NOVO-60): o logo cadastrado chega ao payload dos documentos do orçamento. */
class PdfMapperLogoEmpresaTest {

    private static final String LOGO = "https://r2.exemplo/logos/ateliê.png";

    private final PdfMapper pdfMapper = new PdfMapper();

    @Test
    void orcamentoLevaOLogoDaEmpresaNoPayloadEEmTodosOsCincoDocumentos() {
        Empresa empresa = Empresa.builder().nome("Ateliê Com Logo").logoUrl(LOGO).build();
        Orcamento orcamento = Orcamento.builder().numero(1).build();

        OrcamentoPdfData orc = pdfMapper.toOrcamentoPdfData(orcamento, empresa, List.of(), Map.of());
        assertEquals(LOGO, orc.getLogoUrlEmpresa());
        assertEquals(LOGO, pdfMapper.toMicroservicoPayload(orc).getEmpresa().getLogoUrl());

        ReciboPdfData recibo = pdfMapper.toReciboPdfData(orcamento, empresa, List.of(), Map.of());
        assertEquals(LOGO, recibo.getLogoUrlEmpresa());
        assertEquals(LOGO, pdfMapper.toReciboSinalMicroservicoPayload(recibo).getEmpresa().getLogoUrl());
        assertEquals(LOGO, pdfMapper.toPdfMultaMicroservicoPayload(pdfMapper.toReciboPdfDataMulta(orcamento, empresa, List.of(), Map.of())).getEmpresa().getLogoUrl());
        assertEquals(LOGO, pdfMapper.toReciboEstornoMicroservicoPayload(pdfMapper.toReciboPdfDataEstorno(orcamento, empresa, List.of(), Map.of())).getEmpresa().getLogoUrl());
    }

    @Test
    void empresaSemLogoMandaNuloParaACInicialAparecer() {
        Empresa empresa = Empresa.builder().nome("Ateliê Sem Logo").build();
        Orcamento orcamento = Orcamento.builder().numero(2).build();

        OrcamentoPdfData orc = pdfMapper.toOrcamentoPdfData(orcamento, empresa, List.of(), Map.of());
        assertNull(orc.getLogoUrlEmpresa());
        PdfMicroservicoEmpresaPayload payload = pdfMapper.toMicroservicoPayload(orc).getEmpresa();
        assertNull(payload.getLogoUrl());
        assertEquals("Ateliê Sem Logo", payload.getNome());
    }
}
