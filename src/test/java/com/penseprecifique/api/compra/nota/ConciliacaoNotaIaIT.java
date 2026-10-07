package com.penseprecifique.api.compra.nota;

import tools.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.16.0 (#681, RN-NOVA-12 passo 3, DT-NOVA-11) — sugestão de insumo por IA com o provedor simulado e o
 * limite mensal de 200: só itens sem ligação vão à IA, a resposta é validada, e ao atingir o limite a
 * sugestão é pulada com aviso informativo, sem chamar a IA; o mês seguinte começa do zero.
 */
class ConciliacaoNotaIaIT extends NotaServicosSimulados {

    private static final String CNPJ = "11222333000181";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger CHAVES = new AtomicInteger(500);

    @Autowired NotaCompraService notaCompraService;
    @Autowired SugestaoIaUso sugestaoIaUso;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numero = 1;

    @BeforeEach
    void seed() {
        LEITOR.resetAll();
        IA.resetAll();
        usuario = usuarioRepository.save(Usuario.builder()
                .email("conciliacao-ia-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private static List<String> nomesDosCampos(tools.jackson.databind.JsonNode no) {
        List<String> campos = new java.util.ArrayList<>();
        campos.addAll(no.propertyNames());
        return campos;
    }

    private Insumo insumoComMarca(String nome, String marca) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).marca(marca)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private static NotaLida.Item item(String nome) {
        return new NotaLida.Item(nome, BigDecimal.ONE, new BigDecimal("10.00"), null, null, "UN", null, null);
    }

    private NotaLeituraResponse ler(NotaLida.Item... itens) throws Exception {
        NotaLida nota = new NotaLida(new NotaLida.Emitente(CNPJ, "Papelaria Estrela", "SP"),
                String.format("35261011222333000181650010000%015d", CHAVES.getAndIncrement()), "11", "1",
                LocalDate.now().minusDays(1).toString(), new BigDecimal("10.00").multiply(BigDecimal.valueOf(itens.length)),
                null, null, List.of(itens), "NFCE_QR", "LEITOR_UF", false, "SP", "SP-1", List.of());
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(JSON.writeValueAsString(nota))));
        return notaCompraService.ler("NFCE", null, nota.chaveAcesso(), null, false);
    }

    private void iaResponde(List<Map<String, Object>> sugestoes) throws Exception {
        String conteudo = JSON.writeValueAsString(Map.of("sugestoes", sugestoes));
        String corpo = JSON.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", conteudo)))));
        IA.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    @Test
    void itemSemLigacaoRecebeSugestaoMarcadaComoIa() throws Exception {
        Insumo metalizada = insumo("Fita metalizada");
        iaResponde(List.of(Map.of("posicao", 0, "insumoId", metalizada.getId().toString(), "fator", 1)));

        NotaLeituraResponse.ItemConciliacao item = ler(item("FITA DOURADA 2CM")).itens().get(0);

        assertEquals(OrigemLigacao.SUGESTAO_IA, item.origemLigacao());
        assertEquals(metalizada.getId(), item.insumo().id());
        IA.verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer chave-ia-de-teste"))
                .withRequestBody(containing("FITA DOURADA 2CM"))
                .withRequestBody(containing("Fita metalizada")));
    }

    /** #724 (DT-NOVA-11): ao provedor vão só nomes (a marca vai dentro do nome do insumo), sem unidade nem campo de marca. */
    @Test
    void provedorRecebeSoNomesDeItensEDeInsumos() throws Exception {
        Insumo metalizada = insumoComMarca("Fita metalizada", "Importada");
        iaResponde(List.of(Map.of("posicao", 0, "insumoId", metalizada.getId().toString(), "fator", 1)));

        ler(item("FITA DOURADA 2CM"));

        String corpo = IA.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).get(0).getBodyAsString();
        var mensagens = JSON.readTree(corpo).path("messages");
        var dados = JSON.readTree(mensagens.get(1).path("content").asText());
        var itemEnviado = dados.path("itens").get(0);
        var insumoEnviado = dados.path("insumos").get(0);
        assertEquals(List.of("posicao", "nome"), nomesDosCampos(itemEnviado));
        assertEquals(List.of("id", "nome"), nomesDosCampos(insumoEnviado));
        assertEquals("FITA DOURADA 2CM", itemEnviado.path("nome").asText());
        assertEquals("Fita metalizada Importada", insumoEnviado.path("nome").asText());
    }

    @Test
    void sugestaoComInsumoForaDaListaOuFatorInvalidoEDescartada() throws Exception {
        insumo("Fita metalizada");
        Insumo cola = insumo("Cola quente");
        iaResponde(List.of(Map.of("posicao", 0, "insumoId", UUID.randomUUID().toString(), "fator", 1),
                Map.of("posicao", 1, "insumoId", cola.getId().toString(), "fator", 0)));

        List<NotaLeituraResponse.ItemConciliacao> itens = ler(item("FITA DOURADA 2CM"), item("BASTAO COLA 11MM")).itens();

        assertEquals(OrigemLigacao.SEM_LIGACAO, itens.get(0).origemLigacao());
        assertEquals(OrigemLigacao.SEM_LIGACAO, itens.get(1).origemLigacao());
        assertNull(itens.get(1).insumo());
    }

    @Test
    void itemLigadoPorNomeNaoVaiParaAIa() throws Exception {
        insumo("Caneta gel azul");
        iaResponde(List.of());

        NotaLeituraResponse leitura = ler(item("CANETA GEL AZUL"));

        assertEquals(OrigemLigacao.CASAMENTO_NOME, leitura.itens().get(0).origemLigacao());
        IA.verify(0, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void limiteMensalPulaASugestaoComAvisoSemChamarAIaEZeraNoMesSeguinte() throws Exception {
        insumo("Fita metalizada");
        iaResponde(List.of());

        assertEquals(198, sugestaoIaUso.reservar(usuario.getId(), 198), "198 das 200 sugestões do mês já usadas");

        NotaLeituraResponse primeira = ler(item("FITA DOURADA 2CM"), item("FITA PRATA 2CM"), item("FITA ROSA 2CM"));
        assertEquals(List.of(ConciliacaoNotaService.AVISO_LIMITE_IA), primeira.avisos(), "3 itens, restam 2: um fica sem sugestão");
        IA.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));

        NotaLeituraResponse segunda = ler(item("FITA AZUL 2CM"));
        assertEquals(List.of(ConciliacaoNotaService.AVISO_LIMITE_IA), segunda.avisos());
        assertEquals(OrigemLigacao.SEM_LIGACAO, segunda.itens().get(0).origemLigacao(), "a leitura segue, item sem ligação");
        IA.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));

        LocalDate mesQueVem = LocalDate.now().plusMonths(1);
        assertEquals(5, sugestaoIaUso.reservar(usuario.getId(), 5, mesQueVem), "no mês seguinte o contador recomeça");
        assertEquals(195, sugestaoIaUso.reservar(usuario.getId(), 300, mesQueVem), "e para de novo em 200");
    }

    @Test
    void falhaDaIaNaoBloqueiaALeitura() throws Exception {
        insumo("Fita metalizada");
        IA.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse().withStatus(500)));

        NotaLeituraResponse leitura = ler(item("FITA DOURADA 2CM"));

        assertEquals(OrigemLigacao.SEM_LIGACAO, leitura.itens().get(0).origemLigacao());
        assertTrue(leitura.avisos().isEmpty());
    }
}
