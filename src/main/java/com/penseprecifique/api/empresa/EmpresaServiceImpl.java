package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.dto.request.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.EmpresaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public EmpresaResponseDTO getEmpresa() {
        UUID usuarioId = getUsuarioIdAutenticado();
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da empresa não configurado"));
        return toResponse(empresa);
    }

    @Override
    public EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId)
                .orElse(Empresa.builder().usuarioId(usuarioId).build());

        empresa.setNome(request.nome());
        empresa.setEmail(request.email());
        empresa.setWhatsapp(request.whatsapp());
        empresa.setEndereco(request.endereco());
        empresa.setLogoUrl(request.logoUrl());

        return toResponse(empresaRepository.save(empresa));
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private EmpresaResponseDTO toResponse(Empresa empresa) {
        return new EmpresaResponseDTO(
                empresa.getId(),
                empresa.getNome(),
                empresa.getEmail(),
                empresa.getWhatsapp(),
                empresa.getEndereco(),
                empresa.getLogoUrl(),
                empresa.getCreatedAt(),
                empresa.getUpdatedAt()
        );
    }
}
