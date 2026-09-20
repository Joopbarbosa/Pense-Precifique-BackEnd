package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoPreviewRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoPrecoSugeridoResponse;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAT-013 — POST /catalogos/{catalogoId}/itens/preview-preco. V0.13.0 (#516, RN-NOVA-1/2/3):
 * Item de Catálogo passou de "herda produto.precoVenda × quantidadePacote" (CAT-003) para
 * composição livre de N componentes (Insumo XOR Produto-base) com custo/margem/mão de obra
 * próprios, mesmo cálculo de {@code ItemCatalogoService#calcularCustoTotal}/
 * {@code calcularPrecoSugerido}, exposto sem persistir nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ItemCatalogoPreviewPrecoIT {

    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private int proximoNumeroProduto = 1;
    private int proximoNumeroInsumo = 1;

    private Usuario seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("preview-preco-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        return usuario;
    }

    private Produto novoProdutoBase(Usuario usuario, String nome, BigDecimal precoCusto) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumeroProduto++).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(0).precoCusto(precoCusto).precoVenda(new BigDecimal("10.00")).build());
    }

    private Insumo novoInsumo(Usuario usuario, String nome, BigDecimal custoUnitario) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(proximoNumeroInsumo++).nome(nome).unidadeMedida("un")
                .custoUnitario(custoUnitario).estoqueAtual(BigDecimal.TEN).fracionavel(true)
                .permitirEstoqueNegativo(true).build());
    }

    private UUID novoCatalogo(String nome) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        CatalogoResponse response = catalogoService.cadastrar(request);
        return response.getId();
    }

    private ItemCatalogoComponenteRequest componenteProdutoBase(UUID produtoBaseId, BigDecimal quantidade) {
        ItemCatalogoComponenteRequest req = new ItemCatalogoComponenteRequest();
        req.setProdutoBaseId(produtoBaseId);
        req.setQuantidade(quantidade);
        return req;
    }

    private ItemCatalogoComponenteRequest componenteInsumo(UUID insumoId, BigDecimal quantidade) {
        ItemCatalogoComponenteRequest req = new ItemCatalogoComponenteRequest();
        req.setInsumoId(insumoId);
        req.setQuantidade(quantidade);
        return req;
    }

    @Test
    void previewSemMaoDeObraNemMargem() {
        Usuario usuario = seedUsuario();
        Produto base = novoProdutoBase(usuario, "Sabonete", new BigDecimal("2.5000"));
        UUID catalogoId = novoCatalogo("Catálogo Kits");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setComponentes(List.of(componenteProdutoBase(base.getId(), new BigDecimal("3"))));
        request.setTempoProducao(0);

        ItemCatalogoPrecoSugeridoResponse response = itemCatalogoService.previewPreco(catalogoId, request);

        // custoComponentes = 2.50 x 3 = 7.50; sem valorHora configurado (default 0), custoMaoDeObra = 0
        assertEquals(0, new BigDecimal("7.50").compareTo(response.getCustoComponentes()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getCustoMaoDeObra()));
        assertEquals(0, new BigDecimal("7.50").compareTo(response.getCustoTotal()));
        // sem margem informada, precoSugerido = custoTotal
        assertEquals(0, new BigDecimal("7.50").setScale(2).compareTo(response.getPrecoSugerido()));
    }

    @Test
    void previewComComponenteInsumoEMargem() {
        Usuario usuario = seedUsuario();
        Produto base = novoProdutoBase(usuario, "Sabonete", new BigDecimal("2.5000"));
        Insumo fita = novoInsumo(usuario, "Fita", new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("Catálogo Kits");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setComponentes(List.of(
                componenteProdutoBase(base.getId(), new BigDecimal("2")),
                componenteInsumo(fita.getId(), new BigDecimal("3"))));
        request.setTempoProducao(0);
        request.setMargemLucro(new BigDecimal("20"));

        ItemCatalogoPrecoSugeridoResponse response = itemCatalogoService.previewPreco(catalogoId, request);

        // custoComponentes = (2.50 x 2) + (1.00 x 3) = 5.00 + 3.00 = 8.00
        assertEquals(0, new BigDecimal("8.00").compareTo(response.getCustoComponentes()));
        // precoSugerido = 8.00 x (1 + 20/100) = 9.60
        assertEquals(0, new BigDecimal("9.60").compareTo(response.getPrecoSugerido()));
    }

    @Test
    void previewNaoPersisteNadaNoBanco() {
        Usuario usuario = seedUsuario();
        Produto base = novoProdutoBase(usuario, "Sabonete", new BigDecimal("2.5000"));
        UUID catalogoId = novoCatalogo("Catálogo Kits");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setComponentes(List.of(componenteProdutoBase(base.getId(), BigDecimal.ONE)));
        request.setTempoProducao(0);

        itemCatalogoService.previewPreco(catalogoId, request);

        assertEquals(0, itemCatalogoService.listarPorCatalogo(catalogoId).size());
    }

    @Test
    void previewCatalogoInexistenteLancaResourceNotFound() {
        Usuario usuario = seedUsuario();
        Produto base = novoProdutoBase(usuario, "Sabonete", new BigDecimal("2.5000"));

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setComponentes(List.of(componenteProdutoBase(base.getId(), BigDecimal.ONE)));
        request.setTempoProducao(0);

        assertThrows(ResourceNotFoundException.class, () -> itemCatalogoService.previewPreco(UUID.randomUUID(), request));
    }
}
