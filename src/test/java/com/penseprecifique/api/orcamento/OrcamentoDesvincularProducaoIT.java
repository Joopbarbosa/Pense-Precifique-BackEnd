package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-ORC-VINC-03 (V0.8.2, #320, P-B017 Parte 3) — desvincular reverte de verdade o que o vínculo
 * adicionou à produção, respeitando múltiplas origens no mesmo produto e a restrição de estado
 * simétrica ao vincular (só AGUARDANDO_INICIO — decisão fechada em chat, ver DECISOES_V0.8.2.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoDesvincularProducaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-desvincular-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Desvincular Produção").ativa(true).build());
    }

    private Produto novoProduto() {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("50.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private OrcamentoItemRequest itemDe(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoRequest requestComItens(List<OrcamentoItemRequest> itens) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(itens);
        req.setSinalAtivo(false);
        return req;
    }

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade) {
        return orcamentoService.criar(requestComItens(List.of(itemDe(produto, quantidade)))).getId();
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** Caso 1 — desvincular simples: 1 orçamento, reverte tudo (ProducaoProduto some, vínculo some). */
    @Test
    void desvincularSimplesReverteTudoCorretamente() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId());

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "ProducaoProduto deve ser removido quando a quantidade zera");
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isEmpty(),
                "vínculo deve deixar de existir após desvincular");

        List<HistoricoStatusProducao> removidos = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producao.getId(), orcamentoId, TipoEventoHistoricoProducao.ITEM_REMOVIDO);
        assertEquals(1, removidos.size());
        assertEquals(produto.getId(), removidos.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("4").compareTo(removidos.get(0).getQuantidade()));
    }

    /** Caso 2 — múltiplas origens no mesmo produto: desvincular um orçamento não toca a contribuição do outro. */
    @Test
    void desvincularComMultiplasOrigensNoMesmoProdutoReverteSoAFracaoDaqueleOrcamento() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        Producao producao = novaProducao();

        UUID orcamentoA = criarOrcamentoComProduto(produto, 3);
        UUID orcamentoB = criarOrcamentoComProduto(produto, 5);
        vincular(orcamentoA, producao);
        vincular(orcamentoB, producao);

        ProducaoProduto antes = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("8").compareTo(antes.getQuantidade()));

        orcamentoService.desvincularProducao(orcamentoA, producao.getId());

        ProducaoProduto depois = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(depois.getQuantidade()),
                "só a fração do orçamento A (3) deve ter sido revertida, preservando a de B (5)");

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producao.getId()).isEmpty());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, producao.getId()).isPresent(),
                "vínculo do orçamento B não pode ser afetado por desvincular A");
    }

    /** Caso 3 — desvincular bloqueado quando a produção não está mais AGUARDANDO_INICIO. */
    @Test
    void desvincularBloqueadoForaDeAguardandoInicio() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        producao.setEstado(EstadoProducao.EM_ANDAMENTO);
        producaoRepository.save(producao);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.desvincularProducao(orcamentoId, producao.getId()));
        assertTrue(ex.getMessage().toLowerCase().contains("já começou"));

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isPresent(),
                "vínculo não pode ser removido quando a reversão é bloqueada");
        ProducaoProduto inalterado = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(inalterado.getQuantidade()));
    }

    /** Caso 4 — piso em zero: produção editada manualmente para uma quantidade menor que o histórico não fica negativa. */
    @Test
    void desvincularComPisoEmZeroQuandoProducaoFoiEditadaManualmente() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 6);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        ProducaoProduto producaoProduto = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        producaoProduto.setQuantidade(new BigDecimal("2"));
        producaoProdutoRepository.save(producaoProduto);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId());

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "quantidade não pode ficar negativa — deve zerar e remover a linha, nunca estourar para baixo");
    }

    /** Vínculo inexistente lança ResourceNotFoundException. */
    @Test
    void desvincularSemVinculoLancaResourceNotFound() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamentoComProduto(novoProduto(), 1);
        Producao producao = novaProducao();

        assertThrows(ResourceNotFoundException.class,
                () -> orcamentoService.desvincularProducao(orcamentoId, producao.getId()));
    }

    /** RN-NOVA-17 (V0.8.3, #375+308, P-S001c) — "Sim, manter": produção EM_ANDAMENTO,
     * manterProdutos=true remove só o vínculo — produto e histórico continuam intactos. */
    @Test
    void desvincularComManterProdutosEmProducaoEmAndamentoRemoveSoOVinculo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producaoService.iniciar(producao.getId(), new IniciarProducaoRequest());

        orcamentoService.desvincularProducao(orcamentoId, producao.getId(), true);

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isEmpty(),
                "vínculo deve ser removido");
        ProducaoProduto inalterado = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("4").compareTo(inalterado.getQuantidade()),
                "produto deve continuar intacto na produção — vira item avulso, sem vínculo ativo");
        List<HistoricoStatusProducao> removidos = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producao.getId(), orcamentoId, TipoEventoHistoricoProducao.ITEM_REMOVIDO);
        assertTrue(removidos.isEmpty(), "histórico ITEM_ADICIONADO permanece intacto — nenhum ITEM_REMOVIDO gerado por este caminho");
    }

    /** RN-NOVA-17 — mesma garantia para TRAVADA (mesma restrição de estado de EM_ANDAMENTO). */
    @Test
    void desvincularComManterProdutosEmProducaoTravadaRemoveSoOVinculo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producao.setEstado(EstadoProducao.TRAVADA);
        producaoRepository.save(producao);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId(), true);

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isEmpty());
        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isPresent(),
                "produto continua na produção travada");
    }

    /** RN-NOVA-17 — manterProdutos=true não tem efeito em AGUARDANDO_INICIO: reversão completa normal. */
    @Test
    void manterProdutosNaoTemEfeitoEmAguardandoInicio() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId(), true);

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "AGUARDANDO_INICIO sempre reverte de verdade, independente da flag manterProdutos");
    }

    /** RN-NOVA-17 — "Não, remover": remove a contribuição de 1 produto em produção ativa, sem
     * mexer em estoque (nenhuma movimentação nova) e sem apagar o vínculo em si. */
    @Test
    void removerProdutoDeProducaoAtivaRemoveSemMexerEmEstoqueNemNoVinculo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        Insumo insumo = fichaTecnicaItemRepository.findByProdutoId(produto.getId()).get(0).getInsumo();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producaoService.iniciar(producao.getId(), new IniciarProducaoRequest());

        BigDecimal estoqueAposIniciar = insumoRepository.findById(insumo.getId()).orElseThrow().getEstoqueAtual();
        int movimentacoesAntes = movimentacaoInsumoRepository
                .findByReferenciaIdAndReferenciaTipo(producao.getId(), ReferenciaMovimentacaoTipo.PRODUCAO).size();

        orcamentoService.removerProdutoDeProducaoAtiva(orcamentoId, producao.getId(), produto.getId());

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "ProducaoProduto deve ser removido (piso zero, mesma contribuição do único orçamento)");

        List<HistoricoStatusProducao> removidos = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producao.getId(), orcamentoId, TipoEventoHistoricoProducao.ITEM_REMOVIDO);
        assertEquals(1, removidos.size());
        assertEquals(produto.getId(), removidos.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("4").compareTo(removidos.get(0).getQuantidade()));

        BigDecimal estoqueDepois = insumoRepository.findById(insumo.getId()).orElseThrow().getEstoqueAtual();
        assertEquals(0, estoqueAposIniciar.compareTo(estoqueDepois),
                "estoque já baixado por iniciar() permanece baixado — sem estorno (RN-072)");
        int movimentacoesDepois = movimentacaoInsumoRepository
                .findByReferenciaIdAndReferenciaTipo(producao.getId(), ReferenciaMovimentacaoTipo.PRODUCAO).size();
        assertEquals(movimentacoesAntes, movimentacoesDepois, "nenhuma movimentação de estoque nova deve ser criada");

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isPresent(),
                "o vínculo em si não é afetado — só a contribuição do produto");
    }

    /** RN-NOVA-17 — remoção por produto é rejeitada em AGUARDANDO_INICIO (esse caso usa o desvincular normal). */
    @Test
    void removerProdutoDeProducaoAtivaRejeitaProducaoAguardandoInicio() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.removerProdutoDeProducaoAtiva(orcamentoId, producao.getId(), produto.getId()));
        assertTrue(ex.getMessage().toLowerCase().contains("aguardando"));
    }

    /** RN-NOVA-17 — orçamento que não contribuiu com aquele produto naquela produção lança ResourceNotFoundException. */
    @Test
    void removerProdutoDeProducaoAtivaLancaResourceNotFoundParaProdutoNaoContribuido() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produtoA, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producaoService.iniciar(producao.getId(), new IniciarProducaoRequest());

        assertThrows(ResourceNotFoundException.class,
                () -> orcamentoService.removerProdutoDeProducaoAtiva(orcamentoId, producao.getId(), produtoB.getId()));
    }
}
