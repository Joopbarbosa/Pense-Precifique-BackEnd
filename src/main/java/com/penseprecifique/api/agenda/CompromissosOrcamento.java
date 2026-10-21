package com.penseprecifique.api.agenda;

import com.penseprecifique.api.orcamento.EntregaEstimadaCalculadora;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * V0.18.0 (#1035, DT-NOVA-1) — compromissos que nascem do orçamento, calculados a cada consulta a partir do estado
 * atual dele: entrega estimada (RN-NOVA-9), validade (RN-NOVA-11), sinal a receber (RN-NOVA-12) e saldo a receber
 * (RN-NOVA-13). Nada é gravado: mudar prazo, sinal ou datas move o compromisso, e entregar, cancelar ou pagar o
 * tira do calendário (RN-NOVA-5 e RN-NOVA-6).
 */
@Component
@RequiredArgsConstructor
public class CompromissosOrcamento implements FonteCompromissos {

    private static final Set<StatusOrcamento> ENCERRADOS = Set.of(StatusOrcamento.ENTREGUE, StatusOrcamento.CANCELADO);

    private final OrcamentoRepository orcamentoRepository;
    private final EntregaEstimadaCalculadora entregaEstimadaCalculadora;

    @Override
    public List<CompromissoBase> listar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<CompromissoBase> resultado = new ArrayList<>();
        for (Orcamento o : orcamentoRepository.findEmAbertoComCliente(usuarioId, ENCERRADOS)) {
            entrega(o).ifPresent(c -> adicionar(resultado, c, de, ate, hoje));
            validade(o).ifPresent(c -> adicionar(resultado, c, de, ate, hoje));
            sinal(o, hoje).ifPresent(c -> adicionar(resultado, c, de, ate, hoje));
            saldo(o, hoje).ifPresent(c -> adicionar(resultado, c, de, ate, hoje));
        }
        return resultado;
    }

    /** Entra o que cai no período e o pendente atrasado (a Agenda sobe este para hoje). */
    private void adicionar(List<CompromissoBase> destino, CompromissoBase c, LocalDate de, LocalDate ate, LocalDate hoje) {
        boolean noPeriodo = !c.data().isBefore(de) && !c.data().isAfter(ate);
        if (noPeriodo || c.data().isBefore(hoje)) {
            destino.add(c);
        }
    }

    /** RN-NOVA-9 — enquanto o orçamento não foi entregue nem cancelado e tem entrega estimada. */
    private java.util.Optional<CompromissoBase> entrega(Orcamento o) {
        LocalDate entrega = entregaEstimadaCalculadora.calcular(o);
        if (entrega == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(base(o, TipoCompromisso.ENTREGA_ESTIMADA, "Entrega estimada — " + identificador(o) + " (" + cliente(o) + ")",
                entrega, null));
    }

    /** RN-NOVA-11 — só em RASCUNHO ou ENVIADO e só no dia da validade; nunca vira atraso (não é devolvido depois). */
    private java.util.Optional<CompromissoBase> validade(Orcamento o) {
        if (o.getDataValidade() == null
                || (o.getStatus() != StatusOrcamento.RASCUNHO && o.getStatus() != StatusOrcamento.ENVIADO)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(base(o, TipoCompromisso.VALIDADE_ORCAMENTO, "Validade — " + identificador(o) + " vence hoje",
                o.getDataValidade().toLocalDate(), null));
    }

    /** RN-NOVA-12 — em AGUARDANDO_SINAL: no dia de "Sinal até", ou em hoje (sem destaque) quando não há a data. */
    private java.util.Optional<CompromissoBase> sinal(Orcamento o, LocalDate hoje) {
        if (o.getStatus() != StatusOrcamento.AGUARDANDO_SINAL || o.getValorSinal() == null) {
            return java.util.Optional.empty();
        }
        LocalDate data = o.getSinalAte() != null ? o.getSinalAte() : hoje;
        return java.util.Optional.of(base(o, TipoCompromisso.SINAL_A_RECEBER,
                "Sinal a receber — " + identificador(o) + " (" + cliente(o) + ") — " + reais(o.getValorSinal()), data, o.getValorSinal()));
    }

    /** RN-NOVA-13 — em FINALIZADO: no dia da entrega estimada, ou em hoje (sem destaque) quando não há entrega estimada. */
    private java.util.Optional<CompromissoBase> saldo(Orcamento o, LocalDate hoje) {
        if (o.getStatus() != StatusOrcamento.FINALIZADO) {
            return java.util.Optional.empty();
        }
        BigDecimal sinalPago = Boolean.TRUE.equals(o.getSinalAtivo()) && o.getValorSinal() != null ? o.getValorSinal() : BigDecimal.ZERO;
        BigDecimal saldo = o.getTotal().subtract(sinalPago);
        LocalDate entrega = entregaEstimadaCalculadora.calcular(o);
        return java.util.Optional.of(base(o, TipoCompromisso.SALDO_A_RECEBER,
                "Saldo a receber — " + identificador(o) + " (" + cliente(o) + ") — " + reais(saldo), entrega != null ? entrega : hoje, saldo));
    }

    private CompromissoBase base(Orcamento o, TipoCompromisso tipo, String titulo, LocalDate data, BigDecimal valor) {
        return new CompromissoBase(tipo.name() + ":" + o.getId(), tipo, SituacaoCompromisso.PENDENTE, titulo, data, null,
                List.of(), List.of(), o.getId(), identificador(o), o.getNumero() == null ? 0 : o.getNumero(), valor,
                o.getCreatedAt() == null ? null : o.getCreatedAt().toInstant(ZoneOffset.UTC), null, false);
    }

    private static String identificador(Orcamento o) {
        return IdentificadorFormatter.formatar("ORC", o.getNumero());
    }

    private static String cliente(Orcamento o) {
        return o.getCliente().getNome();
    }

    /** "R$ 1.234,50" — sem espaço especial, para o texto sair igual em qualquer JVM. */
    static String reais(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return "R$ " + formato.format(valor);
    }
}
