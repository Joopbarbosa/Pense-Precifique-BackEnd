package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1036) — compromissos da produção: início e término previstos (RN-NOVA-16), aviso de estouro da entrega
 * prometida (RN-NOVA-17). CEN-NOVO-39 a 44, 77 e 78. As datas são relativas a hoje (relógio de Brasília).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompromissosProducaoIT {

    @Autowired AgendaService agendaService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente cliente;
    private LocalDate hoje;
    private int proximoNumeroProducao = 1;
    private int proximoNumeroProduto = 1;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder()
                .email("agenda-prod-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ativa(true).build());
    }

    private Produto produto(String nome, String marca) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumeroProduto++).nome(nome).marca(marca).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("50.00")).build());
    }

    private Producao producao(EstadoProducao estado, Integer inicioEmDias, Integer terminoEmDias, Produto... produtos) {
        Producao p = producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(estado)
                .dataInicio(inicioEmDias == null ? null : hoje.plusDays(inicioEmDias))
                .dataTerminoPrevista(terminoEmDias == null ? null : hoje.plusDays(terminoEmDias)).build());
        for (Produto produto : produtos) {
            producaoProdutoRepository.save(ProducaoProduto.builder().producao(p).produto(produto).quantidade(BigDecimal.ONE).build());
        }
        return p;
    }

    private void passouPorEmAndamento(Producao p) {
        historicoRepository.save(HistoricoStatusProducao.builder().producao(p).statusAnterior(EstadoProducao.AGUARDANDO_INICIO)
                .statusNovo(EstadoProducao.EM_ANDAMENTO).dataTransicao(LocalDateTime.now())
                .origem(com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus.USUARIO).build());
    }

    private void mudarEstado(Producao p, EstadoProducao estado) {
        p.setEstado(estado);
        producaoRepository.save(p);
    }

    private List<CompromissoResponse> janela(int dias) {
        return agendaService.compromissos(hoje.minusDays(0), hoje.plusDays(dias));
    }

    private List<CompromissoResponse> doTipo(List<CompromissoResponse> l, TipoCompromisso t) {
        return l.stream().filter(c -> c.tipo() == t).toList();
    }

    private String ident(Producao p) {
        return com.penseprecifique.api.util.IdentificadorFormatter.formatar("PRD", p.getNumero());
    }

    /** CEN-NOVO-39 — início e término previstos nos dias certos, com o produto no título. */
    @Test
    void inicioETerminoApareceNosDiasCertos() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 2, 4, produto("Bolo de cenoura", null));
        List<CompromissoResponse> r = janela(10);
        CompromissoResponse inicio = doTipo(r, TipoCompromisso.INICIO_PRODUCAO).get(0);
        CompromissoResponse termino = doTipo(r, TipoCompromisso.TERMINO_PRODUCAO).get(0);
        assertEquals("Início previsto — " + ident(p) + " (Bolo de cenoura)", inicio.titulo());
        assertEquals(hoje.plusDays(2), inicio.dataExibicao());
        assertEquals("Término previsto — " + ident(p) + " (Bolo de cenoura)", termino.titulo());
        assertEquals(hoje.plusDays(4), termino.dataExibicao());
    }

    /** CEN-NOVO-40 — produção sem datas não gera compromisso. */
    @Test
    void producaoSemDatasNaoGeraCompromisso() {
        seed();
        producao(EstadoProducao.AGUARDANDO_INICIO, null, null, produto("Bolo", null));
        assertTrue(janela(10).isEmpty());
    }

    /** CEN-NOVO-41 — término vencido fica atrasado; finalizar encerra. */
    @Test
    void terminoVencidoFicaAtrasadoEFinalizarEncerra() {
        seed();
        Producao p = producao(EstadoProducao.EM_ANDAMENTO, -10, -3, produto("Bolo", null));
        CompromissoResponse c = doTipo(janela(3), TipoCompromisso.TERMINO_PRODUCAO).get(0);
        assertTrue(c.atrasado());
        assertEquals(3, c.diasAtraso());
        assertEquals(hoje.minusDays(3), c.dataExibicao());
        assertEquals(hoje, c.dataFim());
        mudarEstado(p, EstadoProducao.FINALIZADA);
        assertTrue(doTipo(janela(3), TipoCompromisso.TERMINO_PRODUCAO).isEmpty());
    }

    /** CEN-NOVO-42 — início vencido sem iniciar fica atrasado; iniciar encerra o compromisso de início. */
    @Test
    void inicioVencidoFicaAtrasadoEIniciarEncerra() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, -3, 5, produto("Bolo", null));
        CompromissoResponse c = doTipo(janela(3), TipoCompromisso.INICIO_PRODUCAO).get(0);
        assertTrue(c.atrasado());
        assertEquals(3, c.diasAtraso());
        passouPorEmAndamento(p);
        mudarEstado(p, EstadoProducao.EM_ANDAMENTO);
        assertTrue(doTipo(janela(3), TipoCompromisso.INICIO_PRODUCAO).isEmpty());
        assertFalse(doTipo(janela(10), TipoCompromisso.TERMINO_PRODUCAO).isEmpty());
    }

    /** CEN-NOVO-43 — produção travada fica marcada; a marcação some ao retomar. */
    @Test
    void producaoTravadaFicaMarcada() {
        seed();
        Producao p = producao(EstadoProducao.TRAVADA, null, 2, produto("Bolo", null));
        assertEquals(List.of("Travada"), doTipo(janela(5), TipoCompromisso.TERMINO_PRODUCAO).get(0).marcacoes());
        passouPorEmAndamento(p);
        mudarEstado(p, EstadoProducao.EM_ANDAMENTO);
        assertTrue(doTipo(janela(5), TipoCompromisso.TERMINO_PRODUCAO).get(0).marcacoes().isEmpty());
    }

    /** CEN-NOVO-44 — aviso de estouro lista todos os orçamentos que estouram, por data e depois por número; não cita os que não estouram. */
    @Test
    void estouroListaOsOrcamentosPorDataEPorNumero() {
        seed();
        Produto produto = produto("Bolo", null);
        Producao p = producao(EstadoProducao.EM_ANDAMENTO, null, 6, produto);
        UUID a = aprovadoComEntregaEm(3);
        UUID b = aprovadoComEntregaEm(3);
        UUID c = aprovadoComEntregaEm(8);
        for (UUID id : List.of(c, b, a)) {
            orcamentoProducaoRepository.save(OrcamentoProducao.builder()
                    .orcamento(orcamentoRepository.findById(id).orElseThrow()).producao(p).build());
        }
        String idA = com.penseprecifique.api.util.IdentificadorFormatter.formatar("ORC", orcamentoService.buscarPorId(a).getNumero());
        String idB = com.penseprecifique.api.util.IdentificadorFormatter.formatar("ORC", orcamentoService.buscarPorId(b).getNumero());
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        CompromissoResponse termino = doTipo(janela(10), TipoCompromisso.TERMINO_PRODUCAO).get(0);
        assertEquals(List.of("Passa da entrega prometida: " + idA + " (" + hoje.plusDays(3).format(f) + "), " + idB + " (" + hoje.plusDays(3).format(f) + ")"),
                termino.avisos());
    }

    /** Um só orçamento vinculado: o aviso é "Passa da entrega prometida (dd/mm)". Sem estouro, sem aviso. */
    @Test
    void umOrcamentoVinculadoAvisaComASoData() {
        seed();
        Producao p = producao(EstadoProducao.EM_ANDAMENTO, null, 6, produto("Bolo", null));
        UUID a = aprovadoComEntregaEm(3);
        orcamentoProducaoRepository.save(OrcamentoProducao.builder().orcamento(orcamentoRepository.findById(a).orElseThrow()).producao(p).build());
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        assertEquals(List.of("Passa da entrega prometida (" + hoje.plusDays(3).format(f) + ")"),
                doTipo(janela(10), TipoCompromisso.TERMINO_PRODUCAO).get(0).avisos());
        p.setDataTerminoPrevista(hoje.plusDays(3));
        producaoRepository.save(p);
        assertTrue(doTipo(janela(10), TipoCompromisso.TERMINO_PRODUCAO).get(0).avisos().isEmpty());
    }

    /** CEN-NOVO-77 — vários produtos: primeiro em ordem alfabética com a marca e "+N produtos". */
    @Test
    void variosProdutosNoTitulo() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 3, null,
                produto("Necessaire", null), produto("Bolsa", "Atelier"), produto("Carteira", null));
        CompromissoResponse c = doTipo(janela(10), TipoCompromisso.INICIO_PRODUCAO).get(0);
        assertEquals("Início previsto — " + ident(p) + " (Bolsa — Atelier, +2 produtos)", c.titulo());
    }

    /** CEN-NOVO-78 — travada antes de começar mantém o início; depois de ter começado e travado à mão, o início não volta. */
    @Test
    void travadaAntesDeComecarMantemOInicio() {
        seed();
        Producao p = producao(EstadoProducao.TRAVADA, 2, 6, produto("Bolo", null));
        assertEquals(1, doTipo(janela(10), TipoCompromisso.INICIO_PRODUCAO).size());
        passouPorEmAndamento(p);
        mudarEstado(p, EstadoProducao.EM_ANDAMENTO);
        assertTrue(doTipo(janela(10), TipoCompromisso.INICIO_PRODUCAO).isEmpty());
        mudarEstado(p, EstadoProducao.TRAVADA); // trava manual depois de iniciada
        assertTrue(doTipo(janela(10), TipoCompromisso.INICIO_PRODUCAO).isEmpty());
    }

    private UUID aprovadoComEntregaEm(int dias) {
        Produto produto = produto("Item " + UUID.randomUUID(), null);
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(dias);
        req.setItens(List.of(item));
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        return id;
    }
}
