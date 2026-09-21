package com.penseprecifique.api.unidademedida;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.unidademedida.UnidadeMedidaRequestDTO;
import com.penseprecifique.api.shared.dto.response.unidademedida.UnidadeMedidaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RN-NOVA-8/UC-NOVO-1 (V0.14.0, #298) — cadastro de unidades de medida em Configurações,
 * substituindo o texto livre antigo de {@code Insumo.unidadeMedida}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UnidadeMedidaService {

    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final InsumoRepository insumoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<UnidadeMedidaResponseDTO> listar() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return unidadeMedidaRepository.findByUsuarioIdAndDeletedAtIsNullOrderByNomeAsc(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    public UnidadeMedidaResponseDTO cadastrar(UnidadeMedidaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        validarUnicidade(usuarioId, request.nome(), request.sigla(), null);

        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        UnidadeMedida unidade = UnidadeMedida.builder()
                .usuario(usuario)
                .nome(request.nome())
                .sigla(request.sigla())
                .build();
        return toResponse(unidadeMedidaRepository.save(unidade));
    }

    public UnidadeMedidaResponseDTO editar(UUID id, UnidadeMedidaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        UnidadeMedida unidade = unidadeMedidaRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de medida não encontrada"));
        validarUnicidade(usuarioId, request.nome(), request.sigla(), id);

        unidade.setNome(request.nome());
        unidade.setSigla(request.sigla());
        return toResponse(unidadeMedidaRepository.save(unidade));
    }

    /** CEN-NOVO-9 — bloqueia se ao menos 1 insumo (ativo, não excluído) referenciar a unidade. */
    public void excluir(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        UnidadeMedida unidade = unidadeMedidaRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de medida não encontrada"));

        // CEN-NOVO-9 — mensagem literal do cenário confirmado (não é template por contagem).
        long emUso = insumoRepository.countByUnidadeMedidaIdAndDeletedAtIsNull(id);
        if (emUso > 0) {
            throw new BusinessException(
                    "Esta unidade está em uso por 1 ou mais insumos. Troque a unidade dos insumos vinculados antes de excluir.");
        }

        unidade.setDeletedAt(LocalDateTime.now());
        unidadeMedidaRepository.save(unidade);
    }

    private void validarUnicidade(UUID usuarioId, String nome, String sigla, UUID idAtual) {
        boolean nomeDuplicado = idAtual == null
                ? unidadeMedidaRepository.existsByUsuarioIdAndDeletedAtIsNullAndNomeIgnoreCase(usuarioId, nome)
                : unidadeMedidaRepository.existsByUsuarioIdAndDeletedAtIsNullAndNomeIgnoreCaseAndIdNot(usuarioId, nome, idAtual);
        if (nomeDuplicado) {
            throw new BusinessException("Já existe uma unidade de medida com este nome.");
        }
        boolean siglaDuplicada = idAtual == null
                ? unidadeMedidaRepository.existsByUsuarioIdAndDeletedAtIsNullAndSiglaIgnoreCase(usuarioId, sigla)
                : unidadeMedidaRepository.existsByUsuarioIdAndDeletedAtIsNullAndSiglaIgnoreCaseAndIdNot(usuarioId, sigla, idAtual);
        if (siglaDuplicada) {
            throw new BusinessException("Já existe uma unidade de medida com esta sigla.");
        }
    }

    private UnidadeMedidaResponseDTO toResponse(UnidadeMedida unidade) {
        return new UnidadeMedidaResponseDTO(
                unidade.getId(), unidade.getNome(), unidade.getSigla(),
                unidade.getCreatedAt(), unidade.getUpdatedAt());
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
