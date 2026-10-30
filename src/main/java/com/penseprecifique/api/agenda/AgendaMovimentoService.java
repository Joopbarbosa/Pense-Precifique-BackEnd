package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.orcamento.EntregaEstimadaCalculadora;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.request.agenda.MoverCompromissoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.MovimentoCompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (#1181, RN-NOVA-43 e RN-NOVA-44) — mover os cards derivados da Agenda. A Agenda não grava data própria: mover altera o
 * campo da origem de que o card deriva. {@link #simular} devolve o que a janela de confirmação mostra; {@link #mover} repete a
 * mesma validação e grava. O BLOQUEIO impede (400); o AVISO só é devolvido. Concluído, cancelado e Saldo a receber não movem.
 */
@Service
@RequiredArgsConstructor
public class AgendaMovimentoService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate DATA_MAXIMA = LocalDate.of(2100, 12, 31);

    private final UsuarioRepository usuarioRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoProducaoRepository orcamentoProducaoRepository;
    private final ProducaoRepository producaoRepository;
    private final HistoricoStatusProducaoRepository historicoRepository;
    private final CompraRepository compraRepository;
    private final EntregaEstimadaCalculadora entregaEstimadaCalculadora;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MovimentoCompromissoResponse simular(MoverCompromissoRequest request) {
        return avaliar(request).resposta();
    }

    @Transactional
    public MovimentoCompromissoResponse mover(MoverCompromissoRequest request) {
        Avaliacao a = avaliar(request);
        if (a.resposta().bloqueio() != null) {
            throw new BusinessException(a.resposta().bloqueio());
        }
        if (a.gravar() != null) {
            a.gravar().run();
        }
        return a.resposta();
    }

    private record Avaliacao(MovimentoCompromissoResponse resposta, Runnable gravar) {}

    private Avaliacao avaliar(MoverCompromissoRequest r) {
        validarData(r.novaData());
        UUID usuarioId = getUsuarioIdAutenticado();
        LocalDate hoje = LocalDate.now(clock);
        return switch (r.tipo()) {
            case ENTREGA_ESTIMADA -> entrega(orcamento(r, usuarioId), r.novaData(), hoje);
            case VALIDADE_ORCAMENTO -> validade(orcamento(r, usuarioId), r.novaData(), hoje);
            case SINAL_A_RECEBER -> sinal(orcamento(r, usuarioId), r.novaData(), hoje);
            case INICIO_PRODUCAO -> inicioProducao(producao(r, usuarioId), r.novaData(), hoje);
            case TERMINO_PRODUCAO -> terminoProducao(producao(r, usuarioId), r.novaData(), hoje);
            case RECEBIMENTO_COMPRA -> recebimento(compra(r, usuarioId), r.novaData(), hoje);
            case SALDO_A_RECEBER -> throw new BusinessException(
                    "O saldo a receber acompanha a entrega estimada: mova o card de Entrega estimada.");
            case AVULSO -> throw new BusinessException("O compromisso avulso é movido pela edição do próprio compromisso.");
        };
    }

    // ---------------------------------------------------------------------------------- orçamento

    private Avaliacao entrega(Orcamento o, LocalDate nova, LocalDate hoje) {
        if (o.getStatus() == StatusOrcamento.ENTREGUE || o.getStatus() == StatusOrcamento.CANCELADO) {
            throw naoMove("A entrega estimada de um orçamento entregue ou cancelado");
        }
        LocalDate partida = entregaEstimadaCalculadora.pontoDeContagem(o);
        LocalDate antes = entregaEstimadaCalculadora.calcular(o);
        if (partida == null || antes == null) {
            throw new BusinessException("Este orçamento ainda não tem entrega estimada para mover.");
        }
        List<String> avisos = new ArrayList<>();
        String bloqueio = null;
        long prazo = ChronoUnit.DAYS.between(partida, nova);
        if (prazo < 1) {
            bloqueio = "A entrega precisa ser a partir de " + partida.plusDays(1).format(DIA) + ", um dia depois do início da contagem em "
                    + partida.format(DIA);
        } else {
            avisoAntesDaProducao(o, nova, avisos);
            avisoPassado(nova, hoje, avisos);
        }
        String efeito = "Prazo de produção do orçamento: de " + o.getPrazoProducaoDias() + " para " + prazo + " dias."
                + " O saldo a receber acompanha a nova entrega.";
        int prazoNovo = (int) prazo;
        return new Avaliacao(resposta(titulo(o, "Entrega estimada"), antes, nova, efeito, avisos, bloqueio), () -> {
            o.setPrazoProducaoDias(prazoNovo);
            orcamentoRepository.save(o);
        });
    }

    private void avisoAntesDaProducao(Orcamento o, LocalDate novaEntrega, List<String> avisos) {
        for (OrcamentoProducao v : orcamentoProducaoRepository.findByOrcamentoId(o.getId())) {
            Producao p = v.getProducao();
            if (p.getDataTerminoPrevista() != null && novaEntrega.isBefore(p.getDataTerminoPrevista())) {
                avisos.add("A entrega fica antes da produção (término previsto em " + p.getDataTerminoPrevista().format(DIA) + ").");
                return;
            }
        }
    }

    private Avaliacao validade(Orcamento o, LocalDate nova, LocalDate hoje) {
        boolean aberto = o.getStatus() == StatusOrcamento.RASCUNHO || o.getStatus() == StatusOrcamento.ENVIADO;
        if (!aberto || o.getDataValidade() == null) {
            throw naoMove("A validade de um orçamento já aprovado");
        }
        List<String> avisos = new ArrayList<>();
        avisoPassado(nova, hoje, avisos);
        LocalDate antes = o.getDataValidade().toLocalDate();
        return new Avaliacao(resposta(titulo(o, "Validade"), antes, nova, "Validade do orçamento alterada para " + nova.format(DIA) + ".",
                avisos, null), () -> {
            o.setDataValidade(nova.atTime(o.getDataValidade().toLocalTime()));
            orcamentoRepository.save(o);
        });
    }

    private Avaliacao sinal(Orcamento o, LocalDate nova, LocalDate hoje) {
        if (o.getStatus() != StatusOrcamento.AGUARDANDO_SINAL) {
            throw naoMove("O sinal de um orçamento que não está aguardando o sinal");
        }
        LocalDate antes = o.getSinalAte() != null ? o.getSinalAte() : hoje;
        String bloqueio = nova.isBefore(hoje) ? "A data do sinal não pode ser anterior a hoje" : null;
        String efeito = "\"Sinal até\" do orçamento alterado para " + nova.format(DIA) + ".";
        return new Avaliacao(resposta(titulo(o, "Sinal a receber"), antes, nova, efeito, new ArrayList<>(), bloqueio), () -> {
            o.setSinalAte(nova);
            orcamentoRepository.save(o);
        });
    }

    // ---------------------------------------------------------------------------------- produção

    private Avaliacao inicioProducao(Producao p, LocalDate nova, LocalDate hoje) {
        boolean aindaNaoComecou = p.getEstado() == EstadoProducao.AGUARDANDO_INICIO
                || (p.getEstado() == EstadoProducao.TRAVADA && !historicoRepository.findIdsQueJaEstiveramEmAndamento(List.of(p.getId())).contains(p.getId()));
        if (!aindaNaoComecou || p.getDataInicio() == null) {
            throw naoMove("O início de uma produção que já começou");
        }
        List<String> avisos = new ArrayList<>();
        if (p.getDataTerminoPrevista() != null && nova.isAfter(p.getDataTerminoPrevista())) {
            avisos.add("O início fica depois do término previsto (" + p.getDataTerminoPrevista().format(DIA) + ").");
        }
        avisoPassado(nova, hoje, avisos);
        String efeito = "Data de início da produção alterada para " + nova.format(DIA) + ".";
        return new Avaliacao(resposta(tituloProducao(p, "Início previsto"), p.getDataInicio(), nova, efeito, avisos, null), () -> {
            p.setDataInicio(nova);
            producaoRepository.save(p);
        });
    }

    private Avaliacao terminoProducao(Producao p, LocalDate nova, LocalDate hoje) {
        boolean encerrada = p.getEstado() == EstadoProducao.FINALIZADA || p.getEstado() == EstadoProducao.CANCELADA
                || p.getEstado() == EstadoProducao.NAO_REALIZADA;
        if (encerrada || p.getDataTerminoPrevista() == null) {
            throw naoMove("O término de uma produção finalizada, cancelada ou não realizada");
        }
        List<String> avisos = new ArrayList<>();
        if (p.getDataInicio() != null && nova.isBefore(p.getDataInicio())) {
            avisos.add("O término fica antes do início da produção (" + p.getDataInicio().format(DIA) + ").");
        }
        avisoPassado(nova, hoje, avisos);
        String efeito = "Término previsto da produção alterado para " + nova.format(DIA) + ".";
        return new Avaliacao(resposta(tituloProducao(p, "Término previsto"), p.getDataTerminoPrevista(), nova, efeito, avisos, null), () -> {
            p.setDataTerminoPrevista(nova);
            producaoRepository.save(p);
        });
    }

    // ---------------------------------------------------------------------------------- compra

    private Avaliacao recebimento(Compra c, LocalDate nova, LocalDate hoje) {
        if (c.getStatus() != StatusCompra.PENDENTE_RECEBIMENTO || c.getPrevisaoEntrega() == null) {
            throw naoMove("A entrega de uma compra que já foi recebida ou cancelada");
        }
        String bloqueio = null;
        if (c.getDataCompra() != null && nova.isBefore(c.getDataCompra())) {
            bloqueio = "A previsão de entrega não pode ser anterior à data da compra";
        } else if (nova.isBefore(hoje) && !nova.equals(c.getPrevisaoEntrega())) {
            bloqueio = "A previsão de entrega não pode ser anterior a hoje";
        }
        String efeito = "Previsão de entrega da compra alterada para " + nova.format(DIA) + ".";
        return new Avaliacao(resposta("Entrega de compra COM-" + c.getNumero(), c.getPrevisaoEntrega(), nova, efeito, new ArrayList<>(), bloqueio), () -> {
            c.setPrevisaoEntrega(nova);
            compraRepository.save(c);
        });
    }

    // ---------------------------------------------------------------------------------- apoio

    private static void avisoPassado(LocalDate nova, LocalDate hoje, List<String> avisos) {
        if (nova.isBefore(hoje)) {
            avisos.add("A data já passou.");
        }
    }

    private static BusinessException naoMove(String o) {
        return new BusinessException(o + " não pode ser movida: o compromisso já foi cumprido ou não está pendente.");
    }

    private static void validarData(LocalDate data) {
        if (data.isBefore(DATA_MINIMA) || data.isAfter(DATA_MAXIMA)) {
            throw new BusinessException("Escolha uma data entre 01/01/2000 e 31/12/2100.");
        }
    }

    private static MovimentoCompromissoResponse resposta(String titulo, LocalDate antes, LocalDate depois, String efeito, List<String> avisos,
                                                         String bloqueio) {
        return new MovimentoCompromissoResponse(titulo, antes, depois, efeito, List.copyOf(avisos), bloqueio);
    }

    private static String titulo(Orcamento o, String tipo) {
        return tipo + " — ORC-" + o.getNumero();
    }

    private static String tituloProducao(Producao p, String tipo) {
        return tipo + " — PRD-" + p.getNumero();
    }

    private Orcamento orcamento(MoverCompromissoRequest r, UUID usuarioId) {
        return orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(r.referenciaId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
    }

    private Producao producao(MoverCompromissoRequest r, UUID usuarioId) {
        return producaoRepository.findByIdAndUsuarioId(r.referenciaId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));
    }

    private Compra compra(MoverCompromissoRequest r, UUID usuarioId) {
        return compraRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(r.referenciaId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra não encontrada"));
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
