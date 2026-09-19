package com.penseprecifique.api.auth;

import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.auth.CadastroRequestDTO;
import com.penseprecifique.api.shared.dto.request.auth.LoginRequestDTO;
import com.penseprecifique.api.shared.dto.response.auth.AuthResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MetodoPagamentoConfiguravelService metodoPagamentoService;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Override
    @Transactional
    public AuthResponseDTO register(CadastroRequestDTO request) {
        if (!request.senha().equals(request.confirmarSenha())) {
            throw new BusinessException("As senhas não coincidem");
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado");
        }

        Usuario usuario = Usuario.builder()
                .email(request.email())
                .senhaHash(passwordEncoder.encode(request.senha()))
                .ativo(true)
                .build();

        usuarioRepository.save(usuario);
        // DT-NOVA-6 — seed eager (não lazy como Empresa/ConfiguracaoPrecificacao, ver AUT-004):
        // Caixa exige pelo menos 1 método existente na primeira venda.
        metodoPagamentoService.seedMetodosPadrao(usuario);

        String token = jwtTokenProvider.generateToken(usuario);
        return new AuthResponseDTO(token, usuario.getId(), usuario.getEmail(), expirationMs);
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new BusinessException("E-mail ou senha inválidos");
        }
        if (!usuario.getAtivo()) {
            throw new BusinessException("Usuário inativo. Contate o suporte.");
        }

        String token = jwtTokenProvider.generateToken(usuario);
        return new AuthResponseDTO(token, usuario.getId(), usuario.getEmail(), expirationMs);
    }
}
