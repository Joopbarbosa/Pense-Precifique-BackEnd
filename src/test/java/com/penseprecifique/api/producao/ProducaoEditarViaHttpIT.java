package com.penseprecifique.api.producao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoProdutoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-B012 (V0.8.3) — reforço de cobertura para o achado do Grupo B (P-F001c / P-B010 / P-B011).
 * {@link ProducaoEditarMantendoProdutoExistenteIT} (P-B010) chama {@code producaoService.editarProducao()}
 * diretamente — não passa pelo ciclo HTTP completo (filtros de segurança do Spring Security,
 * (de)serialização JSON via Jackson, {@code DispatcherServlet}). Este teste bate em
 * {@code POST}/{@code PUT /producoes} via {@link MockMvc}, caminho mais próximo do {@code curl}
 * real usado em {@code P-F001c}/{@code P-B011} do que a chamada direta ao Service.
 *
 * <p>{@code P-B011} (investigação dedicada) não conseguiu reproduzir o 500 original mesmo sob
 * carga pesada (suíte e2e completa) e repetição (10x em loop) — veredito registrado em
 * {@code DECISOES_V0.8.3.md} como "não reproduzido", não "confirmado resolvido". Este teste não
 * tem o objetivo de reabrir aquela investigação de causa raiz: cobre o mesmo cenário do teste de
 * {@code P-B010} ({@code [A,B,C]→[A,B]}) e acrescenta o cenário específico da reprodução original
 * de {@code P-F001c} que não tinha teste dedicado — produto único mantido
 * ({@code [A]→[A]}, só quantidade/data alteradas).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProducaoEditarViaHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private Usuario usuario;
    private Insumo insumo;

    private String seedUsuarioEToken() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-edit-http-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        // Mesmo padrão de seed de ProducaoEditarMantendoProdutoExistenteIT (P-B010): insumo
        // fracionável, estoque alto, permitirEstoqueNegativo=true — evita qualquer alerta/bloqueio
        // de estoque atrapalhar o teste, já que o que está sob teste é a ordem de flush do
        // delete+insert de ProducaoProduto via HTTP real, não regra de estoque.
        insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Insumo Base HTTP").marca("X").unidadeMedida("un")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true)
                .fracionavel(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    private UUID criarProduto(String nome, int numero) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(BigDecimal.ONE).build());
        return produto.getId();
    }

    private ProducaoDetalheResponse perform(String verbo, String uri, String token, String body) throws Exception {
        MvcResult result = mockMvc.perform((verbo.equals("POST") ? post(uri) : put(uri))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(verbo.equals("POST") ? status().isCreated() : status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ProducaoDetalheResponse.class);
    }

    private Map<UUID, BigDecimal> quantidadesPorProduto(ProducaoDetalheResponse detalhe) {
        return detalhe.getProdutos().stream()
                .collect(Collectors.toMap(ProducaoProdutoResponse::getProdutoId, ProducaoProdutoResponse::getQuantidade));
    }

    @Test
    void editarViaHttpMantendoDoisProdutosERemovendoUmNaoLanca500() throws Exception {
        String token = seedUsuarioEToken();
        UUID produtoAId = criarProduto("Produto A HTTP", 1);
        UUID produtoBId = criarProduto("Produto B HTTP", 2);
        UUID produtoCId = criarProduto("Produto C HTTP", 3);

        String bodyCriar = String.format("""
                {
                  "dataTerminoPrevista": "2026-12-31",
                  "produtos": [
                    {"produtoId": "%s", "quantidade": 1},
                    {"produtoId": "%s", "quantidade": 1},
                    {"produtoId": "%s", "quantidade": 1}
                  ]
                }
                """, produtoAId, produtoBId, produtoCId);
        ProducaoDetalheResponse criada = perform("POST", "/producoes", token, bodyCriar);

        // [A, B, C] -> [A, B]: mantém A e B (mesmo produtoId já persistido), remove C — mesmo
        // cenário coberto por ProducaoEditarMantendoProdutoExistenteIT (P-B010), agora via HTTP real.
        String bodyEditar = String.format("""
                {
                  "dataTerminoPrevista": "2027-01-15",
                  "produtos": [
                    {"produtoId": "%s", "quantidade": 2},
                    {"produtoId": "%s", "quantidade": 3}
                  ]
                }
                """, produtoAId, produtoBId);
        ProducaoDetalheResponse editada = perform("PUT", "/producoes/" + criada.getId(), token, bodyEditar);

        Map<UUID, BigDecimal> quantidades = quantidadesPorProduto(editada);
        assertEquals(Map.of(produtoAId, new BigDecimal("2"), produtoBId, new BigDecimal("3")), quantidades);
    }

    @Test
    void editarViaHttpMantendoProdutoUnicoNaoLanca500() throws Exception {
        String token = seedUsuarioEToken();
        UUID produtoId = criarProduto("Produto Unico HTTP", 1);

        String bodyCriar = String.format("""
                {
                  "dataTerminoPrevista": "2026-12-31",
                  "produtos": [
                    {"produtoId": "%s", "quantidade": 3}
                  ]
                }
                """, produtoId);
        ProducaoDetalheResponse criada = perform("POST", "/producoes", token, bodyCriar);

        // [A] -> [A]: mantém o ÚNICO produto já persistido, só quantidade/data mudam — cenário
        // exato da reprodução original de P-F001c (500/ConstraintViolationException em
        // uq_producao_produto), não coberto pelo teste de P-B010 (que sempre editava mantendo 2+
        // produtos simultaneamente).
        String bodyEditar = String.format("""
                {
                  "dataTerminoPrevista": "2027-01-15",
                  "produtos": [
                    {"produtoId": "%s", "quantidade": 10}
                  ]
                }
                """, produtoId);
        ProducaoDetalheResponse editada = perform("PUT", "/producoes/" + criada.getId(), token, bodyEditar);

        assertEquals(Map.of(produtoId, new BigDecimal("10")), quantidadesPorProduto(editada));
        assertEquals(java.time.LocalDate.of(2027, 1, 15), editada.getDataTerminoPrevista());
    }
}
