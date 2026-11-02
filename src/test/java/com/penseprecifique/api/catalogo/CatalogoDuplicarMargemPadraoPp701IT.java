package com.penseprecifique.api.catalogo;

import tools.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.infra.storage.R2StorageClient;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PP-701 (V0.19.0) — CAT-008: duplicar catálogo copia descrição e foto dos itens, mas NÃO a marca;
 * CAT-003: margem vazia usa a margem padrão da empresa (criar, editar e preview); entrada hostil na margem (400, nunca 500).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogoDuplicarMargemPadraoPp701IT {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired com.penseprecifique.api.empresa.ConfiguracaoPrecificacaoRepository configuracaoRepository;
    @MockitoBean R2StorageClient r2;

    private Usuario usuario;
    private String token;
    private Catalogo catalogo;
    private Insumo insumo;

    @BeforeEach
    void preparar() {
        usuario = usuarioRepository.save(Usuario.builder().email("pp701-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        token = jwtTokenProvider.generateToken(usuario);
        catalogo = catalogoRepository.save(Catalogo.builder().usuario(usuario).numero(1).nome("Natal " + UUID.randomUUID()).build());
        UnidadeMedida un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("un").sigla("un").build());
        insumo = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Insumo").unidadeMedida(un)
                .custoUnitario(new BigDecimal("10.0000")).estoqueAtual(BigDecimal.TEN).fracionavel(true).permitirEstoqueNegativo(true).build());
        configuracaoRepository.save(ConfiguracaoPrecificacao.builder().usuarioId(usuario.getId())
                .valorHora(BigDecimal.ZERO).margemPadrao(new BigDecimal("50.00")).build());
    }

    private String corpoItem(String nome, String margemJson) {
        return "{\"nome\":\"" + nome + "\",\"tempoProducao\":0,\"margemLucro\":" + margemJson
                + ",\"componentes\":[{\"insumoId\":\"" + insumo.getId() + "\",\"quantidade\":1}]}";
    }

    private MvcResult postItem(String corpo) throws Exception {
        return mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/itens").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private Map<?, ?> json(MvcResult r) throws Exception {
        return objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    // ---------------- CAT-008 ----------------

    @Test
    void duplicarCopiaDescricaoEFotoMasNaoAMarca() throws Exception {
        ItemCatalogo original = itemRepository.save(ItemCatalogo.builder().catalogo(catalogo).nome("Caneca").marca("Acme")
                .descricao("Caneca de porcelana").fotoUrl("http://r2/catalogo/item-catalogo/a/foto.jpg")
                .tempoProducao(0).margemLucro(new BigDecimal("20.00")).precoVenda(new BigDecimal("12.00")).build());
        when(r2.copiarPorUrl(eq(original.getFotoUrl()), anyString())).thenReturn("http://r2/catalogo/item-catalogo/b/copia.jpg");

        MvcResult r = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/duplicar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertEquals(201, r.getResponse().getStatus());
        UUID copiaId = UUID.fromString((String) json(r).get("id"));
        List<ItemCatalogo> itens = itemRepository.findByCatalogoIdAndDeletedAtIsNull(copiaId);
        assertEquals(1, itens.size());
        ItemCatalogo copia = itens.get(0);
        assertEquals("Caneca de porcelana", copia.getDescricao());
        assertEquals("http://r2/catalogo/item-catalogo/b/copia.jpg", copia.getFotoUrl());
        assertNotEquals(original.getFotoUrl(), copia.getFotoUrl(), "a cópia tem objeto próprio no R2");
        assertNull(copia.getMarca(), "a marca não é copiada");
        assertEquals(0, new BigDecimal("12.00").compareTo(copia.getPrecoVenda()));
        assertEquals("Acme", itemRepository.findById(original.getId()).orElseThrow().getMarca(), "o original não muda");
    }

    @Test
    void duplicarSemFotoNemDescricaoNaoChamaR2() throws Exception {
        itemRepository.save(ItemCatalogo.builder().catalogo(catalogo).nome("Simples").tempoProducao(0).precoVenda(new BigDecimal("5.00")).build());
        MvcResult r = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/duplicar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertEquals(201, r.getResponse().getStatus());
        ItemCatalogo copia = itemRepository.findByCatalogoIdAndDeletedAtIsNull(UUID.fromString((String) json(r).get("id"))).get(0);
        assertNull(copia.getFotoUrl());
        assertNull(copia.getDescricao());
        org.mockito.Mockito.verifyNoInteractions(r2);
    }

    @Test
    void falhaNaCopiaDaFotoDuplicaSemFotoSemQuebrar() throws Exception {
        itemRepository.save(ItemCatalogo.builder().catalogo(catalogo).nome("Com foto").descricao("d").fotoUrl("http://r2/x.png")
                .tempoProducao(0).precoVenda(new BigDecimal("5.00")).build());
        when(r2.copiarPorUrl(anyString(), anyString())).thenReturn(null);
        MvcResult r = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/duplicar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertEquals(201, r.getResponse().getStatus());
        ItemCatalogo copia = itemRepository.findByCatalogoIdAndDeletedAtIsNull(UUID.fromString((String) json(r).get("id"))).get(0);
        assertNull(copia.getFotoUrl());
        assertEquals("d", copia.getDescricao());
    }

    // ---------------- CAT-003 ----------------

    @Test
    void margemVaziaNaCriacaoUsaAMargemPadraoDaEmpresa() throws Exception {
        MvcResult r = postItem(corpoItem("Sem margem", "null"));
        assertEquals(201, r.getResponse().getStatus());
        ItemCatalogo item = itemRepository.findById(UUID.fromString((String) json(r).get("id"))).orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(item.getMargemLucro()));
        assertEquals(0, new BigDecimal("15.00").compareTo(item.getPrecoVenda())); // 10,00 x 1,50
    }

    @Test
    void margemTextoVazioContaComoVaziaEUsaAMargemPadrao() throws Exception {
        MvcResult r = postItem(corpoItem("Texto vazio", "\"\""));
        assertEquals(201, r.getResponse().getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(itemRepository.findById(UUID.fromString((String) json(r).get("id"))).orElseThrow().getMargemLucro()));
    }

    @Test
    void margemInformadaZeroNaoViraMargemPadrao() throws Exception {
        MvcResult r = postItem(corpoItem("Margem zero", "0"));
        assertEquals(201, r.getResponse().getStatus());
        ItemCatalogo item = itemRepository.findById(UUID.fromString((String) json(r).get("id"))).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getMargemLucro()));
        assertEquals(0, new BigDecimal("10.00").compareTo(item.getPrecoVenda()));
    }

    @Test
    void margemVaziaNaEdicaoEPreviewUsaAMargemPadrao() throws Exception {
        MvcResult criado = postItem(corpoItem("Editar", "20"));
        String id = (String) json(criado).get("id");

        MvcResult ed = mockMvc.perform(put("/catalogos/" + catalogo.getId() + "/itens/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpoItem("Editar", "null"))).andReturn();
        assertEquals(200, ed.getResponse().getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(itemRepository.findById(UUID.fromString(id)).orElseThrow().getMargemLucro()));

        MvcResult pv = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/itens/preview-preco").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tempoProducao\":0,\"componentes\":[{\"insumoId\":\"" + insumo.getId() + "\",\"quantidade\":1}]}")).andReturn();
        assertEquals(200, pv.getResponse().getStatus());
        assertEquals(0, new BigDecimal("15.00").compareTo(new BigDecimal(json(pv).get("precoSugerido").toString())));
    }

    // ---------------- Entrada hostil ----------------

    @Test
    void margemHostilNuncaRetorna500() throws Exception {
        String[] hostis = {"-1", "-0.01", "1000", "999.991", "12.345", "1e400", "99999999999999999999999999", "\"abc\"", "true", "[]", "{}"};
        for (String h : hostis) {
            int s = postItem(corpoItem("Hostil", h)).getResponse().getStatus();
            assertEquals(400, s, "criar com margem " + h);
            MvcResult pv = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/itens/preview-preco").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tempoProducao\":0,\"margemLucro\":" + h + ",\"componentes\":[{\"insumoId\":\"" + insumo.getId() + "\",\"quantidade\":1}]}")).andReturn();
            assertEquals(400, pv.getResponse().getStatus(), "preview com margem " + h);
        }
    }

    @Test
    void margemNosLimitesValidosEAceita() throws Exception {
        assertEquals(201, postItem(corpoItem("Limite 0", "0.00")).getResponse().getStatus());
        assertEquals(201, postItem(corpoItem("Limite max", "999.99")).getResponse().getStatus());
    }

    @Test
    void corpoMalformadoOuTipoErradoNaoRetorna500() throws Exception {
        for (String corpo : new String[]{"{", "nao e json", "[]", "null", ""}) {
            int s = postItem(corpo).getResponse().getStatus();
            assertTrue(s >= 400 && s < 500, "corpo '" + corpo + "' -> " + s);
        }
        int s = mockMvc.perform(post("/catalogos/" + catalogo.getId() + "/itens").header("Authorization", "Bearer " + token)
                .contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus();
        assertEquals(415, s);
    }
}
