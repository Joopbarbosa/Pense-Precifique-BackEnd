package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** V0.19.0 (#1172, RN-NOVA-2 e 4, DT-NOVA-2) — consultas das listas A pagar / A receber e a situação derivada pelo relógio de Brasília. Somente leitura. */
@Service
@RequiredArgsConstructor
public class ParcelaFinanceiraService {

    private final UsuarioRepository usuarioRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ParcelaRepository parcelaRepository;
    private final Clock relogioBrasilia;

    @Transactional(readOnly = true)
    public ListaParcelasResponse listar(TipoLancamento tipo, LocalDate de, LocalDate ate, FiltroSituacaoParcela filtro, Pageable pageable) {
        UUID usuarioId = usuarioId();
        LocalDate hoje = LocalDate.now(relogioBrasilia);
        YearMonth mes = YearMonth.from(hoje);
        LocalDate inicio = de != null ? de : mes.atDay(1);
        LocalDate fim = ate != null ? ate : mes.atEndOfMonth();
        if (!FaixaDataPermitida.contem(inicio) || !FaixaDataPermitida.contem(fim)) {
            throw new BusinessException(FaixaDataPermitida.MENSAGEM);
        }
        if (inicio.isAfter(fim)) {
            throw new BusinessException("A data inicial não pode ser posterior à data final.");
        }
        FiltroSituacaoParcela f = filtro == null ? FiltroSituacaoParcela.EM_ABERTO : filtro;
        Set<SituacaoParcela> situacoes = switch (f) {
            case EM_ABERTO, VENCIDAS -> EnumSet.of(SituacaoParcela.EM_ABERTO);
            case BAIXADAS -> EnumSet.of(SituacaoParcela.BAIXADA);
            case TODAS -> EnumSet.of(SituacaoParcela.EM_ABERTO, SituacaoParcela.BAIXADA);
        };
        // a ordenação é fixa (vencidas primeiro, depois vencimento): o sort do pedido é ignorado
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Parcela> parcelas = parcelaRepository.listar(usuarioId, tipo, inicio, fim, situacoes,
                f == FiltroSituacaoParcela.VENCIDAS, hoje, pagina);
        Map<UUID, Integer> totais = totaisPorLancamento(parcelas.getContent());
        List<ParcelaFinanceiraResponse> itens = parcelas.getContent().stream()
                .map(p -> paraResposta(p, totais.getOrDefault(p.getLancamento().getId(), 1), hoje)).toList();
        return new ListaParcelasResponse(itens, parcelas.getTotalElements(), parcelas.getNumber(), parcelas.getSize(),
                parcelaRepository.totalEmAberto(usuarioId, tipo, inicio, fim),
                parcelaRepository.totalVencido(usuarioId, tipo, inicio, fim, hoje),
                !lancamentoRepository.existsByUsuarioId(usuarioId));
    }

    @Transactional(readOnly = true)
    public ParcelaFinanceiraResponse buscar(UUID id) {
        Parcela p = parcelaRepository.buscarDaUsuaria(id, usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada."));
        return paraResposta(p);
    }

    /** Converte a parcela já gravada na resposta (usada também após baixar/desfazer). */
    @Transactional(readOnly = true)
    public ParcelaFinanceiraResponse paraResposta(Parcela p) {
        Map<UUID, Integer> totais = totaisPorLancamento(List.of(p));
        return paraResposta(p, totais.getOrDefault(p.getLancamento().getId(), 1), LocalDate.now(relogioBrasilia));
    }

    private Map<UUID, Integer> totaisPorLancamento(List<Parcela> parcelas) {
        Set<UUID> ids = parcelas.stream().map(p -> p.getLancamento().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }
        for (Object[] linha : parcelaRepository.contarAtivasPorLancamento(ids)) {
            mapa.put((UUID) linha[0], ((Number) linha[1]).intValue());
        }
        return mapa;
    }

    static ParcelaFinanceiraResponse paraResposta(Parcela p, int totalParcelas, LocalDate hoje) {
        Lancamento l = p.getLancamento();
        String exibida;
        String rotulo;
        Long atraso = null;
        switch (p.getSituacao()) {
            case BAIXADA -> {
                exibida = l.getTipo() == TipoLancamento.A_PAGAR ? "PAGA" : "RECEBIDA";
                rotulo = l.getTipo() == TipoLancamento.A_PAGAR ? "Paga" : "Recebida";
            }
            case CANCELADA -> {
                exibida = "CANCELADA";
                rotulo = "Cancelada";
            }
            default -> {
                if (p.getVencimento().isBefore(hoje)) {
                    atraso = ChronoUnit.DAYS.between(p.getVencimento(), hoje);
                    exibida = "VENCIDA";
                    rotulo = "Vencida há " + atraso + (atraso == 1 ? " dia" : " dias");
                } else if (p.getVencimento().isEqual(hoje)) {
                    exibida = "VENCE_HOJE";
                    rotulo = "Vence hoje";
                } else {
                    exibida = "EM_ABERTO";
                    rotulo = "Em aberto";
                }
            }
        }
        MetodoPagamentoConfiguravel m = p.getMetodoPagamento();
        return new ParcelaFinanceiraResponse(p.getId(), l.getId(), l.getTipo(), l.getDescricao(), l.getOrigem(),
                l.getCompra() == null ? null : l.getCompra().getId(), l.getOrcamento() == null ? null : l.getOrcamento().getId(),
                p.getNumero(), totalParcelas, p.getValor(), p.getVencimento(), p.getSituacao(), exibida, atraso, rotulo,
                p.getDataBaixa(), m == null ? null : m.getId(),
                m == null ? null : (m.getNome() != null ? m.getNome() : m.getTipo().name()), p.getOrigemBaixa());
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
