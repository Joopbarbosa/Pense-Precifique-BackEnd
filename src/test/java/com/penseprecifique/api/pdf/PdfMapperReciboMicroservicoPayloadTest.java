package com.penseprecifique.api.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoPdfMultaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboEstornoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboPagamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboSinalPayload;
import com.penseprecifique.api.shared.dto.pdf.ReciboPagamentoPdfData;
import com.penseprecifique.api.shared.dto.pdf.ReciboPdfData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #248 — {@code PdfMapper.toReciboSinalMicroservicoPayload}/{@code toPdfMultaMicroservicoPayload}/
 * {@code toReciboEstornoMicroservicoPayload}/{@code toReciboPagamentoMicroservicoPayload} precisam
 * bater exatamente com {@code reciboSinalSchema}/{@code pdfMultaSchema}/{@code reciboEstornoSchema}/
 * {@code reciboPagamentoSchema} (contrato-pdf.md). Mesmo padrão de
 * {@link PdfMapperMicroservicoPayloadTest} — comparação de árvore JSON, teste unitário puro.
 */
class PdfMapperReciboMicroservicoPayloadTest {

    private final PdfMapper pdfMapper = new PdfMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reciboSinalPayloadBateComOSchemaDoMicroservico() throws Exception {
        ReciboPdfData dados = ReciboPdfData.builder()
                .numeroFormatado("47")
                .nomeCliente("Mariana Costa")
                .nomeEmpresa("Studio da Ana")
                .emailEmpresa("ana@studio.com")
                .telefoneEmpresa("(11) 99999-1234")
                .metodoRecebido("Pix")
                .valorRecebido("R$ 150,00")
                .dataAprovacao("01/01/2026")
                .prazoProducao("15 dias úteis")
                .inicioProducao("Assim que aprovado")
                .build();

        PdfMicroservicoReciboSinalPayload payload = pdfMapper.toReciboSinalMicroservicoPayload(dados);

        String esperado = """
                {
                  "empresa": {
                    "nome": "Studio da Ana",
                    "email": "ana@studio.com",
                    "whatsapp": "(11) 99999-1234",
                    "logoUrl": null
                  },
                  "documento": {
                    "numeroFormatado": "47",
                    "nomeCliente": "Mariana Costa",
                    "metodoRecebido": "Pix",
                    "valorRecebido": "R$ 150,00",
                    "dataAprovacao": "01/01/2026",
                    "prazoProducao": "15 dias úteis",
                    "inicioProducao": "Assim que aprovado"
                  }
                }
                """;

        assertEquals(objectMapper.readTree(esperado), objectMapper.valueToTree(payload));
    }

    @Test
    void pdfMultaPayloadBateComOSchemaDoMicroservico() throws Exception {
        ReciboPdfData dados = ReciboPdfData.builder()
                .numeroFormatado("9")
                .nomeCliente("Cliente X")
                .nomeEmpresa("Studio")
                .emailEmpresa(null)
                .telefoneEmpresa(null)
                .motivo("Cliente desistiu da encomenda")
                .percentualMulta("10%")
                .valorMulta("R$ 30,00")
                .dataAprovacao("01/01/2026")
                .prazoProducao("5 dias úteis")
                .inicioProducao("—")
                .build();

        PdfMicroservicoPdfMultaPayload payload = pdfMapper.toPdfMultaMicroservicoPayload(dados);

        String esperado = """
                {
                  "empresa": { "nome": "Studio", "email": null, "whatsapp": null, "logoUrl": null },
                  "documento": {
                    "numeroFormatado": "9",
                    "nomeCliente": "Cliente X",
                    "motivo": "Cliente desistiu da encomenda",
                    "percentualMulta": "10%",
                    "valorMulta": "R$ 30,00",
                    "dataAprovacao": "01/01/2026",
                    "prazoProducao": "5 dias úteis",
                    "inicioProducao": "—"
                  }
                }
                """;

        assertEquals(objectMapper.readTree(esperado), objectMapper.valueToTree(payload));
    }

    @Test
    void pdfMultaPayloadEnviaMotivoNullQuandoCancelamentoNaoTemMotivoRegistrado() {
        ReciboPdfData dados = ReciboPdfData.builder()
                .numeroFormatado("9")
                .nomeCliente("Cliente X")
                .nomeEmpresa("Studio")
                .motivo(null)
                .percentualMulta("10%")
                .valorMulta("R$ 30,00")
                .dataAprovacao("—")
                .prazoProducao("—")
                .inicioProducao("—")
                .build();

        PdfMicroservicoPdfMultaPayload payload = pdfMapper.toPdfMultaMicroservicoPayload(dados);

        assertNull(payload.getDocumento().getMotivo());
    }

    @Test
    void reciboEstornoPayloadBateComOSchemaDoMicroservico() throws Exception {
        ReciboPdfData dados = ReciboPdfData.builder()
                .numeroFormatado("9")
                .nomeCliente("Cliente X")
                .nomeEmpresa("Studio")
                .emailEmpresa(null)
                .telefoneEmpresa(null)
                .valorRecebido("R$ 150,00")
                .dataEstorno("05/01/2026")
                .build();

        PdfMicroservicoReciboEstornoPayload payload = pdfMapper.toReciboEstornoMicroservicoPayload(dados);

        String esperado = """
                {
                  "empresa": { "nome": "Studio", "email": null, "whatsapp": null, "logoUrl": null },
                  "documento": {
                    "numeroFormatado": "9",
                    "nomeCliente": "Cliente X",
                    "valorRecebido": "R$ 150,00",
                    "dataEstorno": "05/01/2026"
                  }
                }
                """;

        assertEquals(objectMapper.readTree(esperado), objectMapper.valueToTree(payload));
    }

    @Test
    void reciboPagamentoPayloadBateComOSchemaDoMicroservico() throws Exception {
        ReciboPagamentoPdfData dados = ReciboPagamentoPdfData.builder()
                .numeroFormatado("47")
                .nomeCliente("Mariana Costa")
                .nomeEmpresa("Studio da Ana")
                .emailEmpresa("ana@studio.com")
                .telefoneEmpresa("(11) 99999-1234")
                .metodoPagamento("Pix")
                .valorTotal("R$ 1.000,00")
                .valorSinalPago("R$ 200,00")
                .valorRestantePago("R$ 800,00")
                .totalQuitado("R$ 1.000,00")
                .dataAprovacao("01/01/2026")
                .prazoProducao("15 dias úteis")
                .inicioProducao("Assim que aprovado")
                .dataPagamento("01/03/2026")
                .build();

        PdfMicroservicoReciboPagamentoPayload payload = pdfMapper.toReciboPagamentoMicroservicoPayload(dados);

        String esperado = """
                {
                  "empresa": {
                    "nome": "Studio da Ana",
                    "email": "ana@studio.com",
                    "whatsapp": "(11) 99999-1234",
                    "logoUrl": null
                  },
                  "documento": {
                    "numeroFormatado": "47",
                    "nomeCliente": "Mariana Costa",
                    "metodoPagamento": "Pix",
                    "valorTotal": "R$ 1.000,00",
                    "valorSinalPago": "R$ 200,00",
                    "valorRestantePago": "R$ 800,00",
                    "totalQuitado": "R$ 1.000,00",
                    "dataAprovacao": "01/01/2026",
                    "prazoProducao": "15 dias úteis",
                    "inicioProducao": "Assim que aprovado",
                    "dataPagamento": "01/03/2026"
                  }
                }
                """;

        assertEquals(objectMapper.readTree(esperado), objectMapper.valueToTree(payload));
    }
}
