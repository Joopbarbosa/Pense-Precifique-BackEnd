package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.cliente.ConsentimentoClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.EventoConsentimentoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteConsentimentoService {
    private final ClienteRepository clienteRepository;
    private final ClienteConsentimentoEventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;

    public void registrarRecusa(UUID clienteId, ConsentimentoClienteRequest request) {
        Usuario usuario = usuarioAtual();
        Cliente cliente = buscar(clienteId, usuario.getId());
        validarData(request.getData());
        cliente.setAceitaWhatsapp(false);
        cliente.setAceitaWhatsappEm(null);
        cliente.setAceitaWhatsappPor(null);
        cliente.setRecusouWhatsapp(true);
        cliente.setRecusouWhatsappEm(request.getData());
        cliente.setRecusouWhatsappPor(usuario);
        clienteRepository.save(cliente);
        gravar(cliente, usuario, TipoEventoConsentimento.RECUSA, request.getData(), null);
    }

    public void registrarAceite(UUID clienteId, ConsentimentoClienteRequest request) {
        Usuario usuario = usuarioAtual();
        Cliente origem = buscar(clienteId, usuario.getId());
        validarData(request.getData());
        String descricao = request.getDescricao() == null ? null : request.getDescricao().strip();
        List<Cliente> mesmoNumero = origem.getWhatsappCanonico() == null
                ? List.of(origem)
                : clienteRepository.findByUsuarioIdAndWhatsappCanonico(usuario.getId(), origem.getWhatsappCanonico());
        boolean numeroRecusado = mesmoNumero.stream().anyMatch(c -> Boolean.TRUE.equals(c.getRecusouWhatsapp()));
        if (numeroRecusado && (descricao == null || descricao.length() < 10 || descricao.length() > 200)) {
            throw new BusinessException("Descreva como o destinatário voltou a aceitar (de 10 a 200 caracteres).");
        }
        List<Cliente> afetados = mesmoNumero.stream()
                .filter(c -> c.getId().equals(origem.getId()) || Boolean.TRUE.equals(c.getRecusouWhatsapp())).toList();
        for (Cliente cliente : afetados) {
            cliente.setAceitaWhatsapp(true);
            cliente.setAceitaWhatsappEm(request.getData());
            cliente.setAceitaWhatsappPor(usuario);
            cliente.setRecusouWhatsapp(false);
            cliente.setRecusouWhatsappEm(null);
            cliente.setRecusouWhatsappPor(null);
            clienteRepository.save(cliente);
            gravar(cliente, usuario, TipoEventoConsentimento.ACEITE, request.getData(), descricao);
        }
    }

    @Transactional(readOnly = true)
    public List<EventoConsentimentoResponse> historico(UUID clienteId) {
        Cliente cliente = buscar(clienteId, usuarioAtual().getId());
        return eventoRepository.findByClienteIdOrderByRegistradoEmDesc(cliente.getId()).stream()
                .map(e -> new EventoConsentimentoResponse(e.getId(), e.getTipo(), e.getDataInformada(), e.getDescricao(), e.getRegistradoEm()))
                .toList();
    }

    private void gravar(Cliente cliente, Usuario usuario, TipoEventoConsentimento tipo, LocalDate data, String descricao) {
        ClienteConsentimentoEvento evento = new ClienteConsentimentoEvento();
        evento.setCliente(cliente);
        evento.setUsuario(usuario);
        evento.setTipo(tipo);
        evento.setDataInformada(data);
        evento.setDescricao(descricao);
        eventoRepository.save(evento);
    }

    private void validarData(LocalDate data) {
        if (data == null || data.isBefore(LocalDate.of(2000, 1, 1)) || data.isAfter(LocalDate.of(2100, 12, 31))) {
            throw new BusinessException("A data deve estar entre 01/01/2000 e 31/12/2100.");
        }
    }

    private Cliente buscar(UUID id, UUID usuarioId) {
        return clienteRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadastro não encontrado: " + id));
    }

    private Usuario usuarioAtual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
