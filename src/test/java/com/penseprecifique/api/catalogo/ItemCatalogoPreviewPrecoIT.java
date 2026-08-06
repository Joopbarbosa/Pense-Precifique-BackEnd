package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoPreviewRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoPrecoSugeridoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
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
 * RN-NOVA-8 — POST /catalogos/{catalogoId}/itens/preview-preco. Mesmo cálculo de RN-042
 * (ItemCatalogoService#calcularPrecoSugerido), exposto sem persistir nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ItemCatalogoPreviewPrecoIT {

    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private int proximoNumeroProduto = 1;

    private UUID seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("preview-preco-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        return usuario.getId();
    }

    private Produto novoProduto(Usuario usuario, String nome, TipoProduto tipo, BigDecimal custo, BigDecimal precoVenda) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumeroProduto++).nome(nome).tipo(tipo).tempoProducao(60)
                .precoCusto(custo).precoVenda(precoVenda).build());
    }

    private UUID novoCatalogo(String nome, String margem) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        request.setMargem(new BigDecimal(margem));
        CatalogoResponse response = catalogoService.cadastrar(request);
        return response.getId();
    }

    @Test
    void previewSemCustomizacoes() {
        UUID usuarioId = seedUsuario();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Produto produto = novoProduto(usuario, "Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"), new BigDecimal("10.00"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos", "50");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setProdutoId(produto.getId());
        request.setQuantidadePacote(10);

        ItemCatalogoPrecoSugeridoResponse response = itemCatalogoService.previewPreco(catalogoId, request);

        assertEquals(0, new BigDecimal("30.00").compareTo(response.getPrecoSugerido()));
    }

    @Test
    void previewComCustomizacaoAnexada() {
        UUID usuarioId = seedUsuario();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Produto produto = novoProduto(usuario, "Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"), new BigDecimal("10.00"));
        Produto customizacao = novoProduto(usuario, "Topo", TipoProduto.CUSTOMIZACAO, new BigDecimal("2.0000"), new BigDecimal("5.00"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos", "50");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setProdutoId(produto.getId());
        request.setQuantidadePacote(10);
        CustomizacaoAnexadaRequest customizacaoReq = new CustomizacaoAnexadaRequest();
        customizacaoReq.setProdutoId(customizacao.getId());
        customizacaoReq.setQuantidade(BigDecimal.ONE);
        request.setCustomizacoesAnexadas(List.of(customizacaoReq));

        ItemCatalogoPrecoSugeridoResponse response = itemCatalogoService.previewPreco(catalogoId, request);

        assertEquals(0, new BigDecimal("33.00").compareTo(response.getPrecoSugerido()));
    }

    @Test
    void previewNaoPersisteNadaNoBanco() {
        UUID usuarioId = seedUsuario();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Produto produto = novoProduto(usuario, "Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"), new BigDecimal("10.00"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos", "50");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setProdutoId(produto.getId());
        request.setQuantidadePacote(10);

        itemCatalogoService.previewPreco(catalogoId, request);

        assertEquals(0, itemCatalogoService.listarPorCatalogo(catalogoId).size());
    }

    @Test
    void previewProdutoSemCustoLancaErroRN044() {
        UUID usuarioId = seedUsuario();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Produto produtoSemCusto = novoProduto(usuario, "SemCusto", TipoProduto.PRODUTO, BigDecimal.ZERO, new BigDecimal("10.00"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos", "50");

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setProdutoId(produtoSemCusto.getId());
        request.setQuantidadePacote(10);

        assertThrows(BusinessException.class, () -> itemCatalogoService.previewPreco(catalogoId, request));
    }

    @Test
    void previewCatalogoInexistenteLancaResourceNotFound() {
        seedUsuario();

        ItemCatalogoPreviewRequest request = new ItemCatalogoPreviewRequest();
        request.setProdutoId(UUID.randomUUID());
        request.setQuantidadePacote(10);

        assertThrows(ResourceNotFoundException.class, () -> itemCatalogoService.previewPreco(UUID.randomUUID(), request));
    }
}
