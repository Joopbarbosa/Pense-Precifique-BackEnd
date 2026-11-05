package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * V0.19.0 (#1176, DT-NOVA-6, RN-NOVA-17 a 23, UC-NOVO-3) — resumo do mês, projeção, próximos vencimentos e maiores despesas
 * por categoria. Somente leitura: nada é gravado, nada é semeado (DT-NOVA-7) e o Caixa só é lido. Tudo é calculado na hora, em
 * consultas agregadas, com o relógio de Brasília (DT-NOVA-2).
 */
@Service
@RequiredArgsConstructor
public class ResumoFinanceiroService {

    static final int MAX_LINHAS_PROXIMOS = 10;
    static final int DIAS_PROXIMOS = 7;
    static final int[] MARCOS = {30, 60, 90};
    static final int MAX_CATEGORIAS = 5;
    static final int TAMANHO_MAXIMO_PAGINA = 100;
    static final String MSG_SEM_MOVIMENTOS = "Sem movimentos neste mês.";
    static final String MSG_SALDO_INICIAL = "Informe o saldo inicial ao abrir o caixa.";
    static final String MSG_SEM_VENCIMENTOS = "Nenhum vencimento nos próximos 7 dias.";
    static final String MSG_GRAFICO_VAZIO = "Sem movimentos para mostrar.";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final UsuarioRepository usuarioRepository;
    private final ResumoFinanceiroRepository repo;
    private final ParcelaRepository parcelaRepository;
    private final Clock relogioBrasilia;

    @Transactional(readOnly = true)
    public ResumoFinanceiroResponse resumo(String mesTexto) {
        UUID usuarioId = usuarioId();
        LocalDate hoje = LocalDate.now(relogioBrasilia);
        YearMonth mes = interpretarMes(mesTexto, hoje);
        LocalDate de = mes.atDay(1);
        LocalDate ate = mes.atEndOfMonth();

        Realizado r = realizado(usuarioId, mes);
        BigDecimal saldoFinal = r.saldoInicial.add(r.entradas).subtract(r.saidas);

        BigDecimal pagarAberto = money(parcelaRepository.totalEmAberto(usuarioId, TipoLancamento.A_PAGAR, de, ate, true, hoje));
        BigDecimal pagarVencido = money(parcelaRepository.totalVencido(usuarioId, TipoLancamento.A_PAGAR, de, ate, true, hoje));
        BigDecimal receberAberto = money(parcelaRepository.totalEmAberto(usuarioId, TipoLancamento.A_RECEBER, de, ate, true, hoje));
        BigDecimal receberVencido = money(parcelaRepository.totalVencido(usuarioId, TipoLancamento.A_RECEBER, de, ate, true, hoje));

        BigDecimal previstoEntradas = money(repo.somaPrevista(usuarioId, TipoLancamento.A_RECEBER, de, ate));
        BigDecimal previstoSaidas = money(repo.somaPrevista(usuarioId, TipoLancamento.A_PAGAR, de, ate));

        boolean semMovimentos = r.entradas.signum() == 0 && r.saidas.signum() == 0 && r.saldoInicial.signum() == 0
                && pagarAberto.signum() == 0 && receberAberto.signum() == 0
                && previstoEntradas.signum() == 0 && previstoSaidas.signum() == 0;

        return new ResumoFinanceiroResponse(mes.toString(), de, ate, hoje,
                r.saldoInicial, r.saldoInformado, r.saldoInformado ? null : MSG_SALDO_INICIAL,
                r.entradas, r.saidas, r.entradas.subtract(r.saidas), saldoFinal,
                new ResumoFinanceiroResponse.Pendencia(receberAberto, receberVencido),
                new ResumoFinanceiroResponse.Pendencia(pagarAberto, pagarVencido),
                new ResumoFinanceiroResponse.PrevistoRealizado(previstoEntradas, r.entradas, previstoSaidas, r.saidas),
                semMovimentos, semMovimentos ? MSG_SEM_MOVIMENTOS : null,
                proximos(usuarioId, hoje),
                semanas(usuarioId, mes, r),
                projecao(usuarioId, hoje),
                maioresDespesas(usuarioId, de, ate));
    }

    /** Lista completa de «Ver todas»: vencidas e próximos 7 dias, pagar e receber juntos, mesma ordem do resumo. */
    @Transactional(readOnly = true)
    public ListaVencimentosResponse listarVencimentos(Pageable pageable) {
        UUID usuarioId = usuarioId();
        LocalDate hoje = LocalDate.now(relogioBrasilia);
        int tamanho = Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA));
        int pagina = Math.max(0, Math.min(pageable.getPageNumber(), 100_000));
        Page<Parcela> p = repo.proximosVencimentos(usuarioId, hoje.plusDays(DIAS_PROXIMOS), PageRequest.of(pagina, tamanho));
        return new ListaVencimentosResponse(paraRespostas(p.getContent(), hoje), p.getTotalElements(), p.getNumber(), p.getSize());
    }

    // ---- realizado (RN-NOVA-18 e 19) ----

    private record Realizado(BigDecimal saldoInicial, boolean saldoInformado, BigDecimal entradas, BigDecimal saidas) {}

    private Realizado realizado(UUID usuarioId, YearMonth mes) {
        LocalDate de = mes.atDay(1);
        LocalDate ate = mes.atEndOfMonth();
        LocalDateTime ini = de.atStartOfDay();
        LocalDateTime fimExclusivo = ate.plusDays(1).atStartOfDay();
        BigDecimal entradas = money(repo.somaBaixas(usuarioId, TipoLancamento.A_RECEBER, de, ate))
                .add(money(repo.somaVendasCaixa(usuarioId, ini, fimExclusivo)));
        BigDecimal saidas = money(repo.somaBaixas(usuarioId, TipoLancamento.A_PAGAR, de, ate));
        List<BigDecimal> aberturas = repo.aberturasDoPeriodo(usuarioId, ini, fimExclusivo, PageRequest.of(0, 1));
        boolean informado = !aberturas.isEmpty();
        return new Realizado(informado ? money(aberturas.get(0)) : ZERO, informado, entradas, saidas);
    }

    // ---- próximos vencimentos (RN-NOVA-21) ----

    private ResumoFinanceiroResponse.ProximosVencimentos proximos(UUID usuarioId, LocalDate hoje) {
        Page<Parcela> p = repo.proximosVencimentos(usuarioId, hoje.plusDays(DIAS_PROXIMOS), PageRequest.of(0, MAX_LINHAS_PROXIMOS));
        List<ParcelaFinanceiraResponse> itens = paraRespostas(p.getContent(), hoje);
        long total = p.getTotalElements();
        return new ResumoFinanceiroResponse.ProximosVencimentos(itens, total, total > MAX_LINHAS_PROXIMOS,
                total == 0 ? MSG_SEM_VENCIMENTOS : null);
    }

    private List<ParcelaFinanceiraResponse> paraRespostas(List<Parcela> parcelas, LocalDate hoje) {
        Set<UUID> ids = new HashSet<>();
        parcelas.forEach(p -> ids.add(p.getLancamento().getId()));
        Map<UUID, Integer> totais = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] linha : parcelaRepository.contarAtivasPorLancamento(ids)) {
                totais.put((UUID) linha[0], ((Number) linha[1]).intValue());
            }
        }
        return parcelas.stream()
                .map(p -> ParcelaFinanceiraService.paraResposta(p, totais.getOrDefault(p.getLancamento().getId(), 1), hoje)).toList();
    }

    // ---- gráfico 1: entrou x saiu por semana (RN-NOVA-22) ----

    private List<ResumoFinanceiroResponse.Semana> semanas(UUID usuarioId, YearMonth mes, Realizado r) {
        LocalDate de = mes.atDay(1);
        LocalDate ate = mes.atEndOfMonth();
        Map<LocalDate, BigDecimal> entradasDia = new HashMap<>();
        Map<LocalDate, BigDecimal> saidasDia = new HashMap<>();
        for (Object[] l : repo.baixasPorDia(usuarioId, TipoLancamento.A_RECEBER, de, ate)) {
            entradasDia.merge((LocalDate) l[0], money((BigDecimal) l[1]), BigDecimal::add);
        }
        for (Object[] l : repo.vendasCaixaPorDia(usuarioId, de.atStartOfDay(), ate.plusDays(1).atStartOfDay())) {
            entradasDia.merge((LocalDate) l[0], money((BigDecimal) l[1]), BigDecimal::add);
        }
        for (Object[] l : repo.baixasPorDia(usuarioId, TipoLancamento.A_PAGAR, de, ate)) {
            saidasDia.merge((LocalDate) l[0], money((BigDecimal) l[1]), BigDecimal::add);
        }
        List<ResumoFinanceiroResponse.Semana> semanas = new ArrayList<>();
        BigDecimal acumulado = r.saldoInicial;
        int numero = 1;
        for (int inicio = 1; inicio <= mes.lengthOfMonth(); inicio += 7, numero++) {
            int fim = (numero == 5) ? mes.lengthOfMonth() : Math.min(inicio + 6, mes.lengthOfMonth());
            BigDecimal e = ZERO;
            BigDecimal s = ZERO;
            for (int d = inicio; d <= fim; d++) {
                e = e.add(entradasDia.getOrDefault(mes.atDay(d), ZERO));
                s = s.add(saidasDia.getOrDefault(mes.atDay(d), ZERO));
            }
            acumulado = acumulado.add(e).subtract(s);
            semanas.add(new ResumoFinanceiroResponse.Semana(numero, inicio == fim ? "Dia " + inicio : inicio + " a " + fim,
                    mes.atDay(inicio), mes.atDay(fim), e, s, acumulado));
            if (fim == mes.lengthOfMonth()) break;
        }
        return semanas;
    }

    // ---- gráfico 2: projeção 30/60/90 (RN-NOVA-20) ----

    private ResumoFinanceiroResponse.Projecao projecao(UUID usuarioId, LocalDate hoje) {
        Realizado atual = realizado(usuarioId, YearMonth.from(hoje));
        BigDecimal saldoAtual = atual.saldoInicial.add(atual.entradas).subtract(atual.saidas);
        LocalDate limite = hoje.plusDays(MARCOS[MARCOS.length - 1]);
        Map<LocalDate, BigDecimal> variacao = new HashMap<>();
        boolean temEmAberto = false;
        for (Object[] l : repo.emAbertoProjetadoAte(usuarioId, TipoLancamento.A_RECEBER, limite)) {
            // vencida (antes de hoje) entra já no dia de hoje
            variacao.merge(max((LocalDate) l[0], hoje), money((BigDecimal) l[1]), BigDecimal::add);
            temEmAberto = true;
        }
        for (Object[] l : repo.emAbertoProjetadoAte(usuarioId, TipoLancamento.A_PAGAR, limite)) {
            variacao.merge(max((LocalDate) l[0], hoje), money((BigDecimal) l[1]).negate(), BigDecimal::add);
            temEmAberto = true;
        }
        List<ResumoFinanceiroResponse.Ponto> serie = new ArrayList<>();
        Map<Integer, BigDecimal> porDia = new HashMap<>();
        BigDecimal saldo = saldoAtual;
        for (int i = 0; i <= MARCOS[MARCOS.length - 1]; i++) {
            LocalDate dia = hoje.plusDays(i);
            saldo = saldo.add(variacao.getOrDefault(dia, ZERO));
            serie.add(new ResumoFinanceiroResponse.Ponto(dia, saldo));
            porDia.put(i, saldo);
        }
        List<ResumoFinanceiroResponse.Marco> marcos = new ArrayList<>();
        for (int m : MARCOS) {
            marcos.add(new ResumoFinanceiroResponse.Marco(m, hoje.plusDays(m), porDia.get(m)));
        }
        boolean semDados = !temEmAberto && saldoAtual.signum() == 0;
        return new ResumoFinanceiroResponse.Projecao(saldoAtual, marcos, serie, semDados, semDados ? MSG_GRAFICO_VAZIO : null);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    // ---- maiores despesas por categoria (RN-NOVA-23) ----

    private List<ResumoFinanceiroResponse.CategoriaDespesa> maioresDespesas(UUID usuarioId, LocalDate de, LocalDate ate) {
        record Linha(UUID id, String nome, BigDecimal valor) {}
        List<Linha> linhas = new ArrayList<>();
        for (Object[] l : repo.despesasPorCategoria(usuarioId, de, ate)) {
            BigDecimal valor = money((BigDecimal) l[2]);
            if (valor.signum() > 0) linhas.add(new Linha((UUID) l[0], (String) l[1], valor));
        }
        if (linhas.isEmpty()) return List.of();
        Collator ordemNome = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        ordemNome.setStrength(Collator.PRIMARY);
        linhas.sort(Comparator.<Linha, BigDecimal>comparing(Linha::valor).reversed()
                .thenComparing(Linha::nome, ordemNome).thenComparing(Linha::id));
        BigDecimal total = linhas.stream().map(Linha::valor).reduce(ZERO, BigDecimal::add);
        List<ResumoFinanceiroResponse.CategoriaDespesa> saida = new ArrayList<>();
        BigDecimal outras = ZERO;
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            if (i < MAX_CATEGORIAS) {
                saida.add(new ResumoFinanceiroResponse.CategoriaDespesa(l.id(), l.nome(), l.valor(), percentual(l.valor(), total), false));
            } else {
                outras = outras.add(l.valor());
            }
        }
        if (outras.signum() > 0) {
            saida.add(new ResumoFinanceiroResponse.CategoriaDespesa(null, "Outras categorias", outras, percentual(outras, total), true));
        }
        return saida;
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    // ---- utilidades ----

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private YearMonth interpretarMes(String texto, LocalDate hoje) {
        if (texto == null || texto.isBlank()) return YearMonth.from(hoje);
        YearMonth mes;
        try {
            if (texto.length() != 7) throw new DateTimeParseException("formato", texto, 0);
            mes = YearMonth.parse(texto);
        } catch (DateTimeParseException e) {
            throw new BusinessException("Informe o mês no formato AAAA-MM.");
        }
        if (!FaixaDataPermitida.contem(mes.atDay(1)) || !FaixaDataPermitida.contem(mes.atEndOfMonth())) {
            throw new BusinessException(FaixaDataPermitida.MENSAGEM);
        }
        return mes;
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
