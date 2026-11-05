package com.penseprecifique.api.whatsapp;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V0.19.0 #1170 (RN-NOVA-24 a 36, DT-NOVA-13/14/15): conexão e envio de PDF pelo WhatsApp. Evolution e
 * microsserviço de PDF são falsos (WireMock); nenhuma mensagem real é enviada. Limite diário 3 e sem intervalo
 * aqui; o intervalo de 30 s está em {@link WhatsappIntervaloHttpIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WhatsappEnvioHttpIT extends WhatsappTestBase {

    @DynamicPropertySource
    static void falso(DynamicPropertyRegistry registry) {
        FALSO.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + FALSO.port());
        registry.add("evolution.base-url", () -> "http://localhost:" + FALSO.port());
        registry.add("evolution.api-key", () -> CHAVE_FALSA);
        registry.add("evolution.habilitado", () -> "true");
        registry.add("evolution.limite-diario", () -> "3");
        registry.add("evolution.intervalo-segundos", () -> "0");
    }

    @AfterAll
    static void parar() { FALSO.stop(); }

    private String auth(Conta c) { return "Bearer " + c.token(); }

    @Test
    void conectarSemConfirmarAvisoRetorna400ENaoCriaInstancia() throws Exception {
        Conta c = registrar("wa-semaceite");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/conexao")
                        .header("Authorization", auth(c)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirme que entendeu os riscos para conectar."));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/conexao")
                        .header("Authorization", auth(c)).contentType(MediaType.APPLICATION_JSON).content("{\"entendiRiscos\":false}"))
                .andExpect(status().isBadRequest());
        FALSO.verify(0, postRequestedFor(urlPathMatching("/instance/create")));
    }

    @Test
    void conectarComConfirmacaoDevolveQrESemChaveNaResposta() throws Exception {
        Conta c = registrar("wa-conectar");
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/conexao")
                        .header("Authorization", auth(c)).contentType(MediaType.APPLICATION_JSON).content("{\"entendiRiscos\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_QR")).andReturn();
        String corpo = r.getResponse().getContentAsString();
        assertTrue(corpo.contains("QRFALSO"));
        assertFalse(corpo.contains(CHAVE_FALSA));
        FALSO.verify(1, postRequestedFor(urlPathMatching("/instance/create")).withHeader("apikey", equalTo(CHAVE_FALSA)));
        // escaneado: o canal responde "open" e o status passa a CONECTADO
        mvc.perform(get("/whatsapp/status").header("Authorization", auth(c)))
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.estado").value("CONECTADO"));
    }

    @Test
    void desconectarVoltaParaNaoConectado() throws Exception {
        Conta c = contaConectada("wa-desconectar");
        mvc.perform(delete("/whatsapp/conexao").header("Authorization", auth(c))).andExpect(status().isNoContent());
        mvc.perform(get("/whatsapp/status").header("Authorization", auth(c)))
                .andExpect(jsonPath("$.estado").value("NAO_CONECTADO"));
    }

    @Test
    void envioParaNumeroDigitadoComConfirmacaoEnviaELegendaPadrao() throws Exception {
        Conta c = contaConectada("wa-digitado");
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"(11) 91234-5678\",\"confirmoAceite\":true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ENVIADO"))
                .andExpect(jsonPath("$.enviadosHoje").value(1))
                .andExpect(jsonPath("$.limiteDiario").value(3));
        var req = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*"))).get(0);
        JsonNodeAssert.verifica(json.readTree(req.getBodyAsString()), v.numero());
        var hist = envios.findByUsuarioIdOrderByEnviadoEmDesc(c.usuario().getId(), org.springframework.data.domain.PageRequest.of(0, 5));
        assertEquals(1, hist.size());
        assertEquals("5511912345678", hist.get(0).getNumero());
        assertTrue(hist.get(0).isConsentimentoConfirmado());
        assertEquals(ResultadoEnvio.ENVIADO, hist.get(0).getResultado());
    }

    @Test
    void documentoDeOnzePaginasEnviaAsDuasPartesEmSequenciaComUmaConfirmacao() throws Exception {
        Conta c = contaConectada("wa-pdf-partes");
        VendaCaixaResponseDTO venda = vender(c);
        String base64 = java.util.Base64.getEncoder().encodeToString("%PDF-parte".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FALSO.stubFor(post(urlPathMatching("/render/recibo-caixa/.*"))
                .withQueryParam("dividir", equalTo("false"))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("{\"tipo\":\"AVISO\",\"message\":\"Aviso de páginas\",\"totalPaginas\":11,\"dividirDisponivel\":true}")));
        FALSO.stubFor(post(urlPathMatching("/render/recibo-caixa/.*"))
                .withQueryParam("dividir", equalTo("true"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"totalPaginas\":11,\"partes\":[{\"numeroParte\":1,\"totalPaginas\":6,\"pdfBase64\":\"" + base64
                                + "\"},{\"numeroParte\":2,\"totalPaginas\":5,\"pdfBase64\":\"" + base64 + "\"}]}")));

        enviar(c, corpoEnvio(venda.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ENVIADO"));

        FALSO.verify(2, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
        var envios = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
        assertTrue(envios.get(0).getBodyAsString().contains("parte 1 de 2"));
        assertTrue(envios.get(1).getBodyAsString().contains("parte 2 de 2"));
    }

    @Test
    void seSegundaParteFalhaPermiteReenviarSomenteEla() throws Exception {
        Conta c = contaConectada("wa-pdf-retry-parte2");
        VendaCaixaResponseDTO venda = vender(c);
        String base64 = java.util.Base64.getEncoder().encodeToString("%PDF-parte".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FALSO.stubFor(post(urlPathMatching("/render/recibo-caixa/.*"))
                .withQueryParam("dividir", equalTo("false"))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("{\"tipo\":\"AVISO\",\"message\":\"Aviso de páginas\",\"totalPaginas\":11,\"dividirDisponivel\":true}")));
        FALSO.stubFor(post(urlPathMatching("/render/recibo-caixa/.*"))
                .withQueryParam("dividir", equalTo("true"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"totalPaginas\":11,\"partes\":[{\"numeroParte\":1,\"totalPaginas\":6,\"pdfBase64\":\"" + base64
                                + "\"},{\"numeroParte\":2,\"totalPaginas\":5,\"pdfBase64\":\"" + base64 + "\"}]}")));
        FALSO.stubFor(post(urlPathMatching("/message/sendMedia/.*")).atPriority(1)
                .inScenario("falha-na-segunda-parte").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(201).withBody("{}"))
                .willSetStateTo("parte-um-enviada"));
        FALSO.stubFor(post(urlPathMatching("/message/sendMedia/.*")).atPriority(1)
                .inScenario("falha-na-segunda-parte").whenScenarioStateIs("parte-um-enviada")
                .willReturn(aResponse().withStatus(500).withBody("falha simulada"))
                .willSetStateTo("segunda-parte-falhou"));
        FALSO.stubFor(post(urlPathMatching("/message/sendMedia/.*")).atPriority(1)
                .inScenario("falha-na-segunda-parte").whenScenarioStateIs("segunda-parte-falhou")
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        enviar(c, corpoEnvio(venda.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.primeiraParteEnviada").value(true))
                .andExpect(jsonPath("$.reenviarSegundaParteDisponivel").value(true));
        enviar(c, corpoEnvio(venda.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true,\"reenviarSegundaParte\":true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ENVIADO"));

        var mensagens = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
        assertEquals(3, mensagens.size());
        assertTrue(mensagens.get(0).getBodyAsString().contains("parte 1 de 2"));
        assertTrue(mensagens.get(1).getBodyAsString().contains("parte 2 de 2"));
        assertTrue(mensagens.get(2).getBodyAsString().contains("parte 2 de 2"));
    }

    @Test
    void pedidoHostilDeReenvioDaSegundaParteSemDivisaoRetorna400() throws Exception {
        Conta c = contaConectada("wa-pdf-parte2-hostil");
        VendaCaixaResponseDTO venda = vender(c);
        enviar(c, corpoEnvio(venda.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true,\"reenviarSegundaParte\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este envio não tem uma segunda parte para reenviar."));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void numeroDigitadoSemConfirmarAceiteBloqueiaENaoEnvia() throws Exception {
        Conta c = contaConectada("wa-semconf");
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirme que esta pessoa aceitou receber pelo WhatsApp."));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void numeroInvalidoRetorna400() throws Exception {
        Conta c = contaConectada("wa-invalido");
        VendaCaixaResponseDTO v = vender(c);
        for (String ruim : new String[]{"123", "abc", "1e20", "+55 119", "11912345678; DROP TABLE", "", "   ", "9".repeat(500)}) {
            enviar(c, corpoEnvio(v.id(), "\"numero\":\"" + ruim + "\",\"confirmoAceite\":true"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Número inválido. Use DDD e número, ex.: 11 91234-5678."));
        }
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void numeroInternacionalComMaisEhNormalizado() throws Exception {
        Conta c = contaConectada("wa-intl");
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"+351 912 345 678\",\"confirmoAceite\":true")).andExpect(status().isOk());
        var req = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*"))).get(0);
        assertEquals("351912345678", json.readTree(req.getBodyAsString()).get("number").asText());
    }

    @Test
    void clienteComAceiteRecebeComSaudacao() throws Exception {
        Conta c = contaConectada("wa-cliente");
        VendaCaixaResponseDTO v = vender(c);
        Cliente cli = cliente(c, "Maria Silva", "(11) 98888-7777", true, false);
        enviar(c, corpoEnvio(v.id(), "\"clienteId\":\"" + cli.getId() + "\"")).andExpect(status().isOk());
        var req = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*"))).get(0);
        var n = json.readTree(req.getBodyAsString());
        assertEquals("5511988887777", n.get("number").asText());
        assertEquals("Olá, Maria Silva! Segue o recibo nº " + v.numero() + ".", n.get("caption").asText());
        assertEquals("document", n.get("mediatype").asText());
    }

    @Test
    void clienteSemAceiteBloqueia() throws Exception {
        Conta c = contaConectada("wa-semaceite-cli");
        VendaCaixaResponseDTO v = vender(c);
        Cliente cli = cliente(c, "João", "11 97777-6666", false, false);
        enviar(c, corpoEnvio(v.id(), "\"clienteId\":\"" + cli.getId() + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este cliente ainda não aceitou receber pelo WhatsApp. Marque no cadastro depois de combinar com ele."));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void recusaPorNumeroVenceNumeroDigitadoEmOutroFormato() throws Exception {
        Conta c = contaConectada("wa-recusa");
        VendaCaixaResponseDTO v = vender(c);
        cliente(c, "Ana", "(11) 95555-4444", false, true);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"+55 11 95555 4444\",\"confirmoAceite\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Esta pessoa pediu para parar de receber."));
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11955554444\",\"confirmoAceite\":true"))
                .andExpect(status().isBadRequest());
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void documentoDeOutraContaEInexistenteTemMesmaResposta404() throws Exception {
        Conta a = contaConectada("wa-idor-a");
        Conta b = registrar("wa-idor-b");
        VendaCaixaResponseDTO deB = vender(b);
        String corpoOutra = corpoEnvio(deB.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true");
        String corpoInexistente = corpoEnvio(UUID.randomUUID(), "\"numero\":\"11912345678\",\"confirmoAceite\":true");
        String m1 = enviar(a, corpoOutra).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String m2 = enviar(a, corpoInexistente).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertTrue(m1.contains("Documento não encontrado."));
        assertTrue(m2.contains("Documento não encontrado."));
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void limiteDiarioBloqueiaOQuartoEnvioEFalhaNaoConsome() throws Exception {
        Conta c = contaConectada("wa-limite");
        VendaCaixaResponseDTO v = vender(c);
        String corpo = corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true");
        // uma falha do serviço antes: não consome o limite
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/message/sendMedia/.*"))
                .willReturn(aResponse().withStatus(500).withBody("{\"segredo\":\"" + CHAVE_FALSA + "\"}")));
        String falha = enviar(c, corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Não foi possível enviar: o serviço está indisponível, tente em instantes."))
                .andReturn().getResponse().getContentAsString();
        assertFalse(falha.contains(CHAVE_FALSA));
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/message/sendMedia/.*"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));
        for (int i = 1; i <= 3; i++) {
            enviar(c, corpo).andExpect(status().isOk()).andExpect(jsonPath("$.enviadosHoje").value(i));
        }
        enviar(c, corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você chegou ao limite de envios de hoje."));
        var hist = envios.findByUsuarioIdOrderByEnviadoEmDesc(c.usuario().getId(), org.springframework.data.domain.PageRequest.of(0, 20));
        assertEquals(3, hist.stream().filter(e -> e.getResultado() == ResultadoEnvio.ENVIADO).count());
        assertEquals(1, hist.stream().filter(e -> e.getResultado() == ResultadoEnvio.FALHOU).count());
        assertEquals(1, hist.stream().filter(e -> e.getResultado() == ResultadoEnvio.BLOQUEADO).count());
    }

    @Test
    void numeroSemWhatsappTemMensagemPropria() throws Exception {
        Conta c = contaConectada("wa-semwhats");
        VendaCaixaResponseDTO v = vender(c);
        FALSO.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/message/sendMedia/.*"))
                .willReturn(aResponse().withStatus(400).withBody("{\"response\":{\"message\":[{\"exists\":false}]}}")));
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Não foi possível enviar: este número não parece ter WhatsApp."));
    }

    @Test
    void envioSemConexaoRetornaDesconectado() throws Exception {
        Conta c = registrar("wa-semconexao");
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Não foi possível enviar: seu WhatsApp está desconectado."));
    }

    @Test
    void legendaAcimaDe1024Retorna400() throws Exception {
        Conta c = contaConectada("wa-legenda");
        VendaCaixaResponseDTO v = vender(c);
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true,\"legenda\":\"" + "a".repeat(1025) + "\""))
                .andExpect(status().isBadRequest());
        enviar(c, corpoEnvio(v.id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true,\"legenda\":\"Oi, tudo bem?\""))
                .andExpect(status().isOk());
        var req = FALSO.findAll(postRequestedFor(urlPathMatching("/message/sendMedia/.*"))).get(0);
        assertEquals("Oi, tudo bem?", json.readTree(req.getBodyAsString()).get("caption").asText());
    }

    @Test
    void entradaHostilaNuncaGera500() throws Exception {
        Conta c = contaConectada("wa-hostil");
        VendaCaixaResponseDTO v = vender(c);
        String[] corpos = {
                "", "{", "[]", "null", "{}", "\"texto\"",
                "{\"tipoDocumento\":\"INEXISTENTE\",\"documentoId\":\"" + v.id() + "\"}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"nao-e-uuid\"}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":null}",
                "{\"tipoDocumento\":null,\"documentoId\":\"" + v.id() + "\"}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + v.id() + "\",\"numero\":1e999}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + v.id() + "\",\"numero\":{\"a\":1}}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + v.id() + "\",\"confirmoAceite\":\"talvez\"}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + v.id() + "\",\"clienteId\":\"xx\"}",
                "{\"tipoDocumento\":\"RECIBO_CAIXA\",\"documentoId\":\"" + v.id() + "\",\"clienteId\":\"" + UUID.randomUUID() + "\"}",
        };
        for (String corpo : corpos) {
            MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/envios")
                    .header("Authorization", auth(c)).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
            int s = r.getResponse().getStatus();
            assertTrue(s == 400 || s == 404, "corpo [" + corpo + "] devolveu " + s);
        }
        MvcResult tipo = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/envios")
                .header("Authorization", auth(c)).contentType(MediaType.TEXT_PLAIN).content("x")).andReturn();
        assertEquals(415, tipo.getResponse().getStatus());
        FALSO.verify(0, postRequestedFor(urlPathMatching("/message/sendMedia/.*")));
    }

    @Test
    void semTokenNaoAcessa() throws Exception {
        int s1 = mvc.perform(get("/whatsapp/status")).andReturn().getResponse().getStatus();
        int s2 = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/whatsapp/envios")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
        assertTrue(s1 == 401 || s1 == 403);
        assertTrue(s2 == 401 || s2 == 403);
    }

    @Test
    void historicoENaoMostraEnviosDeOutraConta() throws Exception {
        Conta a = contaConectada("wa-hist-a");
        Conta b = contaConectada("wa-hist-b");
        enviar(a, corpoEnvio(vender(a).id(), "\"numero\":\"11912345678\",\"confirmoAceite\":true")).andExpect(status().isOk());
        mvc.perform(get("/whatsapp/envios").header("Authorization", auth(a))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/whatsapp/envios").header("Authorization", auth(b))).andExpect(jsonPath("$.length()").value(0));
    }

    /** Verificação da legenda padrão de venda do Caixa sem nome (número digitado: sem saudação). */
    static final class JsonNodeAssert {
        static void verifica(com.fasterxml.jackson.databind.JsonNode n, Integer numero) {
            assertEquals("5511912345678", n.get("number").asText());
            assertEquals("Segue o recibo nº " + numero + ".", n.get("caption").asText());
            assertEquals("application/pdf", n.get("mimetype").asText());
            assertTrue(n.get("fileName").asText().endsWith(".pdf"));
            assertFalse(n.get("media").asText().isBlank());
        }
    }
}
