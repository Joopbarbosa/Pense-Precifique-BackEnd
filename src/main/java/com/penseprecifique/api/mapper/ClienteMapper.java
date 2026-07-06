package com.penseprecifique.api.mapper;

import com.penseprecifique.api.domain.entity.Cliente;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.dto.request.ClienteRequest;
import com.penseprecifique.api.dto.response.ClienteResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClienteMapper {

    public ClienteResponse toResponse(Cliente cliente) {
        ClienteResponse response = new ClienteResponse();
        response.setId(cliente.getId());
        response.setNumero(cliente.getNumero());
        response.setNome(cliente.getNome());
        response.setEmail(cliente.getEmail());
        response.setWhatsapp(cliente.getWhatsapp());
        response.setEndereco(cliente.getEndereco());
        response.setObservacoes(cliente.getObservacoes());
        response.setAtiva(cliente.getAtiva());
        response.setCreatedAt(cliente.getCreatedAt());
        response.setUpdatedAt(cliente.getUpdatedAt());
        return response;
    }

    public Cliente toEntity(ClienteRequest request, Usuario usuario) {
        return Cliente.builder()
                .usuario(usuario)
                .nome(request.getNome())
                .email(request.getEmail())
                .whatsapp(request.getWhatsapp())
                .endereco(request.getEndereco())
                .observacoes(request.getObservacoes())
                .ativa(true)
                .build();
    }

    public void updateEntity(ClienteRequest request, Cliente cliente) {
        cliente.setNome(request.getNome());
        cliente.setEmail(request.getEmail());
        cliente.setWhatsapp(request.getWhatsapp());
        cliente.setEndereco(request.getEndereco());
        cliente.setObservacoes(request.getObservacoes());
        // ativa e deletedAt não são atualizados aqui
    }

    public List<ClienteResponse> toResponseList(List<Cliente> clientes) {
        return clientes.stream().map(this::toResponse).toList();
    }
}
