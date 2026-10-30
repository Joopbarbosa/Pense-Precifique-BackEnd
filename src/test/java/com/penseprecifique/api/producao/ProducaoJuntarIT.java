package com.penseprecifique.api.producao;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.JuntarProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.ResolverExcedenteRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.CandidataJuntarResponse;
import com.penseprecifique.api.shared.dto.response.producao.JuntarProducoesResponse;
import com.penseprecifique.api.shared.dto.response.producao.PedidoAtendidoResponse;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RN-NOVA-42 (#1169) — juntar produções de pedidos diferentes num lote. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoJuntarIT {

    @Autowired ProducaoService producaoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired com.penseprecifique.api.orcamento.OrcamentoRepository orcamentoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-juntar-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Maria").ativa(true).build());
    }

    private Produto novoProduto(int numero, boolean insumoFracionavel) {
        return novoProduto(numero, insumoFracionavel, new BigDecimal("12"));
    }

    private Produto novoProduto(int numero, boolean insumoFracionavel, BigDecimal rendimento) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Brownie " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("1000"))
                .permitirEstoqueNegativo(true).rendimento(rendimento)
                .precoVenda(new BigDecimal("50.00")).build());
        UnidadeMedida un = unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), "un")
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome("un").sigla("un").build()));
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Ovos " + numero).marca("X").unidadeMedida(un)
                .estoqueAtual(new BigDecimal("10000")).permitirEstoqueNegativo(true)
                .fracionavel(insumoFracionavel).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private UUID criarProducao(Produto produto, String quantidade, LocalDate termino) {
        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(termino);
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produto.getId());
        item.setQuantidade(new BigDecimal(quantidade));
        criar.setProdutos(List.of(item));
        return producaoService.criarProducao(criar).getId();
    }

    private UUID orcamento(Produto produto, int quantidade, LocalDate inicio, Integer prazoDias) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(prazoDias != null);
        req.setDataInicioEstimada(inicio);
        req.setPrazoProducaoDias(prazoDias);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private void vincular(UUID orcamentoId, UUID producaoId) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producaoId);
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** Cria a produção pelo caminho real do orçamento (RN-NOVA-39: arredonda ao múltiplo do rendimento). */
    private UUID producaoDoOrcamento(UUID orcamentoId, LocalDate termino) {
        CriarProducaoVinculadaRequest req = new CriarProducaoVinculadaRequest();
        req.setDataTerminoPrevista(termino);
        orcamentoService.criarProducaoVinculada(orcamentoId, req);
        return orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).get(0).getProducao().getId();
    }

    private JuntarProducoesRequest juntarCom(UUID... outras) {
        JuntarProducoesRequest r = new JuntarProducoesRequest();
        r.setProducaoOutraIds(List.of(outras));
        return r;
    }

    private JuntarProducoesResponse cenario(int qa, int qb, LocalDate inicioB, Integer prazoB, LocalDate termino) {
        seed();
        Produto p = novoProduto(1, false);
        UUID prodA = producaoDoOrcamento(orcamento(p, qa, null, null), termino);
        UUID prodB = producaoDoOrcamento(orcamento(p, qb, inicioB, prazoB), termino);
        return producaoService.juntar(prodA, juntarCom(prodB));
    }

    @Test
    void cinco_mais_sete_vira_lote_de_doze() {
        JuntarProducoesResponse r = cenario(5, 7, null, null, LocalDate.now().plusDays(3));
        assertEquals(0, new BigDecimal("12").compareTo(r.getProducaoNova().getProdutos().get(0).getQuantidade()));
        List<PedidoAtendidoResponse> pedidos = r.getProducaoNova().getPedidosAtendidos();
        assertEquals(2, pedidos.size());
        assertEquals(12, pedidos.stream().mapToInt(x -> x.getQuantidade().intValue()).sum());
        assertEquals(2, orcamentoProducaoRepository.findByProducaoId(r.getProducaoNova().getId()).size());
    }

    @Test
    void cinco_mais_quatro_arredonda_para_doze_com_sobra() {
        JuntarProducoesResponse r = cenario(5, 4, null, null, LocalDate.now().plusDays(3));
        assertEquals(0, new BigDecimal("12").compareTo(r.getProducaoNova().getProdutos().get(0).getQuantidade()));
        assertEquals(9, r.getProducaoNova().getPedidosAtendidos().stream().mapToInt(x -> x.getQuantidade().intValue()).sum());
    }

    @Test
    void dataPosteriorAoPrazoSoAvisa() {
        LocalDate inicio = LocalDate.now().plusDays(1);
        JuntarProducoesResponse r = cenario(5, 7, inicio, 2, inicio.plusDays(10));
        assertFalse(r.getAvisos().isEmpty());
        assertTrue(r.getAvisos().get(0).contains("posterior ao prazo de entrega de Maria"));
    }

    /** CEN-NOVO-164 e CEN-NOVO-151 revisado (RN-NOVA-48) — qualquer produto entra; Em andamento aparece e pode ser escolhida (R5). */
    @Test
    void candidatasTrazemQualquerProdutoEMarcamEmAndamento() {
        seed();
        Produto p = novoProduto(1, false);
        Produto outro = novoProduto(2, false);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID a = producaoDoOrcamento(orcamento(p, 5, null, null), termino);
        UUID b = producaoDoOrcamento(orcamento(outro, 7, null, null), termino);
        UUID c = producaoDoOrcamento(orcamento(p, 7, null, null), termino);
        var emAndamento = producaoRepository.findById(c).orElseThrow();
        emAndamento.setEstado(EstadoProducao.EM_ANDAMENTO);
        producaoRepository.save(emAndamento);
        List<CandidataJuntarResponse> lista = producaoService.candidatasParaJuntar(a);
        assertEquals(2, lista.size());
        CandidataJuntarResponse deOutroProduto = lista.stream().filter(x -> x.getId().equals(b)).findFirst().orElseThrow();
        assertTrue(deOutroProduto.isSelecionavel());
        assertEquals("AGUARDANDO_INICIO", deOutroProduto.getSituacao());
        CandidataJuntarResponse andamento = lista.stream().filter(x -> x.getId().equals(c)).findFirst().orElseThrow();
        assertTrue(andamento.isSelecionavel());
        assertEquals("EM_ANDAMENTO", andamento.getSituacao());
        JuntarProducoesResponse r = producaoService.juntar(a, juntarCom(b, c));
        assertEquals(3, r.getProducaoNova().getPedidosAtendidos().size());
        assertEquals(3, r.getProducoesOriginais().size());
    }

    /** CEN-NOVO-171 (R5) — junta de uma vez a produção atual com duas outras, uma delas Em andamento com insumo já baixado. */
    @Test
    void juntaVariasDeUmaVezDevolvendoOInsumoDaEmAndamento() {
        seed();
        Produto p = novoProduto(1, false);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID a = producaoDoOrcamento(orcamento(p, 5, null, null), termino);
        UUID b = producaoDoOrcamento(orcamento(p, 4, null, null), termino);
        UUID c = producaoDoOrcamento(orcamento(p, 3, null, null), termino);
        Insumo ovos = fichaTecnicaItemRepository.findByProdutoId(p.getId()).get(0).getInsumo();
        BigDecimal antes = insumoRepository.findById(ovos.getId()).orElseThrow().getEstoqueAtual();
        producaoService.iniciar(c, new com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest());
        BigDecimal baixado = insumoRepository.findById(ovos.getId()).orElseThrow().getEstoqueAtual();
        assertTrue(baixado.compareTo(antes) < 0);
        JuntarProducoesResponse r = producaoService.juntar(a, juntarCom(b, c));
        assertEquals(0, new BigDecimal("12").compareTo(r.getProducaoNova().getProdutos().get(0).getQuantidade()));
        assertEquals(3, r.getProducaoNova().getPedidosAtendidos().size());
        assertEquals(0, antes.compareTo(insumoRepository.findById(ovos.getId()).orElseThrow().getEstoqueAtual()));
    }

    /** CEN-NOVO-172 (R5, negativo) — travada/finalizada, repetida, de mesmo pedido ou lista vazia/grande: erro de negócio, nunca 500. */
    @Test
    void juntarVariasHostil() {
        seed();
        Produto p = novoProduto(1, false);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID oa = orcamento(p, 12, null, null);
        UUID a = producaoDoOrcamento(oa, termino);
        UUID b = producaoDoOrcamento(orcamento(p, 4, null, null), termino);
        UUID travada = producaoDoOrcamento(orcamento(p, 3, null, null), termino);
        var t = producaoRepository.findById(travada).orElseThrow();
        t.setEstado(EstadoProducao.TRAVADA);
        producaoRepository.save(t);
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(b, travada)));
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(b, b)));
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(a, b)));
        UUID mesmoPedido = criarProducao(p, "12", termino);
        vincular(oa, mesmoPedido);
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(b, mesmoPedido)));
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(criarProducao(p, "12", termino))));
    }

    /** CEN-NOVO-163 (RN-NOVA-48) — Brownie 5 + 7 vira uma linha de 12; Necessaire (rendimento 1) fica 4 numa linha separada. */
    @Test
    void produtosDiferentesNaoSomamEntreSi() {
        seed();
        Produto brownie = novoProduto(1, false);
        Produto necessaire = novoProduto(2, false, BigDecimal.ONE);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID a = producaoDoOrcamento(orcamento(brownie, 5, null, null), termino);
        UUID b = producaoDoOrcamento(orcamento(brownie, 7, null, null), termino);
        UUID c = producaoDoOrcamento(orcamento(necessaire, 4, null, null), termino);
        UUID lote1 = producaoService.juntar(a, juntarCom(b)).getProducaoNova().getId();
        JuntarProducoesResponse r = producaoService.juntar(lote1, juntarCom(c));
        var linhas = r.getProducaoNova().getProdutos();
        assertEquals(2, linhas.size());
        assertEquals(12, linhas.stream().filter(l -> l.getProdutoId().equals(brownie.getId())).findFirst().orElseThrow().getQuantidade().intValue());
        assertEquals(4, linhas.stream().filter(l -> l.getProdutoId().equals(necessaire.getId())).findFirst().orElseThrow().getQuantidade().intValue());
        assertEquals(3, r.getProducaoNova().getPedidosAtendidos().size());
    }

    /** CEN-NOVO-169 (RN-NOVA-48) — a Ana cancela: manter o lote deixa as linhas como estão (4 viram sobra); cancelar derruba tudo. */
    @Test
    void cancelarPedidoDeProdutoDiferenteMantendoOuCancelandoLote() {
        seed();
        Produto brownie = novoProduto(1, false);
        Produto necessaire = novoProduto(2, false, BigDecimal.ONE);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID oa = orcamento(brownie, 5, null, null);
        UUID ob = orcamento(brownie, 7, null, null);
        UUID oc = orcamento(necessaire, 4, null, null);
        UUID lote1 = producaoService.juntar(producaoDoOrcamento(oa, termino), juntarCom(producaoDoOrcamento(ob, termino)))
                .getProducaoNova().getId();
        UUID lote = producaoService.juntar(lote1, juntarCom(producaoDoOrcamento(oc, termino))).getProducaoNova().getId();

        orcamentoService.cancelar(oc, cancelamento(lote, false));
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(lote).orElseThrow().getEstado());
        var linhas = producaoService.buscarPorId(lote).getProdutos();
        assertEquals(12, linhas.stream().filter(l -> l.getProdutoId().equals(brownie.getId())).findFirst().orElseThrow().getQuantidade().intValue());
        assertEquals(4, linhas.stream().filter(l -> l.getProdutoId().equals(necessaire.getId())).findFirst().orElseThrow().getQuantidade().intValue());
        assertEquals(2, orcamentoProducaoRepository.findByProducaoId(lote).size());
        assertEquals(2, producaoService.pedidosAtendidosDaProducao(lote).size());
    }

    @Test
    void cancelarPedidoDeProdutoDiferenteCancelandoLoteInteiro() {
        seed();
        Produto brownie = novoProduto(1, false);
        Produto necessaire = novoProduto(2, false, BigDecimal.ONE);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID oa = orcamento(brownie, 5, null, null);
        UUID oc = orcamento(necessaire, 4, null, null);
        UUID lote = producaoService.juntar(producaoDoOrcamento(oa, termino), juntarCom(producaoDoOrcamento(oc, termino)))
                .getProducaoNova().getId();
        orcamentoService.cancelar(oc, cancelamento(lote, true));
        assertEquals(EstadoProducao.CANCELADA, producaoRepository.findById(lote).orElseThrow().getEstado());
        assertTrue(orcamentoProducaoRepository.findByProducaoId(lote).isEmpty());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(oa).stream().noneMatch(v -> v.getProducao().getId().equals(lote)));
    }

    private UUID orcamentoAprovado(Produto produto, int quantidade, LocalDate aprovacao, Integer prazoDias) {
        UUID id = orcamento(produto, quantidade, null, prazoDias);
        var o = orcamentoRepository.findById(id).orElseThrow();
        o.setDataAprovacao(aprovacao == null ? null : aprovacao.atTime(10, 0));
        orcamentoRepository.save(o);
        return id;
    }

    private UUID producaoNoDia(Produto produto, LocalDate inicio, LocalDate termino, EstadoProducao estado) {
        UUID id = producaoDoOrcamento(orcamento(produto, 5, null, null), termino);
        var pr = producaoRepository.findById(id).orElseThrow();
        pr.setDataInicio(inicio);
        pr.setEstado(estado);
        producaoRepository.save(pr);
        return id;
    }

    /** CEN-NOVO-165 (RN-NOVA-49) — aprovado em 02/11 com entrega em 06/11: entram 03/11, 04/11, 05/11; 10/11 e Cancelada não. */
    @Test
    void producoesDoPeriodoDaAprovacaoAteAEntrega() {
        seed();
        Produto p = novoProduto(1, false);
        LocalDate aprovacao = LocalDate.of(2026, 11, 2);
        UUID alvo = orcamentoAprovado(p, 5, aprovacao, 4);
        UUID aguardando = producaoNoDia(p, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), EstadoProducao.AGUARDANDO_INICIO);
        UUID andamento = producaoNoDia(p, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), EstadoProducao.EM_ANDAMENTO);
        UUID travada = producaoNoDia(p, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), EstadoProducao.TRAVADA);
        UUID depois = producaoNoDia(p, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10), EstadoProducao.AGUARDANDO_INICIO);
        UUID cancelada = producaoNoDia(p, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), EstadoProducao.CANCELADA);
        UUID naBorda = producaoNoDia(p, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8), EstadoProducao.AGUARDANDO_INICIO);

        var r = producaoService.producoesDoPeriodo(alvo);
        assertTrue(r.isPeriodoDisponivel());
        assertEquals(aprovacao, r.getDataInicio());
        assertEquals(LocalDate.of(2026, 11, 6), r.getDataFim());
        List<UUID> ids = r.getProducoes().stream().map(CandidataJuntarResponse::getId).toList();
        assertTrue(ids.containsAll(List.of(aguardando, andamento, travada, naBorda)));
        assertFalse(ids.contains(depois));
        assertFalse(ids.contains(cancelada));
        assertTrue(r.getProducoes().stream().filter(x -> x.getId().equals(aguardando)).findFirst().orElseThrow().isSelecionavel());
        assertTrue(r.getProducoes().stream().filter(x -> x.getId().equals(andamento)).findFirst().orElseThrow().isSelecionavel());
        assertFalse(r.getProducoes().stream().filter(x -> x.getId().equals(travada)).findFirst().orElseThrow().isSelecionavel());
    }

    /** CEN-NOVO-165 — orçamento sem aprovação (ou sem prazo) não tem período. */
    @Test
    void producoesDoPeriodoSemAprovacaoOuSemEntrega() {
        seed();
        Produto p = novoProduto(1, false);
        UUID semAprovacao = orcamentoAprovado(p, 5, null, 4);
        UUID semPrazo = orcamentoAprovado(p, 5, LocalDate.of(2026, 11, 2), null);
        assertFalse(producaoService.producoesDoPeriodo(semAprovacao).isPeriodoDisponivel());
        assertFalse(producaoService.producoesDoPeriodo(semPrazo).isPeriodoDisponivel());
        assertTrue(producaoService.producoesDoPeriodo(semPrazo).getProducoes().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> producaoService.producoesDoPeriodo(UUID.randomUUID()));
    }

    @Test
    void entradaHostilNuncaViraErroInterno() {
        seed();
        Produto p = novoProduto(1, false);
        UUID a = producaoDoOrcamento(orcamento(p, 5, null, null), LocalDate.now().plusDays(3));
        assertThrows(BusinessException.class, () -> producaoService.juntar(a, juntarCom(a)));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.juntar(a, juntarCom(UUID.randomUUID())));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.juntar(UUID.randomUUID(), juntarCom(a)));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.candidatasParaJuntar(UUID.randomUUID()));
    }

    /** Monta dois pedidos (5 e 7) já juntados; devolve [orcA, orcB, lote]. */
    private UUID[] lote() {
        seed();
        Produto p = novoProduto(1, false);
        LocalDate termino = LocalDate.now().plusDays(3);
        UUID oa = orcamento(p, 5, null, null);
        UUID ob = orcamento(p, 7, null, null);
        UUID a = producaoDoOrcamento(oa, termino);
        UUID b = producaoDoOrcamento(ob, termino);
        UUID lote = producaoService.juntar(a, juntarCom(b)).getProducaoNova().getId();
        return new UUID[] {oa, ob, lote};
    }

    private AvancaStatusRequest cancelamento(UUID producaoId, boolean cancelar) {
        AvancaStatusRequest req = new AvancaStatusRequest();
        req.setMotivoCancelamento("Cliente desistiu");
        var d = new com.penseprecifique.api.shared.dto.request.orcamento.DecisaoProducaoCancelamentoRequest();
        d.setProducaoId(producaoId);
        d.setCancelar(cancelar);
        req.setDecisoesProducao(List.of(d));
        return req;
    }

    /** CEN-NOVO-148 — cancelar um pedido do lote e manter o lote: só o vínculo dele sai. */
    @Test
    void cancelarPedidoDoLoteMantendoLote() {
        UUID[] l = lote();
        orcamentoService.cancelar(l[1], cancelamento(l[2], false));
        assertEquals(1, orcamentoProducaoRepository.findByProducaoId(l[2]).size());
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(l[2]).orElseThrow().getEstado());
        assertEquals(1, producaoService.pedidosAtendidosDaProducao(l[2]).size());
    }

    /** CEN-NOVO-148 — cancelar o lote inteiro: os outros pedidos ficam sem produção vinculada. */
    @Test
    void cancelarPedidoDoLoteCancelandoLote() {
        UUID[] l = lote();
        orcamentoService.cancelar(l[1], cancelamento(l[2], true));
        assertEquals(EstadoProducao.CANCELADA, producaoRepository.findById(l[2]).orElseThrow().getEstado());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(l[0]).stream().noneMatch(v -> v.getProducao().getId().equals(l[2])));
    }

    /** CEN-NOVO-149 — pedido passa de 5 para 8: soma 15 > 12, aviso com 3 não cobertas. */
    @Test
    void quantidadeAcimaDoLoteAvisaEPermiteManterOuCancelar() {
        UUID[] l = lote();
        assertTrue(producaoService.excedentesDoLote(l[2]).isEmpty());
        var item = orcamentoItemRepository.findByOrcamentoId(l[0]).get(0);
        item.setQuantidade(8);
        orcamentoItemRepository.save(item);
        var exc = producaoService.excedentesDoLote(l[2]);
        assertEquals(1, exc.size());
        assertEquals(3, exc.get(0).getNaoCobertas().intValue());
        ResolverExcedenteRequest manter = new ResolverExcedenteRequest();
        manter.setCancelarLote(false);
        producaoService.resolverExcedente(l[2], manter);
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(l[2]).orElseThrow().getEstado());
        ResolverExcedenteRequest cancelar = new ResolverExcedenteRequest();
        cancelar.setCancelarLote(true);
        producaoService.resolverExcedente(l[2], cancelar);
        assertEquals(EstadoProducao.CANCELADA, producaoRepository.findById(l[2]).orElseThrow().getEstado());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(l[0]).stream().noneMatch(v -> v.getProducao().getId().equals(l[2])));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(l[1]).stream().noneMatch(v -> v.getProducao().getId().equals(l[2])));
    }

    @Test
    void excedenteHostil() {
        UUID[] l = lote();
        ResolverExcedenteRequest r = new ResolverExcedenteRequest();
        r.setCancelarLote(true);
        assertThrows(BusinessException.class, () -> producaoService.resolverExcedente(l[2], r));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.resolverExcedente(UUID.randomUUID(), r));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.excedentesDoLote(UUID.randomUUID()));
    }

    /** CEN-NOVO-150 — falta de insumo trava o lote inteiro e não oferece divisão. */
    @Test
    void producaoDeOutraContaNaoEhLidaNemAlteradaPelosEndpointsDoLote() {
        seed();
        Produto bolo = novoProduto(1, false);
        UUID a = producaoDoOrcamento(orcamento(bolo, 2, LocalDate.now(), 3), LocalDate.now().plusDays(5));
        UUID b = producaoDoOrcamento(orcamento(bolo, 3, LocalDate.now(), 3), LocalDate.now().plusDays(5));
        // outra conta autenticada: nada do lote da primeira é visível, e nada muda
        Usuario intrusa = usuarioRepository.save(Usuario.builder()
                .email("prod-intrusa-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(intrusa.getEmail(), null, List.of()));
        ResolverExcedenteRequest cancelar = new ResolverExcedenteRequest();
        cancelar.setCancelarLote(true);
        assertThrows(ResourceNotFoundException.class, () -> producaoService.candidatasParaJuntar(a));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.excedentesDoLote(a));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.avisosDePrazoDaProducao(a));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.resolverExcedente(a, cancelar));
        assertThrows(ResourceNotFoundException.class, () -> producaoService.juntar(a, juntarCom(b)));
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(a).orElseThrow().getEstado());
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(b).orElseThrow().getEstado());
    }

    @Test
    void loteComInsumoFaltandoTravaInteiroESemDividir() {
        UUID[] l = lote();
        for (Insumo i : insumoRepository.findAll()) {
            if (i.getUsuario().getId().equals(usuario.getId())) {
                i.setEstoqueAtual(BigDecimal.ZERO);
                i.setPermitirEstoqueNegativo(false);
                insumoRepository.save(i);
            }
        }
        com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest req =
                new com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest();
        req.setDividir(true);
        assertThrows(BusinessException.class, () -> producaoService.iniciar(l[2], req));
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producaoRepository.findById(l[2]).orElseThrow().getEstado());
        req.setDividir(false);
        producaoService.iniciar(l[2], req);
        assertEquals(EstadoProducao.TRAVADA, producaoRepository.findById(l[2]).orElseThrow().getEstado());
    }
}
