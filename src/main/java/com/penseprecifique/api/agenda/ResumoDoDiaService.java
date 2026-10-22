package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.dto.response.agenda.ResumoDoDiaResponse;
import com.penseprecifique.api.shared.dto.response.agenda.ResumoDoDiaResponse.InsumoFaltante;
import com.penseprecifique.api.shared.dto.response.agenda.ResumoDoDiaResponse.Valores;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * V0.18.0 (#1071, RN-NOVA-27) — monta o Resumo do dia a partir dos compromissos pendentes de hoje e dos atrasados, sem gravar nada.
 */
@Service
@RequiredArgsConstructor
public class ResumoDoDiaService {

    private static final Set<TipoCompromisso> A_RECEBER = Set.of(TipoCompromisso.SINAL_A_RECEBER, TipoCompromisso.SALDO_A_RECEBER);
    private static final Set<TipoCompromisso> ENTREGAS = Set.of(TipoCompromisso.ENTREGA_ESTIMADA, TipoCompromisso.RECEBIMENTO_COMPRA);
    private static final Set<TipoCompromisso> PRODUCOES = Set.of(TipoCompromisso.INICIO_PRODUCAO, TipoCompromisso.TERMINO_PRODUCAO);

    private final Clock clock;
    private final AgendaService agendaService;
    private final ProducaoService producaoService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public ResumoDoDiaResponse resumo() {
        LocalDate hoje = LocalDate.now(clock);
        List<CompromissoResponse> itens = agendaService.pendentesAteHoje();
        long atrasados = itens.stream().filter(CompromissoResponse::atrasado).count();
        long paraHoje = itens.size() - atrasados;
        return new ResumoDoDiaResponse(hoje, paraHoje, atrasados,
                valores(itens),
                new Valores(false, null, null, List.of()),
                filtrar(itens, ENTREGAS), filtrar(itens, PRODUCOES),
                itens.stream().filter(c -> !A_RECEBER.contains(c.tipo()) && !ENTREGAS.contains(c.tipo()) && !PRODUCOES.contains(c.tipo())).toList(),
                insumosFaltantes(itens));
    }

    private static List<CompromissoResponse> filtrar(List<CompromissoResponse> itens, Set<TipoCompromisso> tipos) {
        return itens.stream().filter(c -> tipos.contains(c.tipo())).toList();
    }

    private static Valores valores(List<CompromissoResponse> itens) {
        List<CompromissoResponse> receber = filtrar(itens, A_RECEBER);
        BigDecimal total = soma(receber, false);
        BigDecimal atrasado = soma(receber, true);
        return new Valores(true, total, atrasado, receber);
    }

    private static BigDecimal soma(List<CompromissoResponse> itens, boolean soAtrasados) {
        return itens.stream().filter(c -> !soAtrasados || c.atrasado()).map(CompromissoResponse::valor)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** O que as produções que ainda não começaram (de hoje ou atrasadas) precisam e o estoque de agora não cobre. */
    private List<InsumoFaltante> insumosFaltantes(List<CompromissoResponse> itens) {
        List<UUID> producoes = itens.stream().filter(c -> c.tipo() == TipoCompromisso.INICIO_PRODUCAO)
                .map(CompromissoResponse::referenciaId).distinct().toList();
        if (producoes.isEmpty()) {
            return List.of();
        }
        return producaoService.alertasDeInsumosDasProducoes(usuarioId(), producoes).stream()
                .filter(a -> a.getSituacao() != SituacaoAlertaInsumo.SUFICIENTE)
                .map(this::paraFaltante)
                .sorted(Comparator.comparing(InsumoFaltante::falta).reversed().thenComparing(InsumoFaltante::nome))
                .toList();
    }

    private InsumoFaltante paraFaltante(AlertaInsumoResponse a) {
        BigDecimal estoque = a.getEstoqueAtual() == null ? BigDecimal.ZERO : a.getEstoqueAtual();
        return new InsumoFaltante(a.getNomeInsumo(), a.getSiglaUnidade(), a.getQuantidadeNecessaria(), estoque,
                a.getQuantidadeNecessaria().subtract(estoque));
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
