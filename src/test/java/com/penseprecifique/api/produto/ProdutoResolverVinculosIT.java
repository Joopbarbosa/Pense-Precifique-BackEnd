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
import com.penseprecifique.api.shared.dto.request.produto.ResolucaoVinculoCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolucaoVinculoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.produto.ResolverVinculosProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.SubstituicaoComponenteVinculoRequest;
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
 * #228/#237 — fluxo "remover vínculos / substituir" ao inativar/excluir Produto: excluir() ganha a
 * mesma trava de vínculo já existente em inativar() (Catálogo + o vínculo de {@code produtoBase} em
 * ficha técnica de outro produto, RN-NOVA-1/#210), e POST /produtos/{id}/resolver-vinculos resolve os
 * vínculos em massa antes de prosseguir com a operação original na mesma chamada.
 *
 * <p>V0.7 (correção de contrato, 2026-08-08): o body deixou de ter uma {@code acao} única aplicada a
 * todos os vínculos e passou a ter 2 blocos independentes — {@code catalogo} (item de catálogo
 * principal + customização anexada) e {@code componente} (produtoBase em ficha técnica de outro
 * produto) —, cada um com a própria {@code acao} ({@code REMOVER_VINCULOS}/{@code SUBSTITUIR},
 * renomeado de {@code INATIVAR_VINCULADOS}). Ambos os blocos são resolvidos numa única transação
 * (classe é {@code @Transactional}) — falha em qualquer bloco reverte os dois.</p>
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

    private ResolverVinculosProdutoRequest request(OperacaoPosResolucaoVinculo operacao,
                                                     ResolucaoVinculoCatalogoRequest catalogo,
                                                     ResolucaoVinculoComponenteRequest componente) {
        ResolverVinculosProdutoRequest request = new ResolverVinculosProdutoRequest();
        request.setOperacao(operacao);
        request.setCatalogo(catalogo);
        request.setComponente(componente);
        return request;
    }

    private ResolucaoVinculoCatalogoRequest removerCatalogo() {
        ResolucaoVinculoCatalogoRequest r = new ResolucaoVinculoCatalogoRequest();
        r.setAcao(AcaoResolucaoVinculo.REMOVER_VINCULOS);
        return r;
    }

    private ResolucaoVinculoCatalogoRequest substituirCatalogo(SubstituicaoVinculoProdutoRequest... subs) {
        ResolucaoVinculoCatalogoRequest r = new ResolucaoVinculoCatalogoRequest();
        r.setAcao(AcaoResolucaoVinculo.SUBSTITUIR);
        r.setSubstituicoes(List.of(subs));
        return r;
    }

    private ResolucaoVinculoComponenteRequest removerComponente() {
        ResolucaoVinculoComponenteRequest r = new ResolucaoVinculoComponenteRequest();
        r.setAcao(AcaoResolucaoVinculo.REMOVER_VINCULOS);
        return r;
    }

    private ResolucaoVinculoComponenteRequest substituirComponente(SubstituicaoComponenteVinculoRequest... subs) {
        ResolucaoVinculoComponenteRequest r = new ResolucaoVinculoComponenteRequest();
        r.setAcao(AcaoResolucaoVinculo.SUBSTITUIR);
        r.setSubstituicoes(List.of(subs));
        return r;
    }

    private SubstituicaoVinculoProdutoRequest subCatalogo(TipoVinculoProduto tipo, UUID vinculoId, UUID novoProdutoId) {
        SubstituicaoVinculoProdutoRequest sub = new SubstituicaoVinculoProdutoRequest();
        sub.setTipo(tipo);
        sub.setVinculoId(vinculoId);
        sub.setNovoProdutoId(novoProdutoId);
        return sub;
    }

    private SubstituicaoComponenteVinculoRequest subComponente(UUID vinculoId, UUID novoProdutoId) {
        SubstituicaoComponenteVinculoRequest sub = new SubstituicaoComponenteVinculoRequest();
        sub.setVinculoId(vinculoId);
        sub.setNovoProdutoId(novoProdutoId);
        return sub;
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
    // resolver-vinculos — só um dos 2 blocos (bloco do outro tipo omitido)
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosApenasBlocoCatalogoRemoverVinculosProsseguiComInativar() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.INATIVAR, removerCatalogo(), null));

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertTrue(itemAtualizado.getDeletedAt() != null, "item de catálogo deveria ter sido removido (soft-delete)");
        assertFalse(produtoRepository.findById(alvo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void resolverVinculosApenasBlocoComponenteSubstituirProsseguiComExcluir() {
        seedUsuario();
        Produto alvo = novoProduto("Recheio", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));
        Produto substituto = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR, null,
                        substituirComponente(subComponente(componente.getId(), substituto.getId()))));

        FichaTecnicaItem componenteAtualizado = fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow();
        assertEquals(substituto.getId(), componenteAtualizado.getProdutoBase().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));
        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — os 2 blocos presentes, com ações independentes
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosAmbosBlocosComAcoesDiferentesAplicaAmbosEProsseguiComExcluir() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogo1 = novoCatalogo("CTG-1", new BigDecimal("50"));
        UUID catalogo2 = novoCatalogo("CTG-2", new BigDecimal("50"));
        ItemCatalogoResponse item1 = novoItem(catalogo1, alvo.getId());
        ItemCatalogoResponse item2 = novoItem(catalogo2, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));
        Produto substitutoComponente = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        // catálogo: REMOVER_VINCULOS | componente: SUBSTITUIR — ações diferentes por bloco, mesma chamada.
        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR,
                        removerCatalogo(),
                        substituirComponente(subComponente(componente.getId(), substitutoComponente.getId()))));

        assertTrue(itemCatalogoRepository.findById(item1.getId()).orElseThrow().getDeletedAt() != null);
        assertTrue(itemCatalogoRepository.findById(item2.getId()).orElseThrow().getDeletedAt() != null);

        FichaTecnicaItem componenteAtualizado = fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow();
        assertEquals(substitutoComponente.getId(), componenteAtualizado.getProdutoBase().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }

    @Test
    void resolverVinculosSubstituirCatalogoEComponenteCobrindoTudoAtualizaTudoEProsseguiComExcluir() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));

        Produto substitutoPrincipal = novoProduto("Substituto principal", TipoProduto.PRODUTO, new BigDecimal("4.0000"));
        Produto substitutoComponente = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR,
                        substituirCatalogo(subCatalogo(TipoVinculoProduto.ITEM_CATALOGO_PRINCIPAL, item.getId(), substitutoPrincipal.getId())),
                        substituirComponente(subComponente(componente.getId(), substitutoComponente.getId()))));

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertEquals(substitutoPrincipal.getId(), itemAtualizado.getProduto().getId());
        assertEquals(0, new BigDecimal("6.00").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o novo custo (sem override)");

        FichaTecnicaItem componenteAtualizado = fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow();
        assertEquals(substitutoComponente.getId(), componenteAtualizado.getProdutoBase().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — atomicidade (bloco incompleto/ausente não aplica nada, nem o outro bloco)
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosBlocoComponenteIncompletoNaoAplicaNadaNemOBlocoCatalogo() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai1 = novoProduto("Bolo composto 1", TipoProduto.PRODUTO, BigDecimal.ZERO);
        Produto produtoPai2 = novoProduto("Bolo composto 2", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente1 = novoComponente(produtoPai1, alvo, new BigDecimal("2"));
        novoComponente(produtoPai2, alvo, new BigDecimal("1"));
        Produto substituto = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        // catálogo: REMOVER_VINCULOS (válido isoladamente) | componente: SUBSTITUIR cobrindo só 1 dos 2 vínculos.
        ResolverVinculosProdutoRequest request = request(OperacaoPosResolucaoVinculo.EXCLUIR,
                removerCatalogo(),
                substituirComponente(subComponente(componente1.getId(), substituto.getId())));

        assertThrows(BusinessException.class, () -> produtoService.resolverVinculos(alvo.getId(), request));

        // nada foi aplicado — nem o bloco catálogo, que seria válido isoladamente (atomicidade entre blocos)
        assertNull(itemCatalogoRepository.findById(item.getId()).orElseThrow().getDeletedAt());
        assertEquals(alvo.getId(), fichaTecnicaItemRepository.findById(componente1.getId()).orElseThrow().getProdutoBase().getId());
        assertNull(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void resolverVinculosBlocoCatalogoAusenteQuandoHaVinculoLancaENaoAplicaNada() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1", new BigDecimal("50"));
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));
        Produto substituto = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        // bloco "catalogo" ausente, mas o produto tem vínculo de catálogo pendente — deve falhar sem aplicar o bloco "componente".
        ResolverVinculosProdutoRequest request = request(OperacaoPosResolucaoVinculo.EXCLUIR,
                null,
                substituirComponente(subComponente(componente.getId(), substituto.getId())));

        assertThrows(BusinessException.class, () -> produtoService.resolverVinculos(alvo.getId(), request));

        assertNull(itemCatalogoRepository.findById(item.getId()).orElseThrow().getDeletedAt());
        assertEquals(alvo.getId(), fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow().getProdutoBase().getId());
        assertNull(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt());
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — customização anexada (bloco catálogo)
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosRemoverVinculosCustomizacaoAnexadaProsseguiComInativar() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto alvo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("CTG-2", new BigDecimal("50"));
        novoItemComCustomizacao(catalogoId, principal.getId(), alvo.getId(), BigDecimal.ONE);

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.INATIVAR, removerCatalogo(), null));

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

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR,
                        substituirCatalogo(subCatalogo(TipoVinculoProduto.CUSTOMIZACAO_ANEXADA, customizacao.getId(), substituto.getId())),
                        null));

        ItemCatalogoCustomizacao customizacaoAtualizada = itemCatalogoCustomizacaoRepository.findById(customizacao.getId()).orElseThrow();
        assertEquals(substituto.getId(), customizacaoAtualizada.getProduto().getId());

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("7.50").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o novo custo (sem override)");

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }
}
