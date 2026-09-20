package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.produto.CatalogoVinculadoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #237/PDT-013 — inativação de Produto passa a ser bloqueada se ele estiver vinculado como
 * componente de algum Item de Catálogo. V0.13.0 (#516, RN-NOVA-1) — item de Catálogo passou de "1
 * produto principal + customizações anexadas" para composição livre de N componentes; "produto
 * principal" e "customização anexada" viraram o mesmo tipo de vínculo (componente). Mudança
 * retroativa em cima de RN-045/CAT-006 (que continua existindo, bloqueando a *venda* de item já
 * inativado como segunda camada de proteção).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoBloqueioInativacaoCatalogoIT {

    @Autowired ProdutoService produtoService;
    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private int proximoNumero = 1;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-bloqueio-catalogo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(String nome, TipoProduto tipo, BigDecimal precoCusto) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumero++).nome(nome).tipo(tipo).tempoProducao(30)
                .ativo(true).precoCusto(precoCusto).precoVenda(new BigDecimal("10.00"))
                .build());
    }

    private UUID novoCatalogo(String nome) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        CatalogoResponse response = catalogoService.cadastrar(request);
        return response.getId();
    }

    private ItemCatalogoRequest itemComComponentes(String nome, UUID... produtoBaseIds) {
        ItemCatalogoRequest item = new ItemCatalogoRequest();
        item.setNome(nome);
        item.setTempoProducao(0);
        item.setComponentes(List.of(produtoBaseIds).stream().map(id -> {
            ItemCatalogoComponenteRequest req = new ItemCatalogoComponenteRequest();
            req.setProdutoBaseId(id);
            req.setQuantidade(BigDecimal.ONE);
            return req;
        }).toList());
        return item;
    }

    @Test
    void inativarSemVinculoEmCatalogoFuncionaNormalmente() {
        // PDT-CEN-037
        seedUsuario();
        Produto produto = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));

        produtoService.inativar(produto.getId());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertFalse(atualizado.getAtivo());
    }

    @Test
    void inativarComponenteDeItemCatalogoBloqueiaComListaDeCatalogos() {
        // PDT-CEN-038
        seedUsuario();
        Produto produto = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos");

        itemCatalogoService.adicionar(catalogoId, itemComComponentes("Kit Bolo", produto.getId()));

        BusinessException ex = assertThrows(BusinessException.class, () -> produtoService.inativar(produto.getId()));
        assertTrue(ex.getMessage().contains("Catálogo Bolos"), "mensagem deveria citar o catálogo vinculado: " + ex.getMessage());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertTrue(inalterado.getAtivo(), "não deve inativar quando bloqueado");
    }

    @Test
    void inativarProdutoUsadoComoSegundoComponenteTambemBloqueia() {
        // PDT-CEN-039 — cobre o segundo componente do mesmo item (antigo caso de "customização anexada")
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto segundo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos");

        itemCatalogoService.adicionar(catalogoId, itemComComponentes("Kit Bolo", principal.getId(), segundo.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> produtoService.inativar(segundo.getId()));
        assertTrue(ex.getMessage().contains("Catálogo Bolos"));

        Produto inalterado = produtoRepository.findById(segundo.getId()).orElseThrow();
        assertTrue(inalterado.getAtivo());
    }

    @Test
    void catalogosVinculadosListaAmbosOsComponentes() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto segundo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos");

        itemCatalogoService.adicionar(catalogoId, itemComComponentes("Kit Bolo", principal.getId(), segundo.getId()));

        List<CatalogoVinculadoResponse> vinculadosPrincipal = produtoService.catalogosVinculados(principal.getId());
        List<CatalogoVinculadoResponse> vinculadosSegundo = produtoService.catalogosVinculados(segundo.getId());

        assertEquals(1, vinculadosPrincipal.size());
        assertEquals("Catálogo Bolos", vinculadosPrincipal.get(0).getNome());
        assertEquals(1, vinculadosSegundo.size());
        assertEquals("Catálogo Bolos", vinculadosSegundo.get(0).getNome());
    }

    @Test
    void reativarContinuaFuncionandoSemAlteracao() {
        seedUsuario();
        Produto produto = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));

        produtoService.inativar(produto.getId());
        produtoService.reativar(produto.getId());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertTrue(atualizado.getAtivo());
    }

    @Test
    void inativarAposRemoverDoCatalogoPassaAFuncionar() {
        seedUsuario();
        Produto produto = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos");

        var itemResponse = itemCatalogoService.adicionar(catalogoId, itemComComponentes("Kit Bolo", produto.getId()));

        assertThrows(BusinessException.class, () -> produtoService.inativar(produto.getId()));

        itemCatalogoService.remover(itemResponse.getId());

        produtoService.inativar(produto.getId());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertFalse(atualizado.getAtivo());
    }
}
