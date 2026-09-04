package com.penseprecifique.api.producao;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoProdutoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P-B010 (V0.8.3, achado do Grupo B de P-F001c) — regressão do bug de 500 em
 * PUT /producoes/{id} quando a edição mantém pelo menos um produtoId já existente na produção.
 *
 * Causa raiz: {@code editarProducao()} chamava {@code producaoProdutoRepository.deleteAll(...)}
 * sem flush antes de regravar via {@code gravarProducaoProdutos()} (save() por produto). A
 * ActionQueue do Hibernate executa INSERTs antes de DELETEs no mesmo flush, então o INSERT do
 * produto mantido rodava antes do DELETE do registro antigo, violando {@code uq_producao_produto}
 * (UNIQUE(producao_id, produto_id), migration V35) com {@code ConstraintViolationException}.
 *
 * Cenário do prompt: produção [A, B, C] → editar para [A, B] (mantém A/B, remove C) e para
 * [A, B, D] (mantém A/B, remove C, adiciona D) — ambos são o caso comum de edição real e devem
 * funcionar sem 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoEditarMantendoProdutoExistenteIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private UUID produtoAId;
    private UUID produtoBId;
    private UUID produtoCId;
    private UUID produtoDId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-edit-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        // RN-077 — ficha técnica + rendimento válido são obrigatórios para um produto entrar em
        // produção (validarEResolverProdutos). Um único insumo fracionável, com estoque alto e
        // permitirEstoqueNegativo=true, evita qualquer alerta/bloqueio de estoque atrapalhar o teste
        // — o que está sob teste aqui é a ordem de flush do delete+insert de ProducaoProduto, não RN-064.
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Insumo Base").marca("X").unidadeMedida("un")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true)
                .fracionavel(true).build());

        produtoAId = criarProduto("Produto A", 1, insumo);
        produtoBId = criarProduto("Produto B", 2, insumo);
        produtoCId = criarProduto("Produto C", 3, insumo);
        produtoDId = criarProduto("Produto D", 4, insumo);
    }

    private UUID criarProduto(String nome, int numero, Insumo insumo) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(BigDecimal.ONE).build());
        return produto.getId();
    }

    private ProducaoProdutoRequest itemDe(UUID produtoId, String quantidade) {
        ProducaoProdutoRequest r = new ProducaoProdutoRequest();
        r.setProdutoId(produtoId);
        r.setQuantidade(new BigDecimal(quantidade));
        return r;
    }

    private CriarProducaoRequest requestCom(ProducaoProdutoRequest... itens) {
        CriarProducaoRequest request = new CriarProducaoRequest();
        request.setDataInicio(LocalDate.now());
        request.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        request.setObservacoes("Teste");
        request.setProdutos(List.of(itens));
        return request;
    }

    private Set<UUID> produtoIdsDe(ProducaoDetalheResponse detalhe) {
        return detalhe.getProdutos().stream().map(ProducaoProdutoResponse::getProdutoId).collect(Collectors.toSet());
    }

    @Test
    void editarMantendoDoisProdutosERemovendoUmNaoLanca500() {
        seed();
        ProducaoDetalheResponse criada = producaoService.criarProducao(
                requestCom(itemDe(produtoAId, "1"), itemDe(produtoBId, "1"), itemDe(produtoCId, "1")));

        // [A, B, C] -> [A, B]: mantém A e B (mesmo produtoId já persistido), remove C.
        ProducaoDetalheResponse editada = producaoService.editarProducao(criada.getId(),
                requestCom(itemDe(produtoAId, "2"), itemDe(produtoBId, "3")));

        assertEquals(Set.of(produtoAId, produtoBId), produtoIdsDe(editada));
    }

    @Test
    void editarMantendoDoisProdutosRemovendoUmEAdicionandoOutroNaoLanca500() {
        seed();
        ProducaoDetalheResponse criada = producaoService.criarProducao(
                requestCom(itemDe(produtoAId, "1"), itemDe(produtoBId, "1"), itemDe(produtoCId, "1")));

        // [A, B, C] -> [A, B, D]: mantém A e B, remove C, adiciona D.
        ProducaoDetalheResponse editada = producaoService.editarProducao(criada.getId(),
                requestCom(itemDe(produtoAId, "2"), itemDe(produtoBId, "3"), itemDe(produtoDId, "1")));

        assertEquals(Set.of(produtoAId, produtoBId, produtoDId), produtoIdsDe(editada));
    }
}
