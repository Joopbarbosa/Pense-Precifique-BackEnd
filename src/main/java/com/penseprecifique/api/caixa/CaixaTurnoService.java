package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaMovimento;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;
import com.penseprecifique.api.shared.dto.request.caixa.AbrirCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.CaixaMovimentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.FecharCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaMovimentoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * #488 — RN-NOVA-6/8/9. Serviço concreto, sem interface (padrão vigente desde o Épico 6).
 * `CaixaTurno`/`CaixaMovimento` vivem no mesmo package que `VendaCaixa*` (#487, DT-NOVA-1) — o
 * turno é pré-condição de toda venda do mesmo domínio.
 */
@Service
@RequiredArgsConstructor
public class CaixaTurnoService {

    private final CaixaTurnoRepository caixaTurnoRepository;
    private final CaixaMovimentoRepository caixaMovimentoRepository;
    private final VendaCaixaPagamentoRepository vendaCaixaPagamentoRepository;
    private final UsuarioRepository usuarioRepository;

    /** RN-NOVA-6 — bloqueia se já existir turno ABERTO; índice único parcial é a rede de segurança
     * contra corrida real (mesmo padrão de #142 em Empresa/ConfiguracaoPrecificacao). */
    @Transactional
    public CaixaTurnoResponseDTO abrirTurno(AbrirCaixaTurnoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        if (caixaTurnoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCaixaTurno.ABERTO).isPresent()) {
            throw new BusinessException("Já existe um caixa aberto.");
        }

        Usuario usuario = usuarioRepository.findByIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));

        CaixaTurno turno = CaixaTurno.builder()
                .usuario(usuario)
                .dataAbertura(LocalDateTime.now())
                .valorAbertura(request.valorAbertura())
                .status(StatusCaixaTurno.ABERTO)
                .build();

        try {
            return toResponse(caixaTurnoRepository.save(turno));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Já existe um caixa aberto.");
        }
    }

    @Transactional(readOnly = true)
    public CaixaTurnoResponseDTO buscarTurnoAberto() {
        UUID usuarioId = getUsuarioIdAutenticado();
        CaixaTurno turno = caixaTurnoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCaixaTurno.ABERTO)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum caixa aberto"));
        return toResponse(turno);
    }

    /** RN-NOVA-8 — exige turno ABERTO; motivo mín. 30 caracteres já validado no DTO. */
    @Transactional
    public CaixaMovimentoResponseDTO registrarMovimento(CaixaMovimentoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        CaixaTurno turno = caixaTurnoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCaixaTurno.ABERTO)
                .orElseThrow(() -> new BusinessException("Não há caixa aberto."));

        Usuario responsavel = usuarioRepository.findByIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));

        CaixaMovimento movimento = CaixaMovimento.builder()
                .caixaTurno(turno)
                .tipo(request.tipo())
                .valor(request.valor())
                .motivo(request.motivo())
                .dataMovimento(LocalDateTime.now())
                .responsavel(responsavel)
                .build();

        return toMovimentoResponse(caixaMovimentoRepository.save(movimento));
    }

    @Transactional(readOnly = true)
    public List<CaixaMovimentoResponseDTO> listarMovimentos(UUID turnoId) {
        UUID usuarioId = getUsuarioIdAutenticado();
        CaixaTurno turno = caixaTurnoRepository.findByIdAndUsuarioId(turnoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caixa não encontrado"));
        return caixaMovimentoRepository.findByCaixaTurnoIdOrderByDataMovimentoDesc(turno.getId())
                .stream().map(this::toMovimentoResponse).toList();
    }

    /** RN-NOVA-9 — calcula e grava a diferença, nunca bloqueia o fechamento. */
    @Transactional
    public CaixaTurnoResponseDTO fecharTurno(UUID id, FecharCaixaTurnoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        CaixaTurno turno = caixaTurnoRepository.findByIdAndUsuarioIdAndStatus(id, usuarioId, StatusCaixaTurno.ABERTO)
                .orElseThrow(() -> new ResourceNotFoundException("Caixa aberto não encontrado"));

        BigDecimal somaSuprimento = caixaMovimentoRepository.somarPorTipo(turno.getId(), TipoCaixaMovimento.SUPRIMENTO);
        BigDecimal somaSangria = caixaMovimentoRepository.somarPorTipo(turno.getId(), TipoCaixaMovimento.SANGRIA);
        // RN-NOVA-9 — completa a fórmula com as vendas em DINHEIRO do turno, agora que
        // VendaCaixaPagamento existe (#487, mesmo pocket — ver decisoes-caixa.md).
        BigDecimal somaVendasDinheiro = vendaCaixaPagamentoRepository.somarPagamentosDinheiroPorTurno(turno.getId());
        BigDecimal valorEsperado = turno.getValorAbertura().add(somaSuprimento).subtract(somaSangria).add(somaVendasDinheiro);

        turno.setValorFechamentoEsperado(valorEsperado);
        turno.setValorFechamentoInformado(request.valorFechamentoInformado());
        turno.setDiferenca(request.valorFechamentoInformado().subtract(valorEsperado));
        turno.setDataFechamento(LocalDateTime.now());
        turno.setStatus(StatusCaixaTurno.FECHADO);

        return toResponse(caixaTurnoRepository.save(turno));
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private CaixaTurnoResponseDTO toResponse(CaixaTurno t) {
        return new CaixaTurnoResponseDTO(
                t.getId(), t.getDataAbertura(), t.getValorAbertura(), t.getDataFechamento(),
                t.getValorFechamentoEsperado(), t.getValorFechamentoInformado(), t.getDiferenca(), t.getStatus());
    }

    private CaixaMovimentoResponseDTO toMovimentoResponse(CaixaMovimento m) {
        return new CaixaMovimentoResponseDTO(
                m.getId(), m.getTipo(), m.getValor(), m.getMotivo(), m.getDataMovimento(), m.getResponsavel().getId());
    }
}
