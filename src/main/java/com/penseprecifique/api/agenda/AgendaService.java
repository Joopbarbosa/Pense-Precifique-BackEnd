package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.enums.OrigemCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.dto.response.agenda.AgendaResumoResponse;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (DT-NOVA-1, DT-NOVA-2) — junta os compromissos de todas as origens, aplica a regra única de atraso
 * (RN-NOVA-4) e ordena o dia (RN-NOVA-2). Nada é gravado aqui: cada {@link FonteCompromissos} calcula o seu a cada
 * consulta. A data de hoje é a de Brasília, pelo {@link Clock} da aplicação.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    static final LocalDate DATA_MAXIMA = LocalDate.of(2100, 12, 31);
    static final int MAXIMO_DIAS_PERIODO = 100;
    static final int TAMANHO_PAGINA_PADRAO = 20;
    static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final Clock clock;
    private final UsuarioRepository usuarioRepository;
    private final ObjectProvider<FonteCompromissos> fontes;

    /** Compromisso pronto para exibir, com a base de onde saiu (a ordenação usa campos que a resposta não expõe). */
    private record Item(CompromissoBase base, CompromissoResponse resposta) {}

    /**
     * RN-NOVA-2 — dentro do dia: atrasados primeiro (o mais antigo antes), depois com horário (do mais cedo ao mais
     * tarde), depois dia inteiro; empate por origem, título, número da origem, criação (avulsos) e data original da
     * ocorrência. A chave fecha o desempate, para o corte do "+N" da visão Mês ser sempre o mesmo.
     */
    private static final Comparator<Item> ORDEM_DO_DIA = Comparator
            .comparing((Item i) -> !i.resposta().atrasado())
            .thenComparing(i -> i.resposta().atrasado() ? i.resposta().dataOrigem() : LocalDate.MIN)
            .thenComparing(i -> i.resposta().horario() == null)
            .thenComparing(i -> i.resposta().horario(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(i -> i.resposta().origem())
            .thenComparing(i -> i.resposta().titulo().toLowerCase(java.util.Locale.ROOT))
            .thenComparingInt(i -> i.base().numeroOrigem())
            .thenComparing(i -> i.base().criadoEm(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(i -> i.base().dataOriginalOcorrencia(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(i -> i.resposta().chave());

    /** Compromissos cuja data de exibição cai no período, na ordem da Agenda. */
    @Transactional(readOnly = true)
    public List<CompromissoResponse> compromissos(LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        LocalDate hoje = LocalDate.now(clock);
        return coletar(getUsuarioIdAutenticado(), de, ate, hoje).stream()
                .filter(i -> !i.resposta().dataExibicao().isBefore(de) && !i.resposta().dataExibicao().isAfter(ate))
                .sorted(Comparator.comparing((Item i) -> i.resposta().dataExibicao()).thenComparing(ORDEM_DO_DIA))
                .map(Item::resposta)
                .toList();
    }

    /** RN-NOVA-3 — atrasados (de todas as origens, qualquer período) e pendentes de hoje, sem contar os atrasados. */
    @Transactional(readOnly = true)
    public AgendaResumoResponse resumo() {
        LocalDate hoje = LocalDate.now(clock);
        List<CompromissoResponse> todos = coletar(getUsuarioIdAutenticado(), hoje, hoje, hoje).stream()
                .map(Item::resposta).toList();
        long atrasados = todos.stream().filter(CompromissoResponse::atrasado).count();
        long deHoje = todos.stream()
                .filter(c -> !c.atrasado() && c.situacao() == SituacaoCompromisso.PENDENTE && c.dataOrigem().equals(hoje))
                .count();
        return new AgendaResumoResponse(atrasados, deHoje);
    }

    /** RN-NOVA-3 — lista de atrasados, do mais antigo para o mais recente, paginada (RN-086). */
    @Transactional(readOnly = true)
    public Page<CompromissoResponse> atrasados(Pageable pageable) {
        LocalDate hoje = LocalDate.now(clock);
        List<CompromissoResponse> atrasados = coletar(getUsuarioIdAutenticado(), hoje, hoje, hoje).stream()
                .filter(i -> i.resposta().atrasado())
                .sorted(Comparator.comparing((Item i) -> i.resposta().dataOrigem()).thenComparing(ORDEM_DO_DIA))
                .map(Item::resposta)
                .toList();
        int tamanho = Math.min(Math.max(pageable.getPageSize(), 1), TAMANHO_PAGINA_MAXIMO);
        int pagina = Math.max(pageable.getPageNumber(), 0);
        long inicio = (long) pagina * tamanho;
        List<CompromissoResponse> fatia = inicio >= atrasados.size()
                ? List.of()
                : atrasados.subList((int) inicio, (int) Math.min(inicio + tamanho, atrasados.size()));
        return new PageImpl<>(fatia, PageRequest.of(pagina, tamanho), atrasados.size());
    }

    private List<Item> coletar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<Item> resultado = new ArrayList<>();
        fontes.orderedStream().forEach(fonte -> {
            for (CompromissoBase base : fonte.listar(usuarioId, de, ate, hoje)) {
                resultado.add(new Item(base, paraResposta(base, hoje)));
            }
        });
        return resultado;
    }

    private CompromissoResponse paraResposta(CompromissoBase base, LocalDate hoje) {
        boolean atrasado = base.pendente() && base.data().isBefore(hoje);
        int diasAtraso = atrasado ? (int) ChronoUnit.DAYS.between(base.data(), hoje) : 0;
        OrigemCompromisso origem = base.tipo().origem();
        return new CompromissoResponse(
                base.chave(), base.tipo(), origem, base.situacao(), base.titulo(), base.data(), base.dataOriginalOcorrencia(),
                atrasado ? hoje : base.data(), base.horario(), base.horario() == null, atrasado, diasAtraso,
                base.marcacoes() == null ? List.of() : base.marcacoes(),
                base.avisos() == null ? List.of() : base.avisos(),
                base.referenciaId(), base.identificador(), base.valor());
    }

    /** DT-NOVA-2 — `de` e `ate` obrigatórios, `ate` não anterior a `de`, no máximo 100 dias, entre 2000 e 2100. */
    void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de == null || ate == null) {
            throw new BusinessException("Informe o período: data inicial e data final.");
        }
        if (ate.isBefore(de)) {
            throw new BusinessException("A data final não pode ser anterior à data inicial.");
        }
        if (de.isBefore(DATA_MINIMA) || ate.isAfter(DATA_MAXIMA)) {
            throw new BusinessException("O período deve estar entre 01/01/2000 e 31/12/2100.");
        }
        if (ChronoUnit.DAYS.between(de, ate) + 1 > MAXIMO_DIAS_PERIODO) {
            throw new BusinessException("O período pode ter no máximo " + MAXIMO_DIAS_PERIODO + " dias.");
        }
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
