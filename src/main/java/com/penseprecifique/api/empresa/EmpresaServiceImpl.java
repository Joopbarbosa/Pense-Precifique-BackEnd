package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.EmpresaHorario;
import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.HorarioFuncionamentoRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;
import com.penseprecifique.api.shared.dto.response.config.HorarioFuncionamentoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final EmpresaHorarioRepository empresaHorarioRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public EmpresaResponseDTO getEmpresa() {
        UUID usuarioId = getUsuarioIdAutenticado();
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da empresa não configurado"));
        return toResponse(empresa);
    }

    @Override
    @Transactional
    public EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId)
                .orElse(Empresa.builder().usuarioId(usuarioId).build());

        empresa.setNome(request.nome());
        empresa.setEmail(request.email());
        empresa.setWhatsapp(request.whatsapp());
        empresa.setEndereco(request.endereco());
        empresa.setLogoUrl(request.logoUrl());

        Empresa salva;
        try {
            // saveAndFlush (não save): o método virou @Transactional para a substituição de horários
            // abaixo ser atômica, e com isso um `save` só estouraria a violação de unicidade no
            // commit — fora deste try, devolvendo erro genérico de banco em vez da mensagem de
            // negócio abaixo. O flush força a violação a acontecer aqui dentro.
            salva = empresaRepository.saveAndFlush(empresa);
        } catch (DataIntegrityViolationException e) {
            // #142 — race condition entre duas chamadas concorrentes de upsert pro mesmo usuário
            // (nenhuma via encontrou a empresa existente antes de inserir); constraint real é a rede de
            // segurança, mensagem aqui evita vazar exception genérica de banco pro cliente.
            throw new BusinessException("Este usuário já possui uma empresa cadastrada.");
        }

        // #488 (V0.12.0) — substituição total do horário. `horarios` nulo preserva o que já existe
        // (permite salvar só os dados de contato sem mexer no horário); lista vazia apaga.
        if (request.horarios() != null) {
            substituirHorarios(salva.getId(), request.horarios());
        }

        return toResponse(salva);
    }

    /** RN do horário: dia sem repetição, e dia aberto exige abertura/fechamento coerentes. As mesmas
     *  regras existem como CHECK no banco (V47) — aqui rendem mensagem legível em vez de 500. */
    private void substituirHorarios(UUID empresaId, List<HorarioFuncionamentoRequestDTO> horarios) {
        Set<Integer> diasVistos = new HashSet<>();
        for (HorarioFuncionamentoRequestDTO h : horarios) {
            if (!diasVistos.add(h.diaSemana())) {
                throw new BusinessException("O mesmo dia da semana foi informado mais de uma vez.");
            }
            if (Boolean.TRUE.equals(h.fechado())) continue;

            if (h.horaAbertura() == null || h.horaFechamento() == null) {
                throw new BusinessException("Informe abertura e fechamento para os dias em que a empresa abre.");
            }
            if (!h.horaFechamento().isAfter(h.horaAbertura())) {
                throw new BusinessException("O horário de fechamento precisa ser depois do de abertura.");
            }
        }

        empresaHorarioRepository.deleteByEmpresaId(empresaId);
        empresaHorarioRepository.flush();
        empresaHorarioRepository.saveAll(horarios.stream()
                .map(h -> EmpresaHorario.builder()
                        .empresaId(empresaId)
                        .diaSemana(h.diaSemana())
                        .fechado(Boolean.TRUE.equals(h.fechado()))
                        .horaAbertura(Boolean.TRUE.equals(h.fechado()) ? null : h.horaAbertura())
                        .horaFechamento(Boolean.TRUE.equals(h.fechado()) ? null : h.horaFechamento())
                        .build())
                .toList());
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private EmpresaResponseDTO toResponse(Empresa empresa) {
        List<HorarioFuncionamentoResponseDTO> horarios =
                empresaHorarioRepository.findByEmpresaIdOrderByDiaSemanaAsc(empresa.getId()).stream()
                        .map(h -> new HorarioFuncionamentoResponseDTO(
                                h.getDiaSemana(), h.getFechado(), h.getHoraAbertura(), h.getHoraFechamento()))
                        .toList();

        return new EmpresaResponseDTO(
                empresa.getId(),
                empresa.getNome(),
                empresa.getEmail(),
                empresa.getWhatsapp(),
                empresa.getEndereco(),
                empresa.getLogoUrl(),
                horarios,
                empresa.getCreatedAt(),
                empresa.getUpdatedAt()
        );
    }
}
