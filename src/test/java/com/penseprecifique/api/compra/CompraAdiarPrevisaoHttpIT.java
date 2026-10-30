package com.penseprecifique.api.compra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1209) — PATCH /compras/{id}/previsao pela rota: teto de 31/12/2100, corpo malformado, tipo de conteúdo,
 * id inválido e compra de outra conta. Sempre 4xx (nunca 500) e a previsão da compra não muda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CompraAdiarPrevisaoHttpIT {

    private static final String SENHA = "senha-valida-123";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    private String token;
    private Compra compra;
    private LocalDate hoje;

    private String registrar(String email) throws Exception {
        String corpo = mvc.perform(MockMvcRequestBuilders.post("/auth/register").contentType(JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = mapper.readTree(corpo);
        return n.get("token").asText();
    }

    private Compra pendente(String email, int numero) {
        Usuario u = usuarioRepository.findByEmail(email).orElseThrow();
        Cliente f = clienteRepository.save(Cliente.builder().usuario(u).numero(1).nome("Fornecedor").ativa(true).build());
        return compraRepository.save(Compra.builder().usuario(u).numero(numero)
                .status(StatusCompra.PENDENTE_RECEBIMENTO).dataCompra(hoje.minusDays(1)).fornecedor(f)
                .previsaoEntrega(hoje.plusDays(2)).build());
    }

    @BeforeEach
    void preparar() throws Exception {
        hoje = LocalDate.now(clock);
        String email = "adiar-http-" + UUID.randomUUID() + "@teste.com";
        token = registrar(email);
        compra = pendente(email, 1);
    }

    private int patch(String caminho, String tipo, String corpo, String tokenUsado) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(HttpMethod.PATCH, caminho)
                .header("Authorization", "Bearer " + tokenUsado);
        if (tipo != null) b.contentType(tipo);
        if (corpo != null) b.content(corpo);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private void esperar4xx(List<String> falhas, String rotulo, int obtido) {
        if (obtido < 400 || obtido >= 500) falhas.add(rotulo + " → " + obtido);
    }

    private String corpoPrevisao(String data) {
        return "{\"previsaoEntrega\":\"" + data + "\"}";
    }

    @Test
    void previsaoAcimaDoTetoEhRecusadaComBadRequestESemAlterarACompra() throws Exception {
        String caminho = "/compras/" + compra.getId() + "/previsao";
        for (String data : List.of("2101-01-01", "2500-06-15", "9999-12-31")) {
            assertEquals(400, patch(caminho, JSON, corpoPrevisao(data), token), data);
        }
        assertEquals(hoje.plusDays(2), compraRepository.findById(compra.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void previsaoNoTetoEhAceita() throws Exception {
        assertEquals(200, patch("/compras/" + compra.getId() + "/previsao", JSON, corpoPrevisao("2100-12-31"), token));
        assertEquals(LocalDate.of(2100, 12, 31), compraRepository.findById(compra.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void corpoHostilNuncaDa500ESemAlterarACompra() throws Exception {
        String alvo = "/compras/" + compra.getId() + "/previsao";
        List<String> falhas = new ArrayList<>();
        esperar4xx(falhas, "sem corpo", patch(alvo, JSON, null, token));
        esperar4xx(falhas, "corpo vazio", patch(alvo, JSON, "", token));
        esperar4xx(falhas, "json quebrado", patch(alvo, JSON, "{\"previsaoEntrega\":", token));
        esperar4xx(falhas, "texto puro", patch(alvo, MediaType.TEXT_PLAIN_VALUE, "2026-12-01", token));
        esperar4xx(falhas, "xml", patch(alvo, MediaType.APPLICATION_XML_VALUE, "<a/>", token));
        esperar4xx(falhas, "sem content-type", patch(alvo, null, corpoPrevisao("2026-12-01"), token));
        esperar4xx(falhas, "data nula", patch(alvo, JSON, "{\"previsaoEntrega\":null}", token));
        esperar4xx(falhas, "campo ausente", patch(alvo, JSON, "{}", token));
        esperar4xx(falhas, "array no corpo", patch(alvo, JSON, "[]", token));
        esperar4xx(falhas, "data em formato brasileiro", patch(alvo, JSON, corpoPrevisao("31/12/2026"), token));
        esperar4xx(falhas, "data texto", patch(alvo, JSON, corpoPrevisao("amanhã"), token));
        esperar4xx(falhas, "data inexistente", patch(alvo, JSON, corpoPrevisao("2026-02-30"), token));
        esperar4xx(falhas, "data número", patch(alvo, JSON, "{\"previsaoEntrega\":20261201}", token));
        esperar4xx(falhas, "ano extremo", patch(alvo, JSON, corpoPrevisao("+999999999-12-31"), token));
        esperar4xx(falhas, "ano negativo extremo", patch(alvo, JSON, corpoPrevisao("-999999999-01-01"), token));
        esperar4xx(falhas, "data passada", patch(alvo, JSON, corpoPrevisao(hoje.minusDays(3).toString()), token));
        esperar4xx(falhas, "antes da data da compra", patch(alvo, JSON, corpoPrevisao("1999-01-01"), token));
        esperar4xx(falhas, "injeção", patch(alvo, JSON, corpoPrevisao("2026-12-01' OR 1=1--"), token));
        assertTrue(falhas.isEmpty(), "adiar previsão: " + falhas);
        assertEquals(hoje.plusDays(2), compraRepository.findById(compra.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void idInvalidoInexistenteOuDeOutraContaNuncaDa500() throws Exception {
        String outro = "adiar-http-outra-" + UUID.randomUUID() + "@teste.com";
        String tokenOutra = registrar(outro);
        List<String> falhas = new ArrayList<>();
        String corpo = corpoPrevisao(hoje.plusDays(10).toString());
        esperar4xx(falhas, "id inválido", patch("/compras/abc/previsao", JSON, corpo, token));
        esperar4xx(falhas, "id inexistente", patch("/compras/" + UUID.randomUUID() + "/previsao", JSON, corpo, token));
        esperar4xx(falhas, "injeção no id", patch("/compras/%27%20OR%201%3D1--/previsao", JSON, corpo, token));
        int deOutraConta = patch("/compras/" + compra.getId() + "/previsao", JSON, corpo, tokenOutra);
        assertEquals(404, deOutraConta);
        assertTrue(falhas.isEmpty(), "ids: " + falhas);
        assertEquals(hoje.plusDays(2), compraRepository.findById(compra.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void compraConfirmadaNaoAdiaESemTokenDa401() throws Exception {
        compra.setStatus(StatusCompra.CONFIRMADA);
        compraRepository.save(compra);
        int s = patch("/compras/" + compra.getId() + "/previsao", JSON, corpoPrevisao(hoje.plusDays(5).toString()), token);
        assertTrue(s >= 400 && s < 500, "confirmada → " + s);
        assertEquals(hoje.plusDays(2), compraRepository.findById(compra.getId()).orElseThrow().getPrevisaoEntrega());
        int semToken = mvc.perform(MockMvcRequestBuilders.patch("/compras/" + compra.getId() + "/previsao")
                .contentType(JSON).content(corpoPrevisao(hoje.plusDays(5).toString()))).andReturn().getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403, "sem token → " + semToken);
    }
}
