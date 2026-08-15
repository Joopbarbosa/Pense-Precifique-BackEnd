package com.penseprecifique.api.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.shared.dto.pdf.ItemPdfData;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Épico #89, Passo 1/5 — {@code PdfMapper.toMicroservicoPayload()} precisa bater exatamente com
 * {@code orcamentoSchema} do microsserviço (contrato-pdf.md seção 1). Teste unitário puro (sem
 * @SpringBootTest, mesmo padrão de {@link PdfMapperNumeroFormatadoTest}) — a lógica não tem
 * dependência nenhuma além de formatação já feita a montante em {@code OrcamentoPdfData}.
 */
class PdfMapperMicroservicoPayloadTest {

    private final PdfMapper pdfMapper = new PdfMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mesmos valores do payload de exemplo de contrato-pdf.md seção 1 (idêntico ao fixture
     * {@code payloadValido} usado nos testes do microsserviço, `render.test.js`) — comparação de
     * árvore JSON pega divergência de campo/estrutura cedo, sem depender de leitura visual.
     */
    @Test
    void payloadBateExatamenteComOExemploDoContrato() throws Exception {
        OrcamentoPdfData dados = OrcamentoPdfData.builder()
                .numeroFormatado("47")
                .nomeEmpresa("Studio da Ana")
                .emailEmpresa("ana@studio.com")
                .telefoneEmpresa("(11) 99999-1234")
                .nomeCliente("Mariana Costa")
                .dataEmissao("10/08/2026")
                .dataValidade("20/08/2026")
                .prazoProducao("15 dias úteis")
                .inicioProducao("Assim que aprovado")
                .metodoPagamento("PIX")
                .sinalAtivo(true)
                .valorSinal("R$ 150,00")
                .restanteAposSinal("R$ 150,00")
                .subtotal("R$ 320,00")
                .desconto("R$ 20,00")
                .total("R$ 300,00")
                .observacoes("Embalagem para presente incluída.")
                .itens(List.of(ItemPdfData.builder()
                        .nomeProduto("Bolo Vulcão de Chocolate")
                        .customizacoes("Sem glúten, Cobertura extra")
                        .quantidade("2")
                        .precoUnitario("R$ 150,00")
                        .subtotal("R$ 300,00")
                        .build()))
                .build();

        PdfMicroservicoOrcamentoPayload payload = pdfMapper.toMicroservicoPayload(dados);

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
                    "telefoneCliente": null,
                    "emailCliente": null,
                    "dataEmissao": "10/08/2026",
                    "dataValidade": "20/08/2026",
                    "prazoProducao": "15 dias úteis",
                    "inicioProducao": "Assim que aprovado",
                    "metodoPagamento": "PIX",
                    "sinalAtivo": true,
                    "valorSinal": "R$ 150,00",
                    "restanteAposSinal": "R$ 150,00",
                    "percentualSinal": null,
                    "subtotal": "R$ 320,00",
                    "desconto": "R$ 20,00",
                    "percentualDesconto": null,
                    "total": "R$ 300,00",
                    "observacoes": "Embalagem para presente incluída.",
                    "itens": [
                      {
                        "nomeProduto": "Bolo Vulcão de Chocolate",
                        "customizacoes": "Sem glúten, Cobertura extra",
                        "quantidade": "2",
                        "precoUnitario": "R$ 150,00",
                        "subtotal": "R$ 300,00"
                      }
                    ]
                  }
                }
                """;

        JsonNode arvoreEsperada = objectMapper.readTree(esperado);
        JsonNode arvoreReal = objectMapper.valueToTree(payload);

        assertEquals(arvoreEsperada, arvoreReal);
    }

    /**
     * Achado do Passo 0 (não implementado em f1c404b apesar de contrato-pdf.md descrever como já
     * corrigido): {@code formatarCustomizacoes} usa "—" como placeholder de exibição do template
     * Thymeleaf local — o schema do microsserviço exige {@code null} explícito quando o item não
     * tem customização, nunca esse placeholder. Só a tradução em {@code toMicroservicoPayload}
     * precisa desse cuidado extra.
     */
    @Test
    void itemSemCustomizacaoEnviaNullParaOMicroservicoNuncaOPlaceholder() {
        OrcamentoPdfData dados = OrcamentoPdfData.builder()
                .numeroFormatado("1")
                .nomeEmpresa("Studio")
                .nomeCliente("Cliente")
                .dataEmissao("—")
                .dataValidade("Não definida")
                .prazoProducao("—")
                .inicioProducao("—")
                .metodoPagamento("Pix")
                .sinalAtivo(false)
                .subtotal("R$ 0,00")
                .total("R$ 0,00")
                .itens(List.of(ItemPdfData.builder()
                        .nomeProduto("Produto sem customização")
                        .customizacoes("—") // saída real de formatarCustomizacoes quando a lista é vazia
                        .quantidade("1")
                        .precoUnitario("R$ 0,00")
                        .subtotal("R$ 0,00")
                        .build()))
                .build();

        PdfMicroservicoOrcamentoPayload payload = pdfMapper.toMicroservicoPayload(dados);

        assertNull(payload.getDocumento().getItens().get(0).getCustomizacoes(),
                "customizacoes deveria virar null pro microsserviço, nunca o placeholder '—'");
    }

    /**
     * Frente B (#248) — telefoneCliente/emailCliente/percentualSinal/percentualDesconto passam
     * direto de {@link OrcamentoPdfData} para o payload, sem transformação adicional (a formatação
     * já aconteceu em {@code PdfMapper.toOrcamentoPdfData}).
     */
    @Test
    void frenteBPopulaOs4CamposPendentesQuandoPresentesEmOrcamentoPdfData() {
        OrcamentoPdfData dados = OrcamentoPdfData.builder()
                .numeroFormatado("1")
                .nomeEmpresa("Studio")
                .nomeCliente("Cliente")
                .telefoneCliente("(11) 98888-7777")
                .emailCliente("cliente@teste.com")
                .dataEmissao("—")
                .dataValidade("Não definida")
                .prazoProducao("—")
                .inicioProducao("—")
                .metodoPagamento("Pix")
                .sinalAtivo(true)
                .percentualSinal("50%")
                .subtotal("R$ 100,00")
                .percentualDesconto("15%")
                .total("R$ 85,00")
                .itens(List.of())
                .build();

        PdfMicroservicoOrcamentoPayload payload = pdfMapper.toMicroservicoPayload(dados);

        assertEquals("(11) 98888-7777", payload.getDocumento().getTelefoneCliente());
        assertEquals("cliente@teste.com", payload.getDocumento().getEmailCliente());
        assertEquals("50%", payload.getDocumento().getPercentualSinal());
        assertEquals("15%", payload.getDocumento().getPercentualDesconto());
    }
}
