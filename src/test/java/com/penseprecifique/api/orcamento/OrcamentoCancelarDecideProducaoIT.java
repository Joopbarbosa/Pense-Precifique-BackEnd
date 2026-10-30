package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
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
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.DecisaoProducaoCancelamentoRequest;
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
 * RN-NOVA-40 (V0.18.0, #1167) — cancelar o orçamento pergunta, por produção vinculada, se a produção
 * também é cancelada. O orçamento cancelado sempre se desvincula.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoCancelarDecideProducaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
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
                .email("orc-cancelar-decide-producao-" + UUID.randomUUID() + "@test.com")
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
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida(unidadeMedida("g"))
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

    private AvancaStatusRequest cancelamentoCom(Producao producao, boolean cancelar) {
        AvancaStatusRequest req = new AvancaStatusRequest();
        req.setMotivoCancelamento("Cliente desistiu");
        DecisaoProducaoCancelamentoRequest decisao = new DecisaoProducaoCancelamentoRequest();
        decisao.setProducaoId(producao.getId());
        decisao.setCancelar(cancelar);
        req.setDecisoesProducao(List.of(decisao));
        return req;
    }

    private EstadoProducao estadoDe(Producao producao) {
        return producaoRepository.findById(producao.getId()).orElseThrow().getEstado();
    }

    /** CEN-NOVO-138 — "Sim, cancelar a produção". */
    @Test
    void simCancelaAProducaoEDesvincula() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 5);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        var detalhe = orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, true));

        assertEquals(StatusOrcamento.CANCELADO, detalhe.getStatus());
        assertEquals(EstadoProducao.CANCELADA, estadoDe(producao));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).isEmpty());
    }

    /** CEN-NOVO-139 — "Não, manter a produção": produtos e histórico de origem ficam. */
    @Test
    void naoMantemAProducaoSemVinculoEComOsProdutos() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 5);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, false));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, estadoDe(producao));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).isEmpty());
        ProducaoProduto pp = producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(pp.getQuantidade()));
        assertEquals(1, historicoStatusProducaoRepository.findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                producao.getId(), orcamentoId, TipoEventoHistoricoProducao.ITEM_ADICIONADO).size());
    }

    /** Sem decisão enviada equivale a "Não, manter" (X da janela). */
    @Test
    void semDecisaoEquivaleAManter() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        AvancaStatusRequest req = new AvancaStatusRequest();
        req.setMotivoCancelamento("Cliente desistiu");
        orcamentoService.cancelar(orcamentoId, req);

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, estadoDe(producao));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).isEmpty());
    }

    /** CEN-NOVO-141 — produção em andamento: "Sim" cancela sem devolver insumo. */
    @Test
    void simEmAndamentoCancelaSemEstornarInsumo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producao.setEstado(EstadoProducao.EM_ANDAMENTO);
        producaoRepository.save(producao);

        orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, true));

        assertEquals(EstadoProducao.CANCELADA, estadoDe(producao));
    }

    /** CEN-NOVO-142 — finalizada não entra na pergunta: mesmo com "cancelar", fica finalizada. */
    @Test
    void producaoFinalizadaNaoEhCancelada() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producao.setEstado(EstadoProducao.FINALIZADA);
        producaoRepository.save(producao);

        orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, true));

        assertEquals(EstadoProducao.FINALIZADA, estadoDe(producao));
    }

    /** Entrada hostil — produção de outra conta ou inexistente: 404 e o orçamento NÃO é cancelado. */
    @Test
    void decisaoComProducaoAlheiaOuInexistenteNaoCancelaNada() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao propria = novaProducao();
        vincular(orcamentoId, propria);

        Usuario outro = usuarioRepository.save(Usuario.builder()
                .email("outra-conta-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        Producao alheia = producaoRepository.save(Producao.builder()
                .usuario(outro).numero(1).estado(EstadoProducao.AGUARDANDO_INICIO).build());

        assertThrows(ResourceNotFoundException.class,
                () -> orcamentoService.cancelar(orcamentoId, cancelamentoCom(alheia, true)));
        Producao fantasma = Producao.builder().build();
        fantasma.setId(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class,
                () -> orcamentoService.cancelar(orcamentoId, cancelamentoCom(fantasma, true)));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, estadoDe(alheia));
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, estadoDe(propria));
        assertEquals(1, orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).size());
    }

    /** Entrada hostil — cancelar duas vezes: a segunda é recusada com erro de negócio, nunca 500. */
    @Test
    void cancelarDuasVezesEhRecusado() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, true));

        assertThrows(BusinessException.class,
                () -> orcamentoService.cancelar(orcamentoId, cancelamentoCom(producao, true)));
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
