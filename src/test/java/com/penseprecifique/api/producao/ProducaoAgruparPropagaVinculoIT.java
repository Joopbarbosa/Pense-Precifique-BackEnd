package com.penseprecifique.api.producao;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AgruparProducoesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-21 (V0.8.3, #375+308, P-B004) — {@code agrupar()} propaga o(s) vínculo(s) de
 * {@code orcamento_producoes} das produções de origem para a produção nova resultante, mesmo
 * mecanismo de {@code dividir()}/{@code propagarOrigemParaFilha()}. Sem isso, RN-NOVA-19 (#379)
 * ficaria sem efeito depois de um agrupamento — a produção nova concentra o trabalho ativo, mas
 * ficaria sem vínculo, e o vínculo remanescente aponta só para as originais, já {@code NAO_REALIZADA}
 * (terminal, não bloqueia).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoAgruparPropagaVinculoIT {

    @Autowired ProducaoService producaoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-agrupar-vinculo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Agrupar Vínculo").ativa(true).build());
    }

    private Produto novoProduto() {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("1000"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("50.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("10000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private UUID criarProducao(Produto produto, BigDecimal quantidade) {
        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produto.getId());
        item.setQuantidade(quantidade);
        criar.setProdutos(List.of(item));
        return producaoService.criarProducao(criar).getId();
    }

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private void vincular(UUID orcamentoId, UUID producaoId) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producaoId);
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    private AgruparProducoesRequest requestAgrupar(List<UUID> producaoIds) {
        AgruparProducoesRequest request = new AgruparProducoesRequest();
        request.setProducaoIds(producaoIds);
        request.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        request.setJustificativa("Agrupamento de teste automatizado para RN-NOVA-21 (P-B004)");
        return request;
    }

    /** Caso 1 — 1 origem vinculada a 1 orçamento: nova produção herda o vínculo. */
    @Test
    void agruparPropagaVinculoDeUmaOrigem() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID producaoA = criarProducao(produtoA, new BigDecimal("5"));
        UUID producaoB = criarProducao(produtoB, new BigDecimal("5"));
        UUID orcamentoId = criarOrcamentoComProduto(produtoA, 5);
        vincular(orcamentoId, producaoA);

        Object resultado = producaoService.agrupar(requestAgrupar(List.of(producaoA, producaoB)));
        AgruparProducoesResponse response = (AgruparProducoesResponse) resultado;
        UUID novaId = response.getProducaoNova().getId();

        assertEquals(1, orcamentoProducaoRepository.findByProducaoId(novaId).size());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, novaId).isPresent());
        // vínculo da origem permanece intocado (append-only, mesmo padrão de dividir())
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producaoA).isPresent(),
                "vínculo da produção-mãe original não pode ser removido pelo agrupamento");
    }

    /** Caso 2 — 2 origens vinculadas a orçamentos DIFERENTES: nova produção herda os dois vínculos. */
    @Test
    void agruparPropagaVinculosDeOrigensDiferentes() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID producaoA = criarProducao(produtoA, new BigDecimal("5"));
        UUID producaoB = criarProducao(produtoB, new BigDecimal("5"));
        UUID orcamentoA = criarOrcamentoComProduto(produtoA, 5);
        UUID orcamentoB = criarOrcamentoComProduto(produtoB, 5);
        vincular(orcamentoA, producaoA);
        vincular(orcamentoB, producaoB);

        Object resultado = producaoService.agrupar(requestAgrupar(List.of(producaoA, producaoB)));
        UUID novaId = ((AgruparProducoesResponse) resultado).getProducaoNova().getId();

        assertEquals(2, orcamentoProducaoRepository.findByProducaoId(novaId).size());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, novaId).isPresent());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, novaId).isPresent());
    }

    /** Caso 3 — risco de colisão: 2 origens vinculadas ao MESMO orçamento (produtos diferentes cada
     * uma). Sem deduplicação entre chamadas de propagarOrigemParaFilha(), a 2ª tentaria inserir a
     * mesma linha (orcamento_id, producao_id) de novo, violando UNIQUE(orcamento_id, producao_id). */
    @Test
    void agruparNaoColideQuandoDuasOrigensCompartilhamOMesmoOrcamento() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID producaoA = criarProducao(produtoA, new BigDecimal("3"));
        UUID producaoB = criarProducao(produtoB, new BigDecimal("4"));

        OrcamentoItemRequest itemA = new OrcamentoItemRequest();
        itemA.setProdutoId(produtoA.getId());
        itemA.setMargemAplicada(BigDecimal.ZERO);
        itemA.setPrecoUnitario(new BigDecimal("50.00"));
        itemA.setQuantidade(3);
        OrcamentoItemRequest itemB = new OrcamentoItemRequest();
        itemB.setProdutoId(produtoB.getId());
        itemB.setMargemAplicada(BigDecimal.ZERO);
        itemB.setPrecoUnitario(new BigDecimal("50.00"));
        itemB.setQuantidade(4);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(itemA, itemB));
        req.setSinalAtivo(false);
        UUID orcamentoUnico = orcamentoService.criar(req).getId();

        vincular(orcamentoUnico, producaoA);
        vincular(orcamentoUnico, producaoB);
        assertEquals(2, orcamentoProducaoRepository.findByOrcamentoId(orcamentoUnico).size(),
                "pré-condição: mesmo orçamento vinculado às 2 origens, 2 linhas distintas hoje");

        AgruparProducoesRequest request = requestAgrupar(List.of(producaoA, producaoB));
        Object resultado = assertDoesNotThrow(() -> producaoService.agrupar(request),
                "agrupar() não pode lançar erro de constraint ao propagar vínculo do mesmo orçamento 2x");
        UUID novaId = ((AgruparProducoesResponse) resultado).getProducaoNova().getId();

        List<OrcamentoProducao> vinculosNova = orcamentoProducaoRepository.findByProducaoId(novaId);
        assertEquals(1, vinculosNova.size(), "só 1 linha de vínculo pra nova, mesmo com 2 origens compartilhando o orçamento");
        assertEquals(orcamentoUnico, vinculosNova.get(0).getOrcamento().getId());
    }

    /** Histórico ITEM_ADICIONADO é propagado por origem, mesmo padrão de dividir(). */
    @Test
    void agruparPropagaHistoricoItemAdicionadoPorOrigem() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID producaoA = criarProducao(produto, new BigDecimal("6"));
        UUID producaoB = criarProducao(novoProduto(), new BigDecimal("2"));
        UUID orcamentoId = criarOrcamentoComProduto(produto, 6);
        vincular(orcamentoId, producaoA);

        Object resultado = producaoService.agrupar(requestAgrupar(List.of(producaoA, producaoB)));
        UUID novaId = ((AgruparProducoesResponse) resultado).getProducaoNova().getId();

        List<HistoricoStatusProducao> historico = historicoStatusProducaoRepository
                .findByProducaoIdOrderByDataTransicaoAsc(novaId);
        assertTrue(historico.stream().anyMatch(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO
                        && h.getReferenciaOrcamento() != null && h.getReferenciaOrcamento().getId().equals(orcamentoId)),
                "histórico ITEM_ADICIONADO do orçamento propagado deve existir na produção nova");
    }
}
