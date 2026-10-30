package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.dto.request.agenda.MoverCompromissoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.MovimentoCompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1187, RN-NOVA-43 e 44) — simular e mover a entrega estimada, a validade e o sinal do orçamento e o início e o
 * término da produção pela Agenda. "Simular" nunca grava; "mover" grava só sem bloqueio; estados já cumpridos são recusados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgendaMovimentoOrcamentoProducaoIT {

    @Autowired AgendaMovimentoService service;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente cliente;
    private LocalDate hoje;
    private int numeroOrc = 1;
    private int numeroPrd = 1;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder().email("agenda-mov-op-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        entrarComo(usuario);
        cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ativa(true).build());
    }

    private void entrarComo(Usuario u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    /** Orçamento aprovado hoje, sem sinal, com prazo de {@code prazo} dias: entrega estimada = hoje + prazo. */
    private Orcamento aprovado(StatusOrcamento status, int prazo) {
        return orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numeroOrc++).status(status)
                .total(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00")).prazoProducaoDias(prazo)
                .dataAprovacao(hoje.atTime(9, 30)).build());
    }

    private Orcamento rascunhoComValidade(StatusOrcamento status, LocalDate validade) {
        return orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numeroOrc++).status(status)
                .total(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00"))
                .dataValidade(validade == null ? null : validade.atTime(18, 45)).build());
    }

    private Orcamento aguardandoSinal(LocalDate sinalAte) {
        return orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numeroOrc++)
                .status(StatusOrcamento.AGUARDANDO_SINAL).sinalAtivo(true).sinalAte(sinalAte)
                .total(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00")).build());
    }

    private Producao producao(EstadoProducao estado, Integer inicioEmDias, Integer terminoEmDias) {
        return producaoRepository.save(Producao.builder().usuario(usuario).numero(numeroPrd++).estado(estado)
                .dataInicio(inicioEmDias == null ? null : hoje.plusDays(inicioEmDias))
                .dataTerminoPrevista(terminoEmDias == null ? null : hoje.plusDays(terminoEmDias)).build());
    }

    private MoverCompromissoRequest pedido(TipoCompromisso tipo, UUID id, LocalDate data) {
        return new MoverCompromissoRequest(tipo, id, data);
    }

    private Orcamento recarregar(Orcamento o) {
        return orcamentoRepository.findById(o.getId()).orElseThrow();
    }

    private Producao recarregar(Producao p) {
        return producaoRepository.findById(p.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------------------------- entrega estimada

    @Test
    void entregaSimularNaoGravaEMoverAjustaOPrazo() {
        seed();
        Orcamento o = aprovado(StatusOrcamento.EM_PRODUCAO, 5);
        LocalDate nova = hoje.plusDays(12);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), nova));
        assertEquals(hoje.plusDays(5), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertTrue(s.avisos().isEmpty());
        assertTrue(s.efeito().contains("de 5 para 12 dias"));
        assertEquals(5, recarregar(o).getPrazoProducaoDias());

        service.mover(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), nova));
        assertEquals(12, recarregar(o).getPrazoProducaoDias());
    }

    @Test
    void entregaNoDiaDaPartidaOuAntesBloqueia() {
        seed();
        Orcamento o = aprovado(StatusOrcamento.EM_PRODUCAO, 5);
        MoverCompromissoRequest noDia = pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje);
        assertNotNull(service.simular(noDia).bloqueio());
        assertThrows(BusinessException.class, () -> service.mover(noDia));
        MoverCompromissoRequest antes = pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje.minusDays(30));
        assertNotNull(service.simular(antes).bloqueio());
        assertThrows(BusinessException.class, () -> service.mover(antes));
        assertEquals(5, recarregar(o).getPrazoProducaoDias());
    }

    @Test
    void entregaAntesDoTerminoDaProducaoAvisaEPermite() {
        seed();
        Orcamento o = aprovado(StatusOrcamento.EM_PRODUCAO, 10);
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 1, 8);
        orcamentoProducaoRepository.save(OrcamentoProducao.builder().orcamento(o).producao(p).build());
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje.plusDays(4)));
        assertNull(s.bloqueio());
        assertEquals(1, s.avisos().size());
        assertTrue(s.avisos().get(0).startsWith("A entrega fica antes da produção"));
        service.mover(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje.plusDays(4)));
        assertEquals(4, recarregar(o).getPrazoProducaoDias());
    }

    @Test
    void entregaSemEstimativaOuJaEntregueOuCanceladaEhRecusada() {
        seed();
        Orcamento semPrazo = orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numeroOrc++)
                .status(StatusOrcamento.EM_PRODUCAO).total(BigDecimal.TEN).subtotal(BigDecimal.TEN).build());
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, semPrazo.getId(), hoje.plusDays(5))));
        for (StatusOrcamento status : List.of(StatusOrcamento.ENTREGUE, StatusOrcamento.CANCELADO)) {
            Orcamento o = aprovado(status, 5);
            assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje.plusDays(9))));
            assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), hoje.plusDays(9))));
            assertEquals(5, recarregar(o).getPrazoProducaoDias());
        }
    }

    // ------------------------------------------------------------------------------------- validade

    @Test
    void validadeSimularNaoGravaEMoverGravaMantendoAHora() {
        seed();
        Orcamento o = rascunhoComValidade(StatusOrcamento.ENVIADO, hoje.plusDays(3));
        LocalDate nova = hoje.plusDays(20);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, o.getId(), nova));
        assertEquals(hoje.plusDays(3), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertEquals(hoje.plusDays(3), recarregar(o).getDataValidade().toLocalDate());

        service.mover(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, o.getId(), nova));
        LocalDateTime gravada = recarregar(o).getDataValidade();
        assertEquals(nova, gravada.toLocalDate());
        assertEquals(LocalTime.of(18, 45), gravada.toLocalTime());
    }

    @Test
    void validadeNoPassadoSoAvisa() {
        seed();
        Orcamento o = rascunhoComValidade(StatusOrcamento.RASCUNHO, hoje.plusDays(3));
        LocalDate passada = hoje.minusDays(1);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, o.getId(), passada));
        assertNull(s.bloqueio());
        assertEquals(List.of("A data já passou."), s.avisos());
        service.mover(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, o.getId(), passada));
        assertEquals(passada, recarregar(o).getDataValidade().toLocalDate());
    }

    @Test
    void validadeDeOrcamentoAprovadoOuSemValidadeEhRecusada() {
        seed();
        Orcamento aprovado = rascunhoComValidade(StatusOrcamento.APROVADO, hoje.plusDays(3));
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, aprovado.getId(), hoje.plusDays(9))));
        Orcamento semValidade = rascunhoComValidade(StatusOrcamento.ENVIADO, null);
        assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, semValidade.getId(), hoje.plusDays(9))));
        assertEquals(hoje.plusDays(3), recarregar(aprovado).getDataValidade().toLocalDate());
    }

    // ------------------------------------------------------------------------------------- sinal

    @Test
    void sinalSimularNaoGravaEMoverGrava() {
        seed();
        Orcamento o = aguardandoSinal(hoje.plusDays(2));
        LocalDate nova = hoje.plusDays(9);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), nova));
        assertEquals(hoje.plusDays(2), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertEquals(hoje.plusDays(2), recarregar(o).getSinalAte());

        service.mover(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), nova));
        assertEquals(nova, recarregar(o).getSinalAte());
    }

    @Test
    void sinalAnteriorAHojeBloqueiaEHojeEhPermitido() {
        seed();
        Orcamento o = aguardandoSinal(hoje.plusDays(2));
        MoverCompromissoRequest ontem = pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), hoje.minusDays(1));
        assertEquals("A data do sinal não pode ser anterior a hoje", service.simular(ontem).bloqueio());
        assertThrows(BusinessException.class, () -> service.mover(ontem));
        assertEquals(hoje.plusDays(2), recarregar(o).getSinalAte());
        service.mover(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), hoje));
        assertEquals(hoje, recarregar(o).getSinalAte());
    }

    @Test
    void sinalForaDoAguardandoSinalEhRecusado() {
        seed();
        Orcamento o = orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numeroOrc++)
                .status(StatusOrcamento.SINAL_PAGO).sinalAtivo(true).sinalAte(hoje.plusDays(2))
                .total(BigDecimal.TEN).subtotal(BigDecimal.TEN).build());
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), hoje.plusDays(5))));
        assertEquals(hoje.plusDays(2), recarregar(o).getSinalAte());
    }

    // ------------------------------------------------------------------------------------- início da produção

    @Test
    void inicioSimularNaoGravaEMoverGrava() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 2, 6);
        LocalDate nova = hoje.plusDays(4);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), nova));
        assertEquals(hoje.plusDays(2), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertTrue(s.avisos().isEmpty());
        assertEquals(hoje.plusDays(2), recarregar(p).getDataInicio());

        service.mover(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), nova));
        assertEquals(nova, recarregar(p).getDataInicio());
    }

    @Test
    void inicioDepoisDoTerminoOuNoPassadoSoAvisa() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 2, 6);
        MovimentoCompromissoResponse depois = service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), hoje.plusDays(9)));
        assertNull(depois.bloqueio());
        assertEquals(1, depois.avisos().size());
        assertTrue(depois.avisos().get(0).startsWith("O início fica depois do término previsto"));
        MovimentoCompromissoResponse passado = service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), hoje.minusDays(2)));
        assertNull(passado.bloqueio());
        assertEquals(List.of("A data já passou."), passado.avisos());
        service.mover(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), hoje.minusDays(2)));
        assertEquals(hoje.minusDays(2), recarregar(p).getDataInicio());
    }

    @Test
    void inicioDeProducaoQueJaComecouEhRecusado() {
        seed();
        Producao andamento = producao(EstadoProducao.EM_ANDAMENTO, 0, 4);
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, andamento.getId(), hoje.plusDays(1))));
        Producao semData = producao(EstadoProducao.AGUARDANDO_INICIO, null, 4);
        assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.INICIO_PRODUCAO, semData.getId(), hoje.plusDays(1))));
        Producao travadaQueJaRodou = producao(EstadoProducao.TRAVADA, 1, 4);
        historicoRepository.save(HistoricoStatusProducao.builder().producao(travadaQueJaRodou).statusAnterior(EstadoProducao.AGUARDANDO_INICIO)
                .statusNovo(EstadoProducao.EM_ANDAMENTO).dataTransicao(LocalDateTime.now()).origem(OrigemHistoricoStatus.USUARIO).build());
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, travadaQueJaRodou.getId(), hoje.plusDays(2))));
        Producao travadaNova = producao(EstadoProducao.TRAVADA, 1, 4);
        service.mover(pedido(TipoCompromisso.INICIO_PRODUCAO, travadaNova.getId(), hoje.plusDays(2)));
        assertEquals(hoje.plusDays(2), recarregar(travadaNova).getDataInicio());
        assertEquals(hoje, recarregar(andamento).getDataInicio());
    }

    // ------------------------------------------------------------------------------------- término da produção

    @Test
    void terminoSimularNaoGravaEMoverGrava() {
        seed();
        Producao p = producao(EstadoProducao.EM_ANDAMENTO, 0, 4);
        LocalDate nova = hoje.plusDays(8);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), nova));
        assertEquals(hoje.plusDays(4), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertEquals(hoje.plusDays(4), recarregar(p).getDataTerminoPrevista());

        service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), nova));
        assertEquals(nova, recarregar(p).getDataTerminoPrevista());
    }

    @Test
    void terminoAntesDoInicioOuNoPassadoSoAvisa() {
        seed();
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 3, 6);
        MovimentoCompromissoResponse antes = service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), hoje.plusDays(1)));
        assertNull(antes.bloqueio());
        assertEquals(1, antes.avisos().size());
        assertTrue(antes.avisos().get(0).startsWith("O término fica antes do início da produção"));
        MovimentoCompromissoResponse passado = service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), hoje.minusDays(1)));
        assertNull(passado.bloqueio());
        assertTrue(passado.avisos().contains("A data já passou."));
        service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), hoje.minusDays(1)));
        assertEquals(hoje.minusDays(1), recarregar(p).getDataTerminoPrevista());
    }

    @Test
    void terminoDeProducaoFinalizadaOuSemTerminoEhRecusado() {
        seed();
        Producao finalizada = producao(EstadoProducao.FINALIZADA, 0, 2);
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, finalizada.getId(), hoje.plusDays(5))));
        assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, finalizada.getId(), hoje.plusDays(5))));
        Producao semTermino = producao(EstadoProducao.AGUARDANDO_INICIO, 1, null);
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, semTermino.getId(), hoje.plusDays(5))));
        assertEquals(hoje.plusDays(2), recarregar(finalizada).getDataTerminoPrevista());
    }

    @Test
    void terminoDeProducaoCanceladaOuNaoRealizadaEhRecusadoESemEfeito() {
        seed();
        for (EstadoProducao estado : List.of(EstadoProducao.CANCELADA, EstadoProducao.NAO_REALIZADA)) {
            Producao p = producao(estado, 0, 2);
            assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), hoje.plusDays(5))));
            assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), hoje.plusDays(5))));
            assertEquals(hoje.plusDays(2), recarregar(p).getDataTerminoPrevista());
        }
    }

    // ------------------------------------------------------------------------------------- entrada hostil

    @Test
    void idDeOutraContaOuInexistenteDa404SemGravar() {
        seed();
        Orcamento o = aguardandoSinal(hoje.plusDays(2));
        Orcamento v = rascunhoComValidade(StatusOrcamento.ENVIADO, hoje.plusDays(3));
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 2, 6);
        Usuario outra = usuarioRepository.save(Usuario.builder().email("agenda-mov-op-outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        entrarComo(outra);
        LocalDate nova = hoje.plusDays(15);
        assertThrows(ResourceNotFoundException.class, () -> service.mover(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), nova)));
        assertThrows(ResourceNotFoundException.class, () -> service.mover(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, v.getId(), nova)));
        assertThrows(ResourceNotFoundException.class, () -> service.mover(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), nova)));
        assertThrows(ResourceNotFoundException.class, () -> service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), nova)));
        assertThrows(ResourceNotFoundException.class, () -> service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), nova)));
        assertThrows(ResourceNotFoundException.class, () -> service.simular(pedido(TipoCompromisso.INICIO_PRODUCAO, UUID.randomUUID(), nova)));
        assertEquals(hoje.plusDays(2), recarregar(o).getSinalAte());
        assertEquals(hoje.plusDays(3), recarregar(v).getDataValidade().toLocalDate());
        assertEquals(hoje.plusDays(2), recarregar(p).getDataInicio());
        assertEquals(hoje.plusDays(6), recarregar(p).getDataTerminoPrevista());
    }

    @Test
    void dataExtremaEhRecusadaEmTodosOsTipos() {
        seed();
        Orcamento o = aguardandoSinal(hoje.plusDays(2));
        Producao p = producao(EstadoProducao.AGUARDANDO_INICIO, 2, 6);
        for (LocalDate extrema : List.of(LocalDate.of(1999, 12, 31), LocalDate.of(2101, 1, 1))) {
            assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, o.getId(), extrema)));
            assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.VALIDADE_ORCAMENTO, o.getId(), extrema)));
            assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.SINAL_A_RECEBER, o.getId(), extrema)));
            assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.INICIO_PRODUCAO, p.getId(), extrema)));
            assertThrows(BusinessException.class, () -> service.mover(pedido(TipoCompromisso.TERMINO_PRODUCAO, p.getId(), extrema)));
        }
        assertEquals(hoje.plusDays(2), recarregar(o).getSinalAte());
        assertEquals(hoje.plusDays(2), recarregar(p).getDataInicio());
    }
}
