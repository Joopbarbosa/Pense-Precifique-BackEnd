package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.CustomizacaoAnexadaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #237 (correção V0.7) — CustomizacaoAnexadaResponse não expunha o {@code id} próprio da
 * linha ItemCatalogoCustomizacao, exigido como {@code vinculoId} por
 * POST /produtos/{id}/resolver-vinculos (ação SUBSTITUIR, tipo CUSTOMIZACAO_ANEXADA).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomizacaoAnexadaVinculoIdIT {

    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired ItemCatalogoCustomizacaoRepository itemCatalogoCustomizacaoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private int proximoNumeroProduto = 1;

    private Usuario seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("customizacao-vinculo-id-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        return usuario;
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
    void itemComCustomizacaoAnexadaExpoeIdDoVinculo() {
        Usuario usuario = seedUsuario();
        Produto principal = novoProduto(usuario, "Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"), new BigDecimal("10.00"));
        Produto customizacao = novoProduto(usuario, "Topo", TipoProduto.CUSTOMIZACAO, new BigDecimal("2.0000"), new BigDecimal("5.00"));
        UUID catalogoId = novoCatalogo("Catálogo Bolos", "50");

        ItemCatalogoRequest request = new ItemCatalogoRequest();
        request.setProdutoId(principal.getId());
        request.setQuantidadePacote(1);
        CustomizacaoAnexadaRequest customizacaoReq = new CustomizacaoAnexadaRequest();
        customizacaoReq.setProdutoId(customizacao.getId());
        customizacaoReq.setQuantidade(BigDecimal.ONE);
        request.setCustomizacoesAnexadas(List.of(customizacaoReq));

        ItemCatalogoResponse response = itemCatalogoService.adicionar(catalogoId, request);

        assertEquals(1, response.getCustomizacoesAnexadas().size());
        CustomizacaoAnexadaResponse customizacaoResponse = response.getCustomizacoesAnexadas().get(0);
        assertNotNull(customizacaoResponse.getId());

        UUID idPersistido = itemCatalogoCustomizacaoRepository.findByProdutoId(customizacao.getId()).get(0).getId();
        assertEquals(idPersistido, customizacaoResponse.getId());

        // Regressão: GET /catalogos/{catalogoId}/itens (listarPorCatalogo) continua expondo os mesmos dados.
        ItemCatalogoResponse listado = itemCatalogoService.listarPorCatalogo(catalogoId).get(0);
        assertEquals(idPersistido, listado.getCustomizacoesAnexadas().get(0).getId());
    }
}
