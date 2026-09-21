package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoVinculoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
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
 * <p>V0.13.0 (#516, RN-NOVA-1) — Item de Catálogo passou de "1 produto principal + N customizações
 * anexadas" para composição livre de N componentes; os antigos {@code TipoVinculoProduto
 * .ITEM_CATALOGO_PRINCIPAL}/{@code CUSTOMIZACAO_ANEXADA} viraram um único {@code
 * ITEM_CATALOGO_COMPONENTE}, e {@code vinculoId} passou a ser sempre {@code
 * ItemCatalogoComponente.id} (nunca mais {@code ItemCatalogo.id}). REMOVER_VINCULOS agora remove só
 * a linha do componente (nunca mais soft-deleta o Item de Catálogo inteiro — RN-NOVA-1 exige pelo
 * menos 1 componente, mas a resolução de vínculo não reforça esse mínimo de volta, ver
 * {@code ProdutoService#removerVinculosCatalogo}). O preço também deixou de herdar
 * {@code produto.precoVenda} (CAT-003): agora é custo dos componentes (precoCusto) + mão de obra +
 * margem (RN-NOVA-2/3) — os exemplos numéricos abaixo usam tempoProducao=0 e margem padrão 0 (sem
 * ConfiguracaoPrecificacao seedada) para isolar o efeito da substituição de componente.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoResolverVinculosIT {

    @Autowired ProdutoService produtoService;
    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;
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

    private UUID novoCatalogo(String nome) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        CatalogoResponse response = catalogoService.cadastrar(request);
        return response.getId();
    }

    private ItemCatalogoResponse novoItemComComponentes(UUID catalogoId, String nome, UUID... produtoBaseIds) {
        ItemCatalogoRequest item = new ItemCatalogoRequest();
        item.setNome(nome);
        item.setTempoProducao(0);
        item.setComponentes(List.of(produtoBaseIds).stream().map(id -> {
            ItemCatalogoComponenteRequest req = new ItemCatalogoComponenteRequest();
            req.setProdutoBaseId(id);
            req.setQuantidade(BigDecimal.ONE);
            return req;
        }).toList());
        return itemCatalogoService.adicionar(catalogoId, item);
    }

    private ItemCatalogoResponse novoItem(UUID catalogoId, UUID produtoBaseId) {
        return novoItemComComponentes(catalogoId, "Kit " + UUID.randomUUID(), produtoBaseId);
    }

    /** vinculoId exigido pela resolução é sempre o id do componente (ItemCatalogoComponente), não
     * mais o do Item de Catálogo — só há 1 componente por produtoBase nestes testes. */
    private UUID componenteIdPorProdutoBase(UUID produtoBaseId) {
        return itemCatalogoComponenteRepository.findByProdutoBaseId(produtoBaseId).get(0).getId();
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

    private SubstituicaoVinculoProdutoRequest subCatalogo(UUID vinculoId, UUID novoProdutoId) {
        SubstituicaoVinculoProdutoRequest sub = new SubstituicaoVinculoProdutoRequest();
        sub.setTipo(TipoVinculoProduto.ITEM_CATALOGO_COMPONENTE);
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
    void excluirComVinculoDeComponenteDeCatalogoSemResolverContinuaBloqueado() {
        seedUsuario();
        Produto produto = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1");
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
        UUID catalogoId = novoCatalogo("CTG-1");
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.INATIVAR, removerCatalogo(), null));

        // REMOVER_VINCULOS remove só a linha do componente — o Item de Catálogo em si continua
        // existindo (RN-NOVA-1 não é reforçada de volta aqui), só fica sem nenhum componente.
        assertNull(itemCatalogoRepository.findById(item.getId()).orElseThrow().getDeletedAt());
        assertTrue(itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId()).isEmpty());
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
        UUID catalogo1 = novoCatalogo("CTG-1");
        UUID catalogo2 = novoCatalogo("CTG-2");
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

        assertNull(itemCatalogoRepository.findById(item1.getId()).orElseThrow().getDeletedAt());
        assertNull(itemCatalogoRepository.findById(item2.getId()).orElseThrow().getDeletedAt());
        assertTrue(itemCatalogoComponenteRepository.findByItemCatalogoId(item1.getId()).isEmpty());
        assertTrue(itemCatalogoComponenteRepository.findByItemCatalogoId(item2.getId()).isEmpty());

        FichaTecnicaItem componenteAtualizado = fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow();
        assertEquals(substitutoComponente.getId(), componenteAtualizado.getProdutoBase().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(produtoPai.getId()).orElseThrow().getPrecoCusto()));

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }

    @Test
    void resolverVinculosSubstituirCatalogoEComponenteCobrindoTudoAtualizaTudoEProsseguiComExcluir() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1");
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());
        UUID componenteCatalogoId = componenteIdPorProdutoBase(alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));

        Produto substitutoPrincipal = novoProduto("Substituto principal", TipoProduto.PRODUTO, new BigDecimal("4.0000"));
        Produto substitutoComponente = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR,
                        substituirCatalogo(subCatalogo(componenteCatalogoId, substitutoPrincipal.getId())),
                        substituirComponente(subComponente(componente.getId(), substitutoComponente.getId()))));

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        ItemCatalogoComponente componenteCatalogoAtualizado =
                itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId()).get(0);
        assertEquals(substitutoPrincipal.getId(), componenteCatalogoAtualizado.getProdutoBase().getId());
        // precoVenda = custoComponentes = substitutoPrincipal.precoCusto(4.00) x quantidade(1) = 4.00
        // (sem mão de obra — tempoProducao=0 — nem margem — padrão 0, sem ConfiguracaoPrecificacao)
        assertEquals(0, new BigDecimal("4.00").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o custo do produto substituto (sem override)");

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
        UUID catalogoId = novoCatalogo("CTG-1");
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
        assertFalse(itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId()).isEmpty());
        assertEquals(alvo.getId(), fichaTecnicaItemRepository.findById(componente1.getId()).orElseThrow().getProdutoBase().getId());
        assertNull(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void resolverVinculosBlocoCatalogoAusenteQuandoHaVinculoLancaENaoAplicaNada() {
        seedUsuario();
        Produto alvo = novoProduto("PRO-6", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        UUID catalogoId = novoCatalogo("CTG-1");
        ItemCatalogoResponse item = novoItem(catalogoId, alvo.getId());

        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem componente = novoComponente(produtoPai, alvo, new BigDecimal("2"));
        Produto substituto = novoProduto("Substituto componente", TipoProduto.PRODUTO, new BigDecimal("5.0000"));

        // bloco "catalogo" ausente, mas o produto tem vínculo de catálogo pendente — deve falhar sem aplicar o bloco "componente".
        ResolverVinculosProdutoRequest request = request(OperacaoPosResolucaoVinculo.EXCLUIR,
                null,
                substituirComponente(subComponente(componente.getId(), substituto.getId())));

        assertThrows(BusinessException.class, () -> produtoService.resolverVinculos(alvo.getId(), request));

        assertFalse(itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId()).isEmpty());
        assertEquals(alvo.getId(), fichaTecnicaItemRepository.findById(componente.getId()).orElseThrow().getProdutoBase().getId());
        assertNull(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt());
    }

    // ---------------------------------------------------------------
    // resolver-vinculos — segundo componente do mesmo item (antigo caso "customização anexada")
    // ---------------------------------------------------------------

    @Test
    void resolverVinculosRemoverVinculosSegundoComponenteProsseguiComInativar() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto alvo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        UUID catalogoId = novoCatalogo("CTG-2");
        ItemCatalogoResponse item = novoItemComComponentes(catalogoId, "Kit Bolo", principal.getId(), alvo.getId());

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.INATIVAR, removerCatalogo(), null));

        // só o componente do alvo é removido — o componente do principal permanece.
        List<ItemCatalogoComponente> restantes = itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId());
        assertEquals(1, restantes.size());
        assertEquals(principal.getId(), restantes.get(0).getProdutoBase().getId());
        assertFalse(produtoRepository.findById(alvo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void resolverVinculosSubstituirSegundoComponenteTrocaProdutoERecalculaPreco() {
        seedUsuario();
        Produto principal = novoProduto("Bolo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto alvo = novoProduto("Topo de bolo", TipoProduto.CUSTOMIZACAO, new BigDecimal("1.0000"));
        Produto substituto = novoProduto("Topo de bolo especial", TipoProduto.CUSTOMIZACAO, new BigDecimal("3.0000"));
        UUID catalogoId = novoCatalogo("CTG-2");
        ItemCatalogoResponse item = novoItemComComponentes(catalogoId, "Kit Bolo", principal.getId(), alvo.getId());
        UUID componenteAlvoId = componenteIdPorProdutoBase(alvo.getId());

        produtoService.resolverVinculos(alvo.getId(),
                request(OperacaoPosResolucaoVinculo.EXCLUIR,
                        substituirCatalogo(subCatalogo(componenteAlvoId, substituto.getId())),
                        null));

        List<ItemCatalogoComponente> componentesAtualizados = itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId());
        assertTrue(componentesAtualizados.stream().anyMatch(c -> substituto.getId().equals(c.getProdutoBase().getId())));

        ItemCatalogo itemAtualizado = itemCatalogoRepository.findById(item.getId()).orElseThrow();
        // precoVenda = custoComponentes = principal.precoCusto(2.00) x 1 + substituto.precoCusto(3.00) x 1 = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(itemAtualizado.getPrecoVenda()), "precoVenda deveria acompanhar o custo do componente substituto (sem override)");

        assertTrue(produtoRepository.findById(alvo.getId()).orElseThrow().getDeletedAt() != null);
    }
}
