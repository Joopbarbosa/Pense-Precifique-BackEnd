package com.penseprecifique.api.service.impl;

import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.UsuarioResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.repository.UsuarioRepository;
import com.penseprecifique.api.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UsuarioResponseDTO getUsuarioAutenticado() {
        Usuario usuario = getUsuarioLogado();
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getAtivo(),
                usuario.getCreatedAt()
        );
    }

    @Override
    public void alterarSenha(AlterarSenhaRequestDTO request) {
        Usuario usuario = getUsuarioLogado();

        if (!request.novaSenha().equals(request.confirmarNovaSenha())) {
            throw new BusinessException("As senhas não coincidem");
        }
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new BusinessException("Senha atual incorreta");
        }

        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarioRepository.save(usuario);
    }

    private Usuario getUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
