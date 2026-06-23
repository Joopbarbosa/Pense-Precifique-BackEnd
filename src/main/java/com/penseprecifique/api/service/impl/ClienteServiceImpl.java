package com.penseprecifique.api.service.impl;

import com.penseprecifique.api.domain.entity.Cliente;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.dto.request.ClienteRequest;
import com.penseprecifique.api.dto.response.ClienteResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.ClienteMapper;
import com.penseprecifique.api.repository.ClienteRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import com.penseprecifique.api.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String nome, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        if (nome != null && !nome.isBlank()) {
            return clienteRepository
                    .findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, nome, pageable)
                    .map(clienteMapper::toResponse);
        }
        return clienteRepository
                .findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable)
                .map(clienteMapper::toResponse);
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
