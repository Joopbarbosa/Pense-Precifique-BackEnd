package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EscopoEdicaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.RecorrenciaCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.agenda.CancelarCompromissoRequest;
import com.penseprecifique.api.shared.dto.request.agenda.CompromissoUsuariaRequest;
import com.penseprecifique.api.shared.dto.request.agenda.EditarCompromissoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoUsuariaDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1039, RN-NOVA-19 a RN-NOVA-21) — compromisso da usuária: criar, recorrência semanal e mensal, concluir, cancelar a
 * ocorrência ou a série, editar só a ocorrência ou a série inteira. CEN-NOVO-51 a 62. Datas relativas a hoje.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompromissosUsuariaIT {

    private static final String OBS_30 = "Parei de entregar às terças e quintas.";

    @Autowired AgendaService agendaService;
    @Autowired CompromissoUsuariaService service;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired com.penseprecifique.api.unidademedida.UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoService orcamentoService;
    @Autowired Clock clock;

    private Usuario usuario;
    private LocalDate hoje;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder()
                .email("agenda-user-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero((int) (Math.random() * 100000)).nome(nome).marca("X")
                .unidadeMedida(unidadeMedidaRepository.save(com.penseprecifique.api.shared.domain.entity.UnidadeMedida.builder().usuario(usuario).nome("g").sigla("g").build()))
                .estoqueAtual(BigDecimal.TEN).permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private CompromissoUsuariaRequest simples(String titulo, LocalDate data) {
        return new CompromissoUsuariaRequest(titulo, data, null, null, null, null, null, null);
    }

    private CompromissoUsuariaRequest serie(String titulo, LocalDate inicio, RecorrenciaCompromisso r, List<DayOfWeek> dias, Integer n) {
        return new CompromissoUsuariaRequest(titulo, inicio, null, null, null, r, dias, n);
    }

    private List<CompromissoResponse> avulsos(LocalDate de, LocalDate ate) {
        return agendaService.compromissos(de, ate).stream().filter(c -> c.tipo() == TipoCompromisso.AVULSO).toList();
    }

    private List<LocalDate> datas(List<CompromissoResponse> l) {
        return l.stream().map(CompromissoResponse::dataExibicao).toList();
    }

    private LocalDate proximo(DayOfWeek dia, int minimoDias) {
        return hoje.plusDays(minimoDias).with(TemporalAdjusters.nextOrSame(dia));
    }

    /** CEN-NOVO-51 — compromisso simples com horário e insumo; o detalhe mostra o vínculo sem estoque. */
    @Test
    void criarCompromissoSimples() {
        seed();
        Insumo linha = insumo("Linha 25");
        var req = new CompromissoUsuariaRequest("Comprar linha de bordar", hoje.plusDays(3), LocalTime.of(10, 0), null, linha.getId(), null, null, null);
        CompromissoUsuariaDetalheResponse d = service.criar(req);
        List<CompromissoResponse> r = avulsos(hoje, hoje.plusDays(7));
        assertEquals(1, r.size());
        assertEquals(hoje.plusDays(3), r.get(0).dataExibicao());
        assertEquals(LocalTime.of(10, 0), r.get(0).horario());
        assertEquals("Comprar linha de bordar", r.get(0).titulo());
        assertEquals("Linha 25", d.insumo().nome());
        assertFalse(d.insumo().removido());
    }

    /** CEN-NOVO-52 — título vazio ou data ausente bloqueiam; título com mais de 120 caracteres também. */
    @Test
    void tituloOuDataVaziosBloqueiam() {
        seed();
        assertThrows(BusinessException.class, () -> service.criar(simples("   ", hoje)));
        assertThrows(BusinessException.class, () -> service.criar(simples("Reunião", null)));
        assertThrows(BusinessException.class, () -> service.criar(simples("Reunião", LocalDate.of(1999, 1, 1))));
    }

    /** CEN-NOVO-53 — insumo excluído depois do vínculo: o compromisso continua e o detalhe mostra "removido". */
    @Test
    void insumoRemovidoDepoisDoVinculo() {
        seed();
        Insumo linha = insumo("Linha 25");
        var d = service.criar(new CompromissoUsuariaRequest("Comprar linha", hoje.plusDays(2), null, null, linha.getId(), null, null, null));
        linha.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(linha);
        assertEquals(1, avulsos(hoje, hoje.plusDays(5)).size());
        assertTrue(service.detalhe(d.id(), d.dataOriginal()).insumo().removido());
    }

    /** CEN-NOVO-54 — semanal terças e quintas, 4 ocorrências, cada uma com situação própria. */
    @Test
    void recorrenciaSemanalComQuatroOcorrencias() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        service.criar(serie("Entregar encomendas", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 4));
        List<LocalDate> esperado = List.of(terca, terca.plusDays(2), terca.plusDays(7), terca.plusDays(9));
        assertEquals(esperado, datas(avulsos(terca, terca.plusDays(40))));
        assertTrue(avulsos(terca, terca.plusDays(40)).stream().allMatch(c -> c.situacao() == SituacaoCompromisso.PENDENTE));
    }

    /** Os sete dias da semana cabem na série (achado do E2E: a coluna dos dias era curta e a criação dava erro). */
    @Test
    void recorrenciaSemanalComOsSeteDiasDaSemana() {
        seed();
        LocalDate inicio = proximo(DayOfWeek.MONDAY, 1);
        service.criar(serie("Todo dia", inicio, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.values()), 10));
        assertEquals(10, avulsos(inicio, inicio.plusDays(20)).size());
    }

    /** CEN-NOVO-55 — mensal no dia 31 cai no último dia dos meses curtos (31/01, 28/02, 31/03 de 2027). */
    @Test
    void recorrenciaMensalNoDia31() {
        seed();
        service.criar(serie("Pagar imposto", LocalDate.of(2027, 1, 31), RecorrenciaCompromisso.MENSAL, null, 3));
        assertEquals(List.of(LocalDate.of(2027, 1, 31)), datas(avulsos(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))));
        assertEquals(List.of(LocalDate.of(2027, 2, 28)), datas(avulsos(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28))));
        assertEquals(List.of(LocalDate.of(2027, 3, 31)), datas(avulsos(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))));
        assertTrue(avulsos(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 30)).isEmpty(), "só 3 ocorrências");
    }

    /** CEN-NOVO-56 — série indefinida aparece em qualquer período navegado (março de 2027: segundas 01, 08, 15, 22 e 29). */
    @Test
    void recorrenciaIndefinidaAparecePeloPeriodoNavegado() {
        seed();
        service.criar(serie("Conferir estoque", LocalDate.of(2027, 1, 18), RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.MONDAY), null));
        assertEquals(List.of(1, 8, 15, 22, 29).stream().map(d -> LocalDate.of(2027, 3, d)).toList(),
                datas(avulsos(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))));
        // e bem mais adiante, sem horizonte
        assertFalse(avulsos(LocalDate.of(2045, 6, 1), LocalDate.of(2045, 6, 30)).isEmpty());
    }

    /** CEN-NOVO-57 — recorrência inválida bloqueia: sem dia da semana, 1 ocorrência, 366 ocorrências, dias na mensal. */
    @Test
    void recorrenciaInvalidaBloqueia() {
        seed();
        LocalDate inicio = LocalDate.of(2027, 1, 18);
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, List.of(), null)));
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, null, null)));
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.MONDAY), 1)));
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.MONDAY), 366)));
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.MENSAL, List.of(DayOfWeek.MONDAY), 3)));
        assertThrows(BusinessException.class, () -> service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.NENHUMA, null, 5)));
        assertEquals(0, avulsos(inicio, inicio.plusDays(30)).size());
        service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.MONDAY), 365)); // limite aceito
        service.criar(serie("Pagar aluguel", inicio, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.MONDAY), 2));
    }

    /** CEN-NOVO-58 — concluir uma ocorrência: ela fica no dia (riscada, concluída) e as outras seguem pendentes. */
    @Test
    void concluirUmaOcorrencia() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 0);
        var d = service.criar(serie("Entregar encomendas", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 4));
        service.concluir(d.id(), terca);
        List<CompromissoResponse> r = avulsos(terca, terca.plusDays(10));
        assertEquals(SituacaoCompromisso.CONCLUIDO, r.stream().filter(c -> c.dataExibicao().equals(terca)).findFirst().orElseThrow().situacao());
        assertEquals(SituacaoCompromisso.PENDENTE, r.stream().filter(c -> c.dataExibicao().equals(terca.plusDays(2))).findFirst().orElseThrow().situacao());
        assertThrows(BusinessException.class, () -> service.concluir(d.id(), terca), "concluir de novo não vale: não volta");
    }

    /** CEN-NOVO-59 — cancelar a série inteira preserva a concluída e grava a observação nas pendentes. */
    @Test
    void cancelarASerieInteiraPreservaOConcluido() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        var d = service.criar(serie("Entregar encomendas", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 4));
        service.concluir(d.id(), terca);
        service.cancelar(d.id(), terca.plusDays(2), new CancelarCompromissoRequest(EscopoEdicaoCompromisso.SERIE, OBS_30));
        List<CompromissoResponse> r = avulsos(terca, terca.plusDays(40));
        assertEquals(4, r.size());
        assertEquals(SituacaoCompromisso.CONCLUIDO, r.get(0).situacao());
        assertTrue(r.subList(1, 4).stream().allMatch(c -> c.situacao() == SituacaoCompromisso.CANCELADO));
        assertEquals(OBS_30, service.detalhe(d.id(), terca.plusDays(7)).observacaoCancelamento());
    }

    /** Série cancelada também atinge a exceção pendente (a movida/editada só nela); a concluída e a cancelada ficam. */
    @Test
    void cancelarSerieAtingeAExcecaoPendente() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        var d = service.criar(serie("Entregar", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY), 3));
        service.editar(d.id(), terca.plusDays(7), new EditarCompromissoRequest(EscopoEdicaoCompromisso.OCORRENCIA, "Entregar", terca.plusDays(8), null, null, null));
        service.cancelar(d.id(), terca, new CancelarCompromissoRequest(EscopoEdicaoCompromisso.SERIE, OBS_30));
        assertEquals(SituacaoCompromisso.CANCELADO, service.detalhe(d.id(), terca.plusDays(7)).situacao());
        assertEquals(terca.plusDays(8), service.detalhe(d.id(), terca.plusDays(7)).data());
    }

    /** CEN-NOVO-60 — cancelar sem observação ou com menos de 30 caracteres bloqueia e o compromisso continua pendente. */
    @Test
    void cancelarSemObservacaoSuficienteBloqueia() {
        seed();
        var d = service.criar(simples("Retirar tecido", hoje.plusDays(2)));
        assertThrows(BusinessException.class, () -> service.cancelar(d.id(), d.dataOriginal(), new CancelarCompromissoRequest(null, "")));
        assertThrows(BusinessException.class, () -> service.cancelar(d.id(), d.dataOriginal(), new CancelarCompromissoRequest(null, "Mudou o dia")));
        assertThrows(BusinessException.class, () -> service.cancelar(d.id(), d.dataOriginal(), new CancelarCompromissoRequest(null, "x".repeat(501))));
        assertEquals(SituacaoCompromisso.PENDENTE, service.detalhe(d.id(), d.dataOriginal()).situacao());
        service.cancelar(d.id(), d.dataOriginal(), new CancelarCompromissoRequest(null, "x".repeat(30)));
        assertEquals(SituacaoCompromisso.CANCELADO, service.detalhe(d.id(), d.dataOriginal()).situacao());
    }

    /** CEN-NOVO-61 — editar só a ocorrência (move a data) ou a série inteira (título); a concluída mantém o título antigo. */
    @Test
    void editarSoAOcorrenciaOuASerieInteira() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        var d = service.criar(serie("Entregar encomendas", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY), 3));
        service.concluir(d.id(), terca);
        LocalDate segunda = terca.plusDays(7);
        service.editar(d.id(), segunda, new EditarCompromissoRequest(EscopoEdicaoCompromisso.OCORRENCIA, "Entregar encomendas", segunda.plusDays(1), null, null, null));
        List<CompromissoResponse> r = avulsos(terca, terca.plusDays(30));
        assertEquals(List.of(terca, segunda.plusDays(1), terca.plusDays(14)), datas(r));
        // a movida aparece só na nova data, mesmo olhando um período que contém só ela
        assertEquals(1, avulsos(segunda.plusDays(1), segunda.plusDays(1)).size());
        assertTrue(avulsos(segunda, segunda).isEmpty());
        service.editar(d.id(), terca.plusDays(14), new EditarCompromissoRequest(EscopoEdicaoCompromisso.SERIE, "Entregar pedidos", null, null, null, null));
        List<CompromissoResponse> depois = avulsos(terca, terca.plusDays(30));
        assertEquals("Entregar encomendas", depois.get(0).titulo(), "a concluída mantém o título antigo");
        assertEquals("Entregar pedidos", depois.get(1).titulo());
        assertEquals("Entregar pedidos", depois.get(2).titulo());
        assertEquals(List.of(terca, segunda.plusDays(1), terca.plusDays(14)), datas(depois), "a data não muda pela série inteira");
    }

    /** Vínculo por ocorrência: remover só numa ocorrência não afeta as outras; a série inteira só vale para as que herdam. */
    @Test
    void vinculosPorOcorrenciaEPelaSerie() {
        seed();
        Insumo linha = insumo("Linha 25");
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        var d = service.criar(new CompromissoUsuariaRequest("Comprar", terca, null, null, linha.getId(), RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY), 3));
        service.editar(d.id(), terca.plusDays(7), new EditarCompromissoRequest(EscopoEdicaoCompromisso.OCORRENCIA, "Comprar", terca.plusDays(7), null, null, null));
        assertNull(service.detalhe(d.id(), terca.plusDays(7)).insumo(), "removido só nesta ocorrência");
        assertEquals("Linha 25", service.detalhe(d.id(), terca).insumo().nome());
        service.concluir(d.id(), terca);
        service.editar(d.id(), terca.plusDays(14), new EditarCompromissoRequest(EscopoEdicaoCompromisso.SERIE, "Comprar", null, null, null, null));
        assertEquals("Linha 25", service.detalhe(d.id(), terca).insumo().nome(), "a concluída mantém o vínculo que tinha");
        assertNull(service.detalhe(d.id(), terca.plusDays(7)).insumo(), "a que tem vínculo próprio mantém o seu");
        assertNull(service.detalhe(d.id(), terca.plusDays(14)).insumo(), "a que herda segue o mestre, agora sem insumo");
    }

    /** CEN-NOVO-62 — pendente vencido fica atrasado e sobe para hoje; ao concluir, fica riscado em hoje. */
    @Test
    void vencidoFicaAtrasadoEConcluirRiscaEmHoje() {
        seed();
        var d = service.criar(simples("Levar encomenda à transportadora", hoje.minusDays(3)));
        CompromissoResponse c = avulsos(hoje, hoje).get(0);
        assertTrue(c.atrasado());
        assertEquals(3, c.diasAtraso());
        service.concluir(d.id(), d.dataOriginal());
        // concluído no passado não é mais atrasado: continua no dia original, riscado
        CompromissoResponse concluido = avulsos(hoje.minusDays(3), hoje.minusDays(3)).get(0);
        assertEquals(SituacaoCompromisso.CONCLUIDO, concluido.situacao());
        assertFalse(concluido.atrasado());
        assertTrue(avulsos(hoje, hoje).isEmpty());
    }

    /** RN-NOVA-6 — compromisso de outra conta: não encontrado; vínculo com orçamento ou insumo de outra conta também. */
    @Test
    void outraContaNaoEncontra() {
        seed();
        Insumo meu = insumo("Meu insumo");
        var d = service.criar(simples("Meu compromisso", hoje.plusDays(1)));
        Usuario outro = usuarioRepository.save(Usuario.builder().email("outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outro.getEmail(), null, List.of()));
        assertThrows(ResourceNotFoundException.class, () -> service.detalhe(d.id(), d.dataOriginal()));
        assertThrows(ResourceNotFoundException.class, () -> service.concluir(d.id(), d.dataOriginal()));
        assertThrows(ResourceNotFoundException.class, () -> service.criar(new CompromissoUsuariaRequest("x", hoje, null, null, meu.getId(), null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.criar(new CompromissoUsuariaRequest("x", hoje, null, UUID.randomUUID(), null, null, null, null)));
        assertTrue(avulsos(hoje, hoje.plusDays(5)).isEmpty());
    }

    /** Data que não é ocorrência da série: não encontrada. */
    @Test
    void dataQueNaoEOcorrenciaNaoEncontra() {
        seed();
        LocalDate terca = proximo(DayOfWeek.TUESDAY, 1);
        var d = service.criar(serie("Entregar", terca, RecorrenciaCompromisso.SEMANAL, List.of(DayOfWeek.TUESDAY), 3));
        assertThrows(ResourceNotFoundException.class, () -> service.concluir(d.id(), terca.plusDays(1)));
        assertThrows(ResourceNotFoundException.class, () -> service.concluir(d.id(), terca.plusDays(21)));
        assertThrows(ResourceNotFoundException.class, () -> service.detalhe(d.id(), LocalDate.of(1900, 1, 1)));
    }

    /** Vincular a um orçamento da conta. */
    @Test
    void vinculoComOrcamentoDaConta() {
        seed();
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Bolo").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(BigDecimal.TEN).permitirEstoqueNegativo(true).rendimento(BigDecimal.TEN).precoVenda(BigDecimal.TEN).build());
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(BigDecimal.TEN);
        item.setQuantidade(1);
        OrcamentoRequest o = new OrcamentoRequest();
        o.setClienteId(cliente.getId());
        o.setMetodoPagamento(MetodoPagamento.PIX);
        o.setTemPrazoProducao(false);
        o.setItens(List.of(item));
        UUID orcamentoId = orcamentoService.criar(o).getId();
        var d = service.criar(new CompromissoUsuariaRequest("Ligar para a Maria", hoje.plusDays(1), null, orcamentoId, null, null, null, null));
        assertEquals("Maria", d.orcamento().cliente());
        assertTrue(d.orcamento().identificador().startsWith("ORC-"));
    }
}
