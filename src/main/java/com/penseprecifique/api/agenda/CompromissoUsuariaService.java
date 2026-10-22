package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.CompromissoUsuaria;
import com.penseprecifique.api.shared.domain.entity.CompromissoUsuariaExcecao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EscopoEdicaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.RecorrenciaCompromisso;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.VinculoSituacao;
import com.penseprecifique.api.shared.dto.request.agenda.CancelarCompromissoRequest;
import com.penseprecifique.api.shared.dto.request.agenda.CompromissoUsuariaRequest;
import com.penseprecifique.api.shared.dto.request.agenda.EditarCompromissoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoUsuariaDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.18.0 (#1039, DT-NOVA-5, RN-NOVA-19 a RN-NOVA-21) — criar, editar, concluir e cancelar o compromisso da usuária. O mestre
 * guarda a série; a ocorrência que foge do padrão vira exceção. Concluir é sempre de uma ocorrência; cancelar e editar podem
 * valer para a ocorrência ou para a série inteira (só as pendentes). Não existe excluir.
 */
@Service
@RequiredArgsConstructor
public class CompromissoUsuariaService {

    static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    static final LocalDate DATA_MAXIMA = LocalDate.of(2100, 12, 31);
    static final int MINIMO_OBSERVACAO = 30;
    static final int MAXIMO_OBSERVACAO = 500;

    private final UsuarioRepository usuarioRepository;
    private final CompromissoUsuariaRepository compromissoRepository;
    private final CompromissoUsuariaExcecaoRepository excecaoRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final InsumoRepository insumoRepository;
    private final OcorrenciasCalculadora calculadora;

    // ------------------------------------------------------------------------------------------ criar

    @Transactional
    public CompromissoUsuariaDetalheResponse criar(CompromissoUsuariaRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        validarData(request.data());
        validarHorarioFim(request.horario(), request.horarioFim());
        RecorrenciaCompromisso recorrencia = request.recorrencia() == null ? RecorrenciaCompromisso.NENHUMA : request.recorrencia();
        validarRecorrencia(recorrencia, request.diasSemana(), request.quantidadeOcorrencias());
        CompromissoUsuaria c = CompromissoUsuaria.builder()
                .usuario(usuario)
                .titulo(request.titulo().strip())
                .dataInicio(request.data())
                .horario(request.horario())
                .horarioFim(request.horarioFim())
                .orcamento(resolverOrcamento(request.orcamentoId(), usuario.getId()))
                .insumo(resolverInsumo(request.insumoId(), usuario.getId()))
                .recorrencia(recorrencia)
                .diasSemana(recorrencia == RecorrenciaCompromisso.SEMANAL ? diasEmTexto(request.diasSemana()) : null)
                .quantidadeOcorrencias(recorrencia == RecorrenciaCompromisso.NENHUMA ? null : request.quantidadeOcorrencias())
                .build();
        if (c.getTitulo().isEmpty()) {
            throw BusinessException.noCampo("titulo", "Informe o título");
        }
        c = compromissoRepository.save(c);
        return detalhe(primeiraOcorrencia(c));
    }

    // ----------------------------------------------------------------------------------------- detalhe

    @Transactional(readOnly = true)
    public CompromissoUsuariaDetalheResponse detalhe(UUID id, LocalDate dataOriginal) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        CompromissoUsuaria c = buscarMestre(id, usuarioId);
        return detalhe(resolver(c, dataOriginal));
    }

    // ----------------------------------------------------------------------------------------- concluir

    /** Concluir é sempre de uma ocorrência e não pede observação (RN-NOVA-21). */
    @Transactional
    public CompromissoUsuariaDetalheResponse concluir(UUID id, LocalDate dataOriginal) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        CompromissoUsuaria c = buscarMestre(id, usuarioId);
        OcorrenciaResolvida o = exigirPendente(resolver(c, dataOriginal));
        CompromissoUsuariaExcecao e = gravarComValoresVigentes(o);
        e.setSituacao(SituacaoCompromisso.CONCLUIDO);
        excecaoRepository.save(e);
        return detalhe(resolver(c, dataOriginal));
    }

    // ----------------------------------------------------------------------------------------- cancelar

    @Transactional
    public CompromissoUsuariaDetalheResponse cancelar(UUID id, LocalDate dataOriginal, CancelarCompromissoRequest request) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        CompromissoUsuaria c = buscarMestre(id, usuarioId);
        String observacao = validarObservacao(request.observacao());
        OcorrenciaResolvida o = exigirPendente(resolver(c, dataOriginal));
        if (serieInteira(c, request.escopo())) {
            // a série inteira atinge só o que ainda está pendente: as exceções pendentes e as ocorrências sem exceção
            c.setSituacao(SituacaoCompromisso.CANCELADO);
            c.setObservacaoCancelamento(observacao);
            compromissoRepository.save(c);
            for (CompromissoUsuariaExcecao e : excecaoRepository.findByCompromissoId(c.getId())) {
                if (e.getSituacao() == SituacaoCompromisso.PENDENTE) {
                    e.setSituacao(SituacaoCompromisso.CANCELADO);
                    e.setObservacaoCancelamento(observacao);
                    excecaoRepository.save(e);
                }
            }
        } else {
            CompromissoUsuariaExcecao e = gravarComValoresVigentes(o);
            e.setSituacao(SituacaoCompromisso.CANCELADO);
            e.setObservacaoCancelamento(observacao);
            excecaoRepository.save(e);
        }
        return detalhe(resolver(c, dataOriginal));
    }

    // ------------------------------------------------------------------------------------------ editar

    @Transactional
    public CompromissoUsuariaDetalheResponse editar(UUID id, LocalDate dataOriginal, EditarCompromissoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        CompromissoUsuaria c = buscarMestre(id, usuario.getId());
        OcorrenciaResolvida o = exigirPendente(resolver(c, dataOriginal));
        String titulo = request.titulo().strip();
        if (titulo.isEmpty()) {
            throw BusinessException.noCampo("titulo", "Informe o título");
        }
        validarHorarioFim(request.horario(), request.horarioFim());
        Orcamento orcamento = resolverOrcamento(request.orcamentoId(), usuario.getId());
        Insumo insumo = resolverInsumo(request.insumoId(), usuario.getId());
        boolean serie = c.getRecorrencia() != RecorrenciaCompromisso.NENHUMA;
        if (!serie) {
            // sem recorrência a ocorrência é o próprio compromisso: edita o mestre, inclusive a data
            LocalDate data = request.data() != null ? request.data() : c.getDataInicio();
            validarData(data);
            c.setTitulo(titulo);
            c.setDataInicio(data);
            c.setHorario(request.horario());
            c.setHorarioFim(request.horarioFim());
            c.setOrcamento(orcamento);
            c.setInsumo(insumo);
            compromissoRepository.save(c);
            // a exceção (se houver, por exemplo uma conclusão desfeita nunca existe) segue a data original nova
            return detalhe(resolver(c, data));
        }
        if (serieInteira(c, request.escopo())) {
            // série inteira: título, horário e vínculos do mestre; a data só muda por ocorrência (RN-NOVA-21)
            c.setTitulo(titulo);
            c.setHorario(request.horario());
            c.setHorarioFim(request.horarioFim());
            c.setOrcamento(orcamento);
            c.setInsumo(insumo);
            compromissoRepository.save(c);
            return detalhe(resolver(c, dataOriginal));
        }
        LocalDate novaData = request.data() != null ? request.data() : o.data();
        validarData(novaData);
        CompromissoUsuariaExcecao e = excecaoRepository.findByCompromissoIdAndDataOriginal(c.getId(), dataOriginal)
                .orElseGet(() -> CompromissoUsuariaExcecao.builder().compromisso(c).dataOriginal(dataOriginal).build());
        e.setNovaData(novaData.equals(dataOriginal) ? null : novaData);
        e.setTitulo(titulo.equals(c.getTitulo()) ? null : titulo);
        boolean horarioIgual = Objects.equals(request.horario(), c.getHorario()) && Objects.equals(request.horarioFim(), c.getHorarioFim());
        e.setHorarioProprio(!horarioIgual);
        e.setHorario(horarioIgual ? null : request.horario());
        e.setHorarioFim(horarioIgual ? null : request.horarioFim());
        aplicarVinculoOrcamento(e, c, orcamento);
        aplicarVinculoInsumo(e, c, insumo);
        excecaoRepository.save(e);
        return detalhe(resolver(c, dataOriginal));
    }

    // ----------------------------------------------------------------------------------------- internos

    /** Cria (ou reaproveita) a exceção da ocorrência gravando os valores vigentes, para ela manter o que tinha. */
    private CompromissoUsuariaExcecao gravarComValoresVigentes(OcorrenciaResolvida o) {
        CompromissoUsuaria c = o.mestre();
        CompromissoUsuariaExcecao e = excecaoRepository.findByCompromissoIdAndDataOriginal(c.getId(), o.dataOriginal())
                .orElseGet(() -> CompromissoUsuariaExcecao.builder().compromisso(c).dataOriginal(o.dataOriginal()).build());
        e.setTitulo(o.titulo());
        e.setHorarioProprio(true);
        e.setHorario(o.horario());
        e.setHorarioFim(o.horarioFim());
        e.setOrcamento(o.orcamento());
        e.setOrcamentoSituacao(OcorrenciaResolvida.situacaoDe(o.orcamento()));
        e.setInsumo(o.insumo());
        e.setInsumoSituacao(OcorrenciaResolvida.situacaoDe(o.insumo()));
        return e;
    }

    private void aplicarVinculoOrcamento(CompromissoUsuariaExcecao e, CompromissoUsuaria c, Orcamento desejado) {
        Orcamento do_mestre = c.getOrcamento();
        if (Objects.equals(idOrcamento(desejado), idOrcamento(do_mestre))) {
            e.setOrcamento(null);
            e.setOrcamentoSituacao(VinculoSituacao.HERDA);
        } else {
            e.setOrcamento(desejado);
            e.setOrcamentoSituacao(desejado == null ? VinculoSituacao.REMOVIDO : VinculoSituacao.DEFINIDO);
        }
    }

    private void aplicarVinculoInsumo(CompromissoUsuariaExcecao e, CompromissoUsuaria c, Insumo desejado) {
        if (Objects.equals(idInsumo(desejado), idInsumo(c.getInsumo()))) {
            e.setInsumo(null);
            e.setInsumoSituacao(VinculoSituacao.HERDA);
        } else {
            e.setInsumo(desejado);
            e.setInsumoSituacao(desejado == null ? VinculoSituacao.REMOVIDO : VinculoSituacao.DEFINIDO);
        }
    }

    private static UUID idOrcamento(Orcamento o) {
        return o == null ? null : o.getId();
    }

    private static UUID idInsumo(Insumo i) {
        return i == null ? null : i.getId();
    }

    private boolean serieInteira(CompromissoUsuaria c, EscopoEdicaoCompromisso escopo) {
        return c.getRecorrencia() != RecorrenciaCompromisso.NENHUMA && escopo == EscopoEdicaoCompromisso.SERIE;
    }

    /** A ocorrência existe se é uma data da série ou se já tem exceção; senão, não encontrada. */
    private OcorrenciaResolvida resolver(CompromissoUsuaria c, LocalDate dataOriginal) {
        // data fora de 2000–2100 não é ocorrência de nada, e nem chega ao banco (data extrema derrubaria a consulta)
        if (dataOriginal == null || dataOriginal.isBefore(DATA_MINIMA) || dataOriginal.isAfter(DATA_MAXIMA)) {
            throw new ResourceNotFoundException("Compromisso não encontrado");
        }
        CompromissoUsuariaExcecao e = excecaoRepository.findByCompromissoIdAndDataOriginal(c.getId(), dataOriginal).orElse(null);
        if (e == null && !calculadora.ehOcorrencia(c, dataOriginal)) {
            throw new ResourceNotFoundException("Compromisso não encontrado");
        }
        return OcorrenciaResolvida.de(c, dataOriginal, e);
    }

    private OcorrenciaResolvida exigirPendente(OcorrenciaResolvida o) {
        if (!o.pendente()) {
            throw new BusinessException("Este compromisso já foi " + (o.situacao() == SituacaoCompromisso.CONCLUIDO ? "concluído" : "cancelado") + ".");
        }
        return o;
    }

    private String validarObservacao(String observacao) {
        String texto = observacao == null ? "" : observacao.strip();
        if (texto.length() < MINIMO_OBSERVACAO) {
            throw BusinessException.noCampo("observacao", "A observação precisa ter pelo menos " + MINIMO_OBSERVACAO + " caracteres");
        }
        if (texto.length() > MAXIMO_OBSERVACAO) {
            throw BusinessException.noCampo("observacao", "Máximo de " + MAXIMO_OBSERVACAO + " caracteres");
        }
        return texto;
    }

    /** #1063 — o término exige o início e é posterior a ele (BLOQUEIO). */
    private void validarHorarioFim(LocalTime horario, LocalTime horarioFim) {
        if (horarioFim == null) return;
        if (horario == null) {
            throw BusinessException.noCampo("horarioFim", "Informe o horário de início para definir o término");
        }
        if (!horarioFim.isAfter(horario)) {
            throw BusinessException.noCampo("horarioFim", "O término deve ser depois do início");
        }
    }

    private void validarData(LocalDate data) {
        if (data == null || data.isBefore(DATA_MINIMA) || data.isAfter(DATA_MAXIMA)) {
            throw BusinessException.noCampo("data", "A data deve estar entre 01/01/2000 e 31/12/2100");
        }
    }

    /** RN-NOVA-20 — semanal exige ao menos um dia; N de 2 a 365; sem recorrência não aceita dias nem quantidade. */
    private void validarRecorrencia(RecorrenciaCompromisso recorrencia, List<DayOfWeek> dias, Integer quantidade) {
        if (recorrencia == RecorrenciaCompromisso.NENHUMA) {
            if ((dias != null && !dias.isEmpty()) || quantidade != null) {
                throw new BusinessException("Dias da semana e quantidade de ocorrências só valem em compromisso com recorrência");
            }
            return;
        }
        if (recorrencia == RecorrenciaCompromisso.SEMANAL && (dias == null || dias.isEmpty() || dias.stream().anyMatch(Objects::isNull))) {
            throw BusinessException.noCampo("diasSemana", "Escolha pelo menos um dia da semana");
        }
        if (recorrencia == RecorrenciaCompromisso.MENSAL && dias != null && !dias.isEmpty()) {
            throw new BusinessException("Na recorrência mensal não há dias da semana: vale o dia do mês da data de início");
        }
        if (quantidade != null && (quantidade < 2 || quantidade > 365)) {
            throw BusinessException.noCampo("quantidadeOcorrencias", "A quantidade de ocorrências vai de 2 a 365");
        }
    }

    private static String diasEmTexto(List<DayOfWeek> dias) {
        return dias.stream().distinct().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    /** Orçamento ativo da conta; de outra conta ou inexistente, não encontrado (RN-NOVA-6). */
    private Orcamento resolverOrcamento(UUID id, UUID usuarioId) {
        if (id == null) {
            return null;
        }
        return orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
    }

    /** Insumo ativo da conta; a busca da tela só oferece registros ativos (RN-NOVA-19). */
    private Insumo resolverInsumo(UUID id, UUID usuarioId) {
        if (id == null) {
            return null;
        }
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        if (!Boolean.TRUE.equals(insumo.getAtivo())) {
            throw BusinessException.noCampo("insumoId", "Escolha um insumo ativo");
        }
        return insumo;
    }

    private CompromissoUsuaria buscarMestre(UUID id, UUID usuarioId) {
        return compromissoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Compromisso não encontrado"));
    }

    private OcorrenciaResolvida primeiraOcorrencia(CompromissoUsuaria c) {
        LocalDate primeira = calculadora.datas(c, c.getDataInicio(), c.getDataInicio().plusDays(366)).stream()
                .min(Comparator.naturalOrder()).orElse(c.getDataInicio());
        return OcorrenciaResolvida.de(c, primeira, null);
    }

    private CompromissoUsuariaDetalheResponse detalhe(OcorrenciaResolvida o) {
        CompromissoUsuaria c = o.mestre();
        Orcamento orc = o.orcamento();
        Insumo ins = o.insumo();
        return new CompromissoUsuariaDetalheResponse(c.getId(), o.titulo(), o.dataOriginal(), o.data(), o.horario(), o.horarioFim(), o.situacao(),
                o.observacaoCancelamento(), c.getRecorrencia() != RecorrenciaCompromisso.NENHUMA, c.getRecorrencia(),
                OcorrenciasCalculadora.diasDaSemana(c).stream().sorted().toList(), c.getQuantidadeOcorrencias(),
                orc == null ? null : new CompromissoUsuariaDetalheResponse.VinculoOrcamento(orc.getId(),
                        IdentificadorFormatter.formatar("ORC", orc.getNumero()), orc.getCliente().getNome()),
                ins == null ? null : new CompromissoUsuariaDetalheResponse.VinculoInsumo(ins.getId(), ins.getNome(),
                        ins.getDeletedAt() != null || !Boolean.TRUE.equals(ins.getAtivo())));
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
