package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class UsuarioAtual {

    private final UsuarioRepository usuarioRepository;

    UUID id() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
