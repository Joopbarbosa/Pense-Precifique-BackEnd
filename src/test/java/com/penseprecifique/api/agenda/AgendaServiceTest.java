package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.OrigemCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.dto.response.agenda.AgendaResumoResponse;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V0.18.0 (#1034) — regra única de atraso (RN-NOVA-4), ordem do dia (RN-NOVA-2), faixa de atrasados e de hoje
 * (RN-NOVA-3) e limites do período (DT-NOVA-2). Hoje = 12/10/2026 (segunda-feira).
 */
class AgendaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 12);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final List<CompromissoBase> base = new ArrayList<>();
    private AgendaService service;

    @BeforeEach
    void preparar() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).email("agenda@teste.com").build();
        when(usuarios.findByEmailAndDeletedAtIsNull("agenda@teste.com")).thenReturn(Optional.of(usuario));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("agenda@teste.com", null, List.of()));
        @SuppressWarnings("unchecked")
        ObjectProvider<FonteCompromissos> fontes = mock(ObjectProvider.class);
        FonteCompromissos fonte = (u, de, ate, hoje) -> new ArrayList<>(base);
        when(fontes.orderedStream()).thenAnswer(i -> Stream.of(fonte));
        service = new AgendaService(clock, usuarios, fontes);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private CompromissoBase c(String chave, TipoCompromisso tipo, String titulo, LocalDate data, LocalTime horario, int numero) {
        return new CompromissoBase(chave, tipo, SituacaoCompromisso.PENDENTE, titulo, data, horario, List.of(), List.of(),
                UUID.randomUUID(), "X-" + numero, numero, null, Instant.parse("2026-10-01T10:00:00Z"), null, false);
    }

    /** CEN-NOVO-5/RN-NOVA-4 — pendente com data anterior a hoje sobe para hoje, com N dias de atraso; hoje não é atraso. */
    @Test
    void atrasoSobeParaHojeComODiaDeAtraso() {
        base.add(c("a", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega", LocalDate.of(2026, 10, 9), null, 1));
        base.add(c("b", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega de hoje", HOJE, null, 2));
        List<CompromissoResponse> r = service.compromissos(HOJE, HOJE);
        assertEquals(2, r.size());
        CompromissoResponse atrasado = r.stream().filter(x -> x.chave().equals("a")).findFirst().orElseThrow();
        assertTrue(atrasado.atrasado());
        assertEquals(3, atrasado.diasAtraso());
        assertEquals(LocalDate.of(2026, 10, 9), atrasado.dataExibicao());
        assertEquals(HOJE, atrasado.dataFim());
        assertEquals(LocalDate.of(2026, 10, 9), atrasado.dataOrigem());
        CompromissoResponse deHoje = r.stream().filter(x -> x.chave().equals("b")).findFirst().orElseThrow();
        assertFalse(deHoje.atrasado());
        assertEquals(0, deHoje.diasAtraso());
    }

    /** RN-NOVA-4 v2 — o atrasado é um só compromisso, do dia original até hoje; aparece em todo período que toca esse trecho. */
    @Test
    void atrasadoApareceEmTodoPeriodoQueTocaDaOrigemAteHoje() {
        base.add(c("a", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega", LocalDate.of(2026, 10, 9), null, 1));
        assertEquals(1, service.compromissos(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)).size(), "período de hoje");
        assertEquals(1, service.compromissos(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)).size(), "período que contém a origem");
        assertEquals(1, service.compromissos(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)).size(), "dia do meio");
        assertEquals(0, service.compromissos(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8)).size(), "antes da origem");
        assertEquals(0, service.compromissos(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 18)).size(), "depois de hoje");
        CompromissoResponse r = service.compromissos(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)).get(0);
        assertEquals(LocalDate.of(2026, 10, 9), r.dataExibicao());
        assertEquals(HOJE, r.dataFim());
    }

    /** CEN-NOVO-83 — atrasado de 90 dias: um compromisso só, em qualquer janela de 100 dias que o toque. */
    @Test
    void atrasadoAntigoContaUmaVezEApareceNaJanela() {
        base.add(c("velho", TipoCompromisso.AVULSO, "Antigo", HOJE.minusDays(90), null, 0));
        assertEquals(1, service.compromissos(HOJE.minusDays(30), HOJE.minusDays(1)).size());
        assertEquals(new AgendaResumoResponse(1, 0), service.resumo());
        assertEquals(1, service.atrasados(PageRequest.of(0, 20)).getTotalElements());
        assertEquals(90, service.compromissos(HOJE, HOJE).get(0).diasAtraso());
    }

    /** CEN-NOVO-77, 78, 79 — hoje às 12:00 (Brasília): horário já passado é atraso por horário; igual ou futuro não é. */
    @Test
    void atrasoPorHorarioNoMesmoDia() {
        base.add(c("cedo", TipoCompromisso.AVULSO, "Ligar", HOJE, LocalTime.of(5, 0), 0));
        base.add(c("meia", TipoCompromisso.AVULSO, "Buscar", HOJE, LocalTime.of(11, 20), 0));
        base.add(c("agora", TipoCompromisso.AVULSO, "Reunião", HOJE, LocalTime.of(12, 0), 0));
        base.add(c("depois", TipoCompromisso.AVULSO, "Retirar", HOJE, LocalTime.of(12, 1), 0));
        base.add(c("diainteiro", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega", HOJE, null, 3));
        List<CompromissoResponse> r = service.compromissos(HOJE, HOJE);
        CompromissoResponse cedo = r.stream().filter(x -> x.chave().equals("cedo")).findFirst().orElseThrow();
        assertTrue(cedo.atrasado());
        assertEquals(0, cedo.diasAtraso());
        assertEquals(420, cedo.minutosAtraso());
        assertEquals(HOJE, cedo.dataExibicao());
        assertEquals(HOJE, cedo.dataFim());
        assertEquals(40, r.stream().filter(x -> x.chave().equals("meia")).findFirst().orElseThrow().minutosAtraso());
        for (String chave : List.of("agora", "depois", "diainteiro")) {
            CompromissoResponse x = r.stream().filter(y -> y.chave().equals(chave)).findFirst().orElseThrow();
            assertFalse(x.atrasado(), chave);
            assertEquals(0, x.minutosAtraso(), chave);
        }
        assertEquals(new AgendaResumoResponse(2, 3), service.resumo(), "atrasados saem da contagem de hoje");
    }

    /** O atraso por horário só vale para pendente: o concluído de hoje com horário passado não é atrasado. */
    @Test
    void concluidoDeHojeComHorarioPassadoNaoEAtrasado() {
        base.add(new CompromissoBase("feito", TipoCompromisso.AVULSO, SituacaoCompromisso.CONCLUIDO, "Feito", HOJE, LocalTime.of(5, 0),
                List.of(), List.of(), UUID.randomUUID(), null, 0, null, Instant.parse("2026-10-01T10:00:00Z"), null, false));
        assertFalse(service.compromissos(HOJE, HOJE).get(0).atrasado());
    }

    /** RN-NOVA-2 — atrasados, depois com horário (cedo primeiro), depois dia inteiro; empate por origem e título. */
    @Test
    void ordemDoDia() {
        base.add(c("dia-inteiro-compra", TipoCompromisso.RECEBIMENTO_COMPRA, "Fio", HOJE, null, 3));
        base.add(c("dia-inteiro-orc-b", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega B", HOJE, null, 5));
        base.add(c("dia-inteiro-orc-a", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega A", HOJE, null, 6));
        base.add(c("tarde", TipoCompromisso.AVULSO, "Reunião", HOJE, LocalTime.of(15, 0), 0));
        base.add(c("cedo", TipoCompromisso.AVULSO, "Dentista", HOJE, LocalTime.of(9, 30), 0));
        base.add(c("atrasado", TipoCompromisso.INICIO_PRODUCAO, "Início", LocalDate.of(2026, 10, 10), null, 8));
        List<String> chaves = service.compromissos(HOJE, HOJE).stream().map(CompromissoResponse::chave).toList();
        assertEquals(List.of("atrasado", "cedo", "tarde", "dia-inteiro-orc-a", "dia-inteiro-orc-b", "dia-inteiro-compra"), chaves);
    }

    /** Desempates finais: mesmo título e origem → número da origem; avulsos iguais → o criado primeiro. */
    @Test
    void desempateFinalPeloNumeroDaOrigemEPelaCriacao() {
        base.add(c("orc-9", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega", HOJE, null, 9));
        base.add(c("orc-2", TipoCompromisso.ENTREGA_ESTIMADA, "Entrega", HOJE, null, 2));
        CompromissoBase novo = new CompromissoBase("avulso-novo", TipoCompromisso.AVULSO, SituacaoCompromisso.PENDENTE, "Ligar", HOJE, null,
                List.of(), List.of(), UUID.randomUUID(), null, 0, null, Instant.parse("2026-10-05T10:00:00Z"), null, false);
        CompromissoBase velho = new CompromissoBase("avulso-velho", TipoCompromisso.AVULSO, SituacaoCompromisso.PENDENTE, "Ligar", HOJE, null,
                List.of(), List.of(), UUID.randomUUID(), null, 0, null, Instant.parse("2026-10-01T10:00:00Z"), null, false);
        base.add(novo);
        base.add(velho);
        List<String> chaves = service.compromissos(HOJE, HOJE).stream().map(CompromissoResponse::chave).toList();
        assertEquals(List.of("orc-2", "orc-9", "avulso-velho", "avulso-novo"), chaves);
    }

    /** RN-NOVA-3 — atrasados contam de qualquer data; hoje conta só pendentes com a data de hoje, sem os atrasados. */
    @Test
    void resumoContaAtrasadosEHoje() {
        base.add(c("a1", TipoCompromisso.ENTREGA_ESTIMADA, "A1", LocalDate.of(2026, 9, 1), null, 1));
        base.add(c("a2", TipoCompromisso.RECEBIMENTO_COMPRA, "A2", LocalDate.of(2026, 10, 11), null, 2));
        base.add(c("h1", TipoCompromisso.AVULSO, "H1", HOJE, LocalTime.of(14, 0), 0));
        base.add(c("h2", TipoCompromisso.ENTREGA_ESTIMADA, "H2", HOJE, null, 3));
        base.add(new CompromissoBase("concluido", TipoCompromisso.AVULSO, SituacaoCompromisso.CONCLUIDO, "Feito", HOJE, null,
                List.of(), List.of(), UUID.randomUUID(), null, 0, null, Instant.parse("2026-10-01T10:00:00Z"), null, false));
        assertEquals(new AgendaResumoResponse(2, 2), service.resumo());
    }

    /** RN-NOVA-4 — compromisso da usuária concluído ou cancelado no passado não é atrasado. */
    @Test
    void concluidoNoPassadoNaoEAtrasado() {
        base.add(new CompromissoBase("c", TipoCompromisso.AVULSO, SituacaoCompromisso.CONCLUIDO, "Feito", LocalDate.of(2026, 10, 1), null,
                List.of(), List.of(), UUID.randomUUID(), null, 0, null, Instant.parse("2026-09-01T10:00:00Z"), null, false));
        assertEquals(new AgendaResumoResponse(0, 0), service.resumo());
    }

    /** RN-NOVA-3 — lista de atrasados do mais antigo para o mais recente, paginada de 20 em 20. */
    @Test
    void atrasadosPaginadosDoMaisAntigoAoMaisRecente() {
        for (int i = 0; i < 25; i++) {
            base.add(c("a" + i, TipoCompromisso.RECEBIMENTO_COMPRA, "Compra " + i, HOJE.minusDays(i + 1), null, i));
        }
        Page<CompromissoResponse> p0 = service.atrasados(PageRequest.of(0, 20));
        assertEquals(25, p0.getTotalElements());
        assertEquals(20, p0.getContent().size());
        assertEquals("a24", p0.getContent().get(0).chave());
        assertEquals("a5", p0.getContent().get(19).chave());
        Page<CompromissoResponse> p1 = service.atrasados(PageRequest.of(1, 20));
        assertEquals(5, p1.getContent().size());
        assertEquals("a0", p1.getContent().get(4).chave());
        assertTrue(service.atrasados(PageRequest.of(9, 20)).getContent().isEmpty());
    }

    /** DT-NOVA-2 — limites do período (entrada hostil): sempre 400, nunca 500. */
    @Test
    void periodoInvalidoEBloqueado() {
        assertThrows(BusinessException.class, () -> service.compromissos(null, HOJE));
        assertThrows(BusinessException.class, () -> service.compromissos(HOJE, null));
        assertThrows(BusinessException.class, () -> service.compromissos(HOJE, HOJE.minusDays(1)));
        assertThrows(BusinessException.class, () -> service.compromissos(LocalDate.of(1999, 12, 31), HOJE));
        assertThrows(BusinessException.class, () -> service.compromissos(HOJE, LocalDate.of(2101, 1, 1)));
        assertThrows(BusinessException.class, () -> service.compromissos(HOJE, HOJE.plusDays(100)));
        assertEquals(0, service.compromissos(HOJE, HOJE.plusDays(99)).size());
    }
}
