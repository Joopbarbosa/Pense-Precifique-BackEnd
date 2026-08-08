package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.catalogo.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoVinculoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolverVinculosProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.SubstituicaoVinculoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #228/#237 — fluxo "inativar vinculados / substituir" ao inativar/excluir Produto: excluir() ganha a
 * mesma trava de vínculo já existente em inativar() (Catálogo + o novo vínculo de {@code produtoBase}
 * em ficha técnica de outro produto, RN-NOVA-1/#210), e POST /produtos/{id}/resolver-vinculos resolve
 * os 3 tipos de vínculo em massa antes de prosseguir com a operação original na mesma chamada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoResolverVinculosIT {

    @Autowired ProdutoService produtoService;
    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired ItemCatalogoCustomizacaoRepository itemCatalogoCustomizacaoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private int proximoNumero = 1;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-resolver-vinculos-" + UUID.randomUUID() + "@test.com")
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

    private UUID novoCatalogo(String nome, BigDecimal margem) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        request.setMargem(margem);
        CatalogoResponse response = catalogoService.cadastrar(request);
        return response.getId();
    }

    private ItemCatalogoResponse novoItem(UUID catalogoId, UUID produtoId) {
        ItemCatalogoRequest item = new ItemCatalogoRequest();
        item.setProdutoId(produtoId);
        item.setQuantidadePacote(1);
        return itemCatalogoService.adicionar(catalogoId, item);
    }

    private ItemCatalogoResponse novoItemComCustomizacao(UUID catalogoId, UUID produtoPrincipalId, UUID customizacaoProdutoId, BigDecimal quantidadeCustomizacao) {
        ItemCatalogoRequest item = new ItemCatalogoRequest();
        item.setProdutoId(produtoPrincipalId);
        item.setQuantidadePacote(1);
        CustomizacaoAnexadaRequest customizacaoReq = new CustomizacaoAnexadaRequest();
        customizacaoReq.setProdutoId(customizacaoProdutoId);
        customizacaoReq.setQuantidade(quantidadeCustomizacao);
        item.setCustomizacoesAnexadas(List.of(customizacaoReq));
        return itemCatalogoService.adicionar(catalogoId, item);
    }

    private FichaTecnicaItem novoComponente(Produto produtoPai, Produto produtoBase, BigDecimal quantidade) {
        return fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoPai).produtoBase(produtoBase).quantidade(quantidade).build());
    }

    // ---------------------------------------------------------------
    // excluir() — regressão + trava estendida
    // ---------------------------------------------------------------

    @Test
    void excluirSemVinculoFuncionaDireto() {
        seedUsuario();
        Produto produto = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));

        produtoService.excluir(produto.getId());

        assertTrue(produtoRepository.findById(produto.getId()).orElseThrow().getDeletedAt() != null);
    }

    @Test
    void excluirComVinculoEmCatalogoPrincipalSemResolverContinuaBloqueado() {
        seedUsuario();
        Produto produto = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        novoItem(catalogoId, produto.getId());

        BusinessException ex = assertThrows(BusinessException.class, () -> produtoService.excluir(produto.getId()));
        assertTrue(ex.getMessage().contains("CTG-1"));
        assertNull(produtoRepository.findById(produto.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void excluirComVinculoComoComponenteFichaTecnicaSemResolverContinuaBloqueado() {
        seedUsuario();
        Produto componente = novoProduto("Recheio", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        novoComponente(produtoPai, componente, new BigDecimal("2"));

        BusinessException ex = assertThrows(BusinessException.class, () -> produtoService.excluir(componente.getId()));
        assertTrue(ex.getMessage().contains("Bolo composto"), "mensagem deveria citar o produto pai: " + ex.getMessage());
        assertNull(produtoRepository.findById(componente.getId()).orElseThrow().getDeletedAt());
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — item de catálogo principal + componente de ficha técnica
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosInativarVinculadosRemoveItemESoftDeletaComponenteEProsseguiComInativar() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        novoComponente(produtoPai, alvo, new BigDecimal("2"));

        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setAcao(AcaoResolucaoVinculo.INATIVAR_VINCULADOS);
        request.setOperacao(OperacaoPosResolucaoVinculo.INATIVAR);
        produtoService.resolverVinculos(alvo.getId(), request);

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertTrue(itemAtualizado.getDeletedAt() != null, "item de catálogo deveria ter sido removido (soft-delete)");

        assertTrue(fichaTecnicaItemRepository.findByProdutoBaseId(alvo.getId()).isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));

        assertFalse(produtoRepository.findById(alvo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void resolverVinculosSubstituirCobrindoTudoAtualizaTudoEProsseguiComExcluir() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));

        Produto substitutoPrincipal = novoProduto("Substituto principal", TipoProduto.PRODUTO, new BigDecimal("4.0000"));
        Produto substitutoComponente = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        SubstituicaoVinculoProdutoRequest subItem = new SubstituicaoVinculoProdutoRequest();
        subItem.setTipo(TipoVinculoProduto.ITEM_CATALOGO_PRINCIPAL);
        subItem.setVinculoId(item.getId());
        subItem.setNovoProdutoId(substitutoPrincipal.getId());

        SubstituicaoVinculoProdutoRequest subComponente = new SubstituicaoVinculoProdutoRequest();
        subComponente.setTipo(TipoVinculoProduto.COMPONENTE_FICHA_TECNICA);
        subComponente.setVinculoId(componente.getId());
        subComponente.setNovoProdutoId(substitutoComponente.getId());

        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setAcao(AcaoResolucaoVinculo.SUBSTITUIR);
        request.setOperacao(OperacaoPosResolucaoVinculo.EXCLUIR);
        request.setSubstituicoes(List.of(subItem, subComponente));
        produtoService.resolverVinculos(alvo.getId(), request);

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertEquals(substitutoPrincipal.getId(), itemAtualizado.getProduto().getId());
        assertEquals(0, new BigDecimal("6.00").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o novo custo (sem override)");

        FichaTecnicaItem componenteAtualizado = fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow();
        assertEquals(substitutoComponente.getId(), componenteAtualizado.getProdutoBase().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }

    @Test
    void resolverVinculosSubstituirFaltandoCobrirLancaENaoAplicaNada() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));

        Produto substitutoPrincipal = novoProduto("Substituto principal", TipoProduto.PRODUTO, new BigDecimal("4.0000"));

        SubstituicaoVinculoProdutoRequest subItem = new SubstituicaoVinculoProdutoRequest();
        subItem.setTipo(TipoVinculoProduto.ITEM_CATALOGO_PRINCIPAL);
        subItem.setVinculoId(item.getId());
        subItem.setNovoProdutoId(substitutoPrincipal.getId());
        // falta a substituição do componente de ficha técnica

        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setAcao(AcaoResolucaoVinculo.SUBSTITUIR);
        request.setOperacao(OperacaoPosResolucaoVinculo.EXCLUIR);
        request.setSubstituicoes(List.of(subItem));

        assertThrows(BusinessException.class, () -> produtoService.resolverVinculos(alvo.getId(), request));

        assertEquals(alvo.getId(), itemCatalogoRepository.findById(item.getId()).orElseThrow().getProduto().getId());
        assertEquals(alvo.getId(), fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow().getProdutoBase().getId());
        assertNull(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt());
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — customização anexada
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosInativarVinculadosRemoveCustomizacaoAnexadaEProsseguiComInativar() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto alvo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("CTG-2", new BigDecimal("50"));
        novoItemComCustomizacao(catalogoId, principal.getId(), alvo.getId(), BigDecimal.ONE);

        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setAcao(AcaoResolucaoVinculo.INATIVAR_VINCULADOS);
        request.setOperacao(OperacaoPosResolucaoVinculo.INATIVAR);
        produtoService.resolverVinculos(alvo.getId(), request);

        assertTrue(itemCatalogoCustomizacaoRepository.findByProdutoId(alvo.getId()).isEmpty());
        assertFalse(produtoRepository.findById(alvo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void resolverVinculosSubstituirCustomizacaoAnexadaTrocaProdutoERecalculaPreco() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto alvo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        Produto substituto = novoProduto("Topo de bolo especial", TipoProduto.CUSTOMIZACAO, new BigDecimal("3.0000"));
        UUID catalogoId = novoCatalogo("CTG-2", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItemComCustomizacao(catalogoId, principal.getId(), alvo.getId(), BigDecimal.ONE);

        ItemCatalogoCustomizacao customizacao = itemCatalogoCustomizacaoRepository.findByProdutoId(alvo.getId()).get(0);

        SubstituicaoVinculoProdutoRequest sub = new SubstituicaoVinculoProdutoRequest();
        sub.setTipo(TipoVinculoProduto.CUSTOMIZACAO_ANEXADA);
        sub.setVinculoId(customizacao.getId());
        sub.setNovoProdutoId(substituto.getId());

        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setAcao(AcaoResolucaoVinculo.SUBSTITUIR);
        request.setOperacao(OperacaoPosResolucaoVinculo.EXCLUIR);
        request.setSubstituicoes(List.of(sub));
        produtoService.resolverVinculos(alvo.getId(), request);

        ItemCatalogoCustomizacao customizacaoAtualizada = itemCatalogoCustomizacaoRepository.findById(customizacao.getId()).orElseThrow();
        assertEquals(substituto.getId(), customizacaoAtualizada.getProduto().getId());

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("7.50").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o novo custo (sem override)");

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }
}
