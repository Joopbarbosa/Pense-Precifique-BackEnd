package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.auth.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.auth.AuthResponseDTO;
import com.penseprecifique.api.shared.dto.response.auth.UsuarioResponseDTO;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

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

    /**
     * #334 (RN-NOVA-1, DT-NOVA-13) — o token carrega o e-mail, então a troca devolve token novo no
     * mesmo formato do login. Não toca em {@code Empresa.email} (RN-NOVA-2).
     */
    @Override
    @Transactional
    public AuthResponseDTO alterarEmail(AlterarEmailRequestDTO request) {
        Usuario usuario = getUsuarioLogado();
        String novoEmail = request.novoEmail().trim();

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new BusinessException("Senha atual incorreta");
        }
        if (novoEmail.equalsIgnoreCase(usuario.getEmail())) {
            throw new BusinessException("O novo e-mail precisa ser diferente do atual");
        }
        if (usuarioRepository.existsByEmail(novoEmail)) {
            throw new BusinessException("E-mail já cadastrado");
        }

        usuario.setEmail(novoEmail);
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // corrida entre duas trocas/cadastros para o mesmo e-mail: a restrição única é a rede de segurança (RN-085)
            throw new BusinessException("E-mail já cadastrado");
        }

        String token = jwtTokenProvider.generateToken(usuario);
        return new AuthResponseDTO(token, usuario.getId(), usuario.getEmail(), expirationMs);
    }

    private Usuario getUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
