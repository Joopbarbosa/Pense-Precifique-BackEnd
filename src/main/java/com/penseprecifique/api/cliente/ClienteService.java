package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ClienteMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteServiceImpl implements ClienteService {

    // #354 — allowlist explícita dos campos de ordenação aceitos em GET /clientes. Campo fora desta
    // lista é rejeitado com BusinessException (400) por PageableOrdenacaoResolver, nunca mais
    // repassado cru pro Hibernate (UnknownPathException → 500).
    private static final Map<String, String> CAMPOS_ORDENACAO_CLIENTE = Map.of(
            "nome", "nome",
            "numero", "numero",
            "email", "email",
            "createdAt", "createdAt"
    );

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Pageable pageableOrdenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO_CLIENTE,
                "nome, numero, email, createdAt");

        Page<Cliente> pagina = (busca != null && !busca.isBlank())
                ? clienteRepository.findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(
                        usuarioId, busca, pageableOrdenado)
                : clienteRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageableOrdenado);

        Page<ClienteResponse> mapeado = pagina.map(clienteMapper::toResponse);
        return new PageImpl<>(mapeado.getContent(), pageable, mapeado.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Cliente cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + id));
        return clienteMapper.toResponse(cliente);
    }

    @Override
    public ClienteResponse cadastrar(ClienteRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Cliente cliente = clienteMapper.toEntity(request, usuario);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuario.getId());
        cliente.setNumero(NumeroSequencialUtil.proximoNumero(
                clienteRepository.findTopByUsuarioIdOrderByNumeroDesc(usuario.getId()).map(Cliente::getNumero)));
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    @Override
    public ClienteResponse editar(UUID id, ClienteRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Cliente cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + id));
        clienteMapper.updateEntity(request, cliente);
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    @Override
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Cliente cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + id));
        cliente.setAtiva(false);
        cliente.setDeletedAt(LocalDateTime.now());
        clienteRepository.save(cliente);
    }

    private UUID getUsuarioIdAutenticado() {
        return getUsuarioAutenticado().getId();
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
