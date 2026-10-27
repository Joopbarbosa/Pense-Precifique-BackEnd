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

    private static final Set<StatusOrcamento> ENCERRADOS = Set.of(StatusOrcamento.CANCELADO);

    private static final Set<StatusOrcamento> SINAL_RECEBIDO = Set.of(StatusOrcamento.SINAL_PAGO, StatusOrcamento.EM_PRODUCAO,
            StatusOrcamento.FINALIZADO, StatusOrcamento.PAGO, StatusOrcamento.ENTREGUE);

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

    private void adicionar(List<CompromissoBase> destino, CompromissoBase c, LocalDate de, LocalDate ate, LocalDate hoje) {
        if (relevante(c, de, ate, hoje)) {
            destino.add(c);
        }
    }

    /** RN-NOVA-9 — enquanto o orçamento não foi entregue nem cancelado e tem entrega estimada. */
    private java.util.Optional<CompromissoBase> entrega(Orcamento o) {
        LocalDate entrega = entregaEstimadaCalculadora.calcular(o);
        if (entrega == null) {
            return java.util.Optional.empty();
        }
        SituacaoCompromisso situacao = o.getStatus() == StatusOrcamento.ENTREGUE ? SituacaoCompromisso.CONCLUIDO : SituacaoCompromisso.PENDENTE;
        return java.util.Optional.of(base(o, TipoCompromisso.ENTREGA_ESTIMADA, "Entrega estimada — " + identificador(o) + " (" + cliente(o) + ")",
                entrega, null, situacao));
    }

    /**
     * RN-NOVA-11 — no dia da validade; nunca vira atraso (não é devolvido depois). Em RASCUNHO ou ENVIADO fica
     * pendente; depois da aprovação continua na Agenda como concluído (verde e riscado), até o orçamento ser
     * entregue ou cancelado.
     */
    private java.util.Optional<CompromissoBase> validade(Orcamento o) {
        if (o.getDataValidade() == null) {
            return java.util.Optional.empty();
        }
        boolean aberto = o.getStatus() == StatusOrcamento.RASCUNHO || o.getStatus() == StatusOrcamento.ENVIADO;
        return java.util.Optional.of(base(o, TipoCompromisso.VALIDADE_ORCAMENTO, "Validade — " + identificador(o) + " vence hoje",
                o.getDataValidade().toLocalDate(), null, aberto ? SituacaoCompromisso.PENDENTE : SituacaoCompromisso.CONCLUIDO));
    }

    /** RN-NOVA-12 — em AGUARDANDO_SINAL: no dia de "Sinal até", ou em hoje (sem destaque) quando não há a data. */
    private java.util.Optional<CompromissoBase> sinal(Orcamento o, LocalDate hoje) {
        boolean aguardando = o.getStatus() == StatusOrcamento.AGUARDANDO_SINAL;
        boolean recebido = SINAL_RECEBIDO.contains(o.getStatus()) && Boolean.TRUE.equals(o.getSinalAtivo());
        if (o.getValorSinal() == null || !aguardando && (!recebido || o.getSinalAte() == null)) {
            return java.util.Optional.empty();
        }
        LocalDate data = o.getSinalAte() != null ? o.getSinalAte() : hoje;
        return java.util.Optional.of(base(o, TipoCompromisso.SINAL_A_RECEBER,
                "Sinal a receber — " + identificador(o) + " (" + cliente(o) + ") — " + reais(o.getValorSinal()), data, o.getValorSinal(),
                aguardando ? SituacaoCompromisso.PENDENTE : SituacaoCompromisso.CONCLUIDO));
    }

    /** RN-NOVA-13 — em FINALIZADO: no dia da entrega estimada, ou em hoje (sem destaque) quando não há entrega estimada. */
    private java.util.Optional<CompromissoBase> saldo(Orcamento o, LocalDate hoje) {
        boolean pago = o.getStatus() == StatusOrcamento.PAGO || o.getStatus() == StatusOrcamento.ENTREGUE;
        if (o.getStatus() != StatusOrcamento.FINALIZADO && !pago) {
            return java.util.Optional.empty();
        }
        BigDecimal sinalPago = Boolean.TRUE.equals(o.getSinalAtivo()) && o.getValorSinal() != null ? o.getValorSinal() : BigDecimal.ZERO;
        BigDecimal saldo = o.getTotal().subtract(sinalPago);
        LocalDate entrega = entregaEstimadaCalculadora.calcular(o);
        return java.util.Optional.of(base(o, TipoCompromisso.SALDO_A_RECEBER,
                "Saldo a receber — " + identificador(o) + " (" + cliente(o) + ") — " + reais(saldo), entrega != null ? entrega : hoje, saldo,
                pago ? SituacaoCompromisso.CONCLUIDO : SituacaoCompromisso.PENDENTE));
    }

    private CompromissoBase base(Orcamento o, TipoCompromisso tipo, String titulo, LocalDate data, BigDecimal valor) {
        return base(o, tipo, titulo, data, valor, SituacaoCompromisso.PENDENTE);
    }

    private CompromissoBase base(Orcamento o, TipoCompromisso tipo, String titulo, LocalDate data, BigDecimal valor,
                                 SituacaoCompromisso situacao) {
        return new CompromissoBase(tipo.name() + ":" + o.getId(), tipo, situacao, titulo, data, null,
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
