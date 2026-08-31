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
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.DivisaoProducaoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-PROD-VINC-04 (V0.8.2, #320, P-B018) — dividir() propaga, para as filhas, o histórico de origem
 * (ITEM_ADICIONADO) e o vínculo (orcamento_producoes) dos orçamentos que efetivamente contribuíram
 * para os produtos que cada filha recebeu. Como dividir() nunca fraciona a quantidade de um produto
 * entre as duas filhas (cada produto, inteiro, vai pra uma só — UNIQUE(producao_id, produto_id)),
 * "propagar proporcionalmente" aqui significa: a filha que recebe o produto recebe todo o histórico
 * daquele produto; a que não recebe, não recebe nada dele.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoDividirPropagaVinculoIT {

    @Autowired ProducaoService producaoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private Insumo insumoBloqueante;
    private Insumo insumoLiberado;
    private int proximoNumeroProduto = 1;

    private void seedUsuarioEInsumos() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-dividir-vinculo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Dividir Vínculo").ativa(true).build());

        insumoBloqueante = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Chocolate Belga").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1")).permitirEstoqueNegativo(false).fracionavel(true).build());
        insumoLiberado = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(2).nome("Farinha").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("10000")).permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private Produto novoProduto(Insumo insumoDaFicha) {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(BigDecimal.ZERO)
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("50.00")).build());
        // quantidade alta na ficha técnica garante que mesmo produção pequena (qty=1) já supere o
        // estoque de insumoBloqueante (1 unidade) — necessaria = 20 * (quantidadeProducao/rendimento=10).
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumoDaFicha).quantidade(new BigDecimal("20")).build());
        return produto;
    }

    private UUID criarProducaoComProdutoDireto(Produto produto, int quantidade) {
        CriarProducaoRequest req = new CriarProducaoRequest();
        req.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produto.getId());
        item.setQuantidade(new BigDecimal(quantidade));
        req.setProdutos(List.of(item));
        return producaoService.criarProducao(req).getId();
    }

    private void adicionarProdutoDireto(UUID producaoId, Produto produto, int quantidade) {
        Producao producao = producaoRepository.findById(producaoId).orElseThrow();
        producaoProdutoRepository.save(com.penseprecifique.api.shared.domain.entity.ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(new BigDecimal(quantidade)).build());
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

    private DivisaoProducaoResponse dividir(UUID producaoId) {
        IniciarProducaoRequest request = new IniciarProducaoRequest();
        request.setDividir(true);
        Object resultado = producaoService.iniciar(producaoId, request);
        return assertInstanceOf(DivisaoProducaoResponse.class, resultado);
    }

    private List<HistoricoStatusProducao> itensAdicionados(UUID producaoId) {
        return historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producaoId).stream()
                .filter(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .toList();
    }

    /** Caso 1 — divisão simples: produto com 1 origem propaga histórico + vínculo pra filha correta, sem tocar a outra. */
    @Test
    void divisaoSimplesPropagaHistoricoEVinculoParaFilhaCorreta() {
        seedUsuarioEInsumos();
        Produto produtoLiberado = novoProduto(insumoLiberado);
        Produto produtoBloqueado = novoProduto(insumoBloqueante);

        UUID producaoId = criarProducaoComProdutoDireto(produtoBloqueado, 3);
        UUID orcamentoA = criarOrcamentoComProduto(produtoLiberado, 5);
        vincular(orcamentoA, producaoId);

        DivisaoProducaoResponse divisao = dividir(producaoId);
        UUID producaoAId = divisao.getProducaoA().getId();
        UUID producaoBId = divisao.getProducaoB().getId();

        List<HistoricoStatusProducao> historicoA = itensAdicionados(producaoAId);
        assertEquals(1, historicoA.size());
        assertEquals(produtoLiberado.getId(), historicoA.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("5").compareTo(historicoA.get(0).getQuantidade()));
        assertEquals(orcamentoA, historicoA.get(0).getReferenciaOrcamento().getId());
        assertEquals(TipoEventoHistoricoProducao.ITEM_ADICIONADO, historicoA.get(0).getTipoEvento());

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoAId).isPresent(),
                "produção A deve ganhar o vínculo do orçamento que originou o produto que ela recebeu");

        assertTrue(itensAdicionados(producaoBId).isEmpty(), "produção B não recebeu nenhum produto com origem — sem histórico propagado");
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoBId).isEmpty(),
                "produção B não pode herdar o vínculo de um orçamento cujo produto não foi pra ela");

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoId).isPresent(),
                "vínculo da produção original (agora NAO_REALIZADA) permanece intocado — append-only");
    }

    /** Caso 2 — múltiplas origens no mesmo produto: cada filha recebe só o histórico dos orçamentos que contribuíram pro produto dela. */
    @Test
    void divisaoComMultiplasOrigensPropagaCadaOrigemParaAFilhaCorreta() {
        seedUsuarioEInsumos();
        Produto produtoLiberado = novoProduto(insumoLiberado);
        Produto produtoBloqueado = novoProduto(insumoBloqueante);

        UUID producaoId = criarProducaoComProdutoDireto(produtoBloqueado, 1);
        UUID orcamentoA = criarOrcamentoComProduto(produtoLiberado, 3);
        UUID orcamentoB = criarOrcamentoComProduto(produtoLiberado, 5);
        UUID orcamentoC = criarOrcamentoComProduto(produtoBloqueado, 2);
        vincular(orcamentoA, producaoId);
        vincular(orcamentoB, producaoId);
        vincular(orcamentoC, producaoId);

        DivisaoProducaoResponse divisao = dividir(producaoId);
        UUID producaoAId = divisao.getProducaoA().getId();
        UUID producaoBId = divisao.getProducaoB().getId();

        List<HistoricoStatusProducao> historicoA = itensAdicionados(producaoAId);
        assertEquals(2, historicoA.size(), "produção A deve ter as 2 origens do produto liberado (A e B), sem cópia cega");
        assertTrue(historicoA.stream().anyMatch(h -> h.getReferenciaOrcamento().getId().equals(orcamentoA)
                && new BigDecimal("3").compareTo(h.getQuantidade()) == 0));
        assertTrue(historicoA.stream().anyMatch(h -> h.getReferenciaOrcamento().getId().equals(orcamentoB)
                && new BigDecimal("5").compareTo(h.getQuantidade()) == 0));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoAId).isPresent());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, producaoAId).isPresent());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoC, producaoAId).isEmpty(),
                "produção A não pode herdar o vínculo do orçamento C, que só contribuiu pro produto bloqueado");

        List<HistoricoStatusProducao> historicoB = itensAdicionados(producaoBId);
        assertEquals(1, historicoB.size());
        assertEquals(orcamentoC, historicoB.get(0).getReferenciaOrcamento().getId());
        assertEquals(0, new BigDecimal("2").compareTo(historicoB.get(0).getQuantidade()));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoC, producaoBId).isPresent());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoBId).isEmpty());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, producaoBId).isEmpty());
    }

    /** Caso 3 — produtos sem nenhuma origem rastreada (adicionados manualmente): divisão não quebra, só não propaga o que não existia. */
    @Test
    void divisaoSemOrigemRastreadaNaoQuebraENaoPropagaNada() {
        seedUsuarioEInsumos();
        Produto produtoLiberado = novoProduto(insumoLiberado);
        Produto produtoBloqueado = novoProduto(insumoBloqueante);

        UUID producaoId = criarProducaoComProdutoDireto(produtoLiberado, 5);
        adicionarProdutoDireto(producaoId, produtoBloqueado, 3);

        DivisaoProducaoResponse divisao = dividir(producaoId);
        UUID producaoAId = divisao.getProducaoA().getId();
        UUID producaoBId = divisao.getProducaoB().getId();

        assertEquals(EstadoProducao.EM_ANDAMENTO, divisao.getProducaoA().getEstado());
        assertEquals(EstadoProducao.TRAVADA, divisao.getProducaoB().getEstado());
        assertTrue(itensAdicionados(producaoAId).isEmpty());
        assertTrue(itensAdicionados(producaoBId).isEmpty());
    }

    /** Achado da investigação — orçamento parcialmente desvinculado antes da divisão: só o saldo líquido (pós ITEM_REMOVIDO) é propagado. */
    @Test
    void divisaoPropagaSaldoLiquidoQuandoOrcamentoFoiDesvinculadoAntes() {
        seedUsuarioEInsumos();
        Produto produtoLiberado = novoProduto(insumoLiberado);
        Produto produtoBloqueado = novoProduto(insumoBloqueante);

        UUID producaoId = criarProducaoComProdutoDireto(produtoBloqueado, 1);
        UUID orcamentoA = criarOrcamentoComProduto(produtoLiberado, 5);
        UUID orcamentoB = criarOrcamentoComProduto(produtoLiberado, 3);
        vincular(orcamentoA, producaoId);
        vincular(orcamentoB, producaoId);

        orcamentoService.desvincularProducao(orcamentoA, producaoId);

        var produtoLiberadoNaProducao = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producaoId, produtoLiberado.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(produtoLiberadoNaProducao.getQuantidade()),
                "só a contribuição de B (3) deve restar após desvincular A");

        DivisaoProducaoResponse divisao = dividir(producaoId);
        UUID producaoAId = divisao.getProducaoA().getId();

        List<HistoricoStatusProducao> historicoA = itensAdicionados(producaoAId);
        assertEquals(1, historicoA.size(), "orçamento A foi desvinculado — só o saldo líquido de B pode ser propagado");
        assertEquals(orcamentoB, historicoA.get(0).getReferenciaOrcamento().getId());
        assertEquals(0, new BigDecimal("3").compareTo(historicoA.get(0).getQuantidade()));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producaoAId).isEmpty(),
                "orçamento desvinculado antes da divisão não pode reaparecer vinculado na filha");
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, producaoAId).isPresent());
    }
}
