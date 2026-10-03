package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClienteMapper {

    public ClienteResponse toResponse(Cliente cliente) {
        ClienteResponse response = new ClienteResponse();
        response.setId(cliente.getId());
        response.setNumero(cliente.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("CLI", cliente.getNumero()));
        response.setNome(cliente.getNome());
        response.setEmail(cliente.getEmail());
        response.setWhatsapp(cliente.getWhatsapp());
        response.setTelefone(cliente.getTelefone());
        response.setSite(cliente.getSite());
        response.setEndereco(cliente.getEndereco());
        response.setObservacoes(cliente.getObservacoes());
        response.setEhCliente(Boolean.TRUE.equals(cliente.getEhCliente()));
        response.setEhFornecedor(Boolean.TRUE.equals(cliente.getEhFornecedor()));
        response.setTipoPessoa(cliente.getTipoPessoa());
        response.setDocumento(cliente.getDocumento());
        response.setAtiva(cliente.getAtiva());
        response.setCreatedAt(cliente.getCreatedAt());
        response.setUpdatedAt(cliente.getUpdatedAt());
        return response;
    }

    public Cliente toEntity(ClienteRequest request, Usuario usuario) {
        Cliente cliente = Cliente.builder().usuario(usuario).ativa(true).build();
        updateEntity(request, cliente);
        return cliente;
    }

    public void updateEntity(ClienteRequest request, Cliente cliente) {
        cliente.setNome(request.getNome());
        cliente.setEmail(request.getEmail());
        cliente.setWhatsapp(request.getWhatsapp());
        cliente.setTelefone(request.getTelefone());
        cliente.setSite(request.getSite());
        cliente.setEndereco(request.getEndereco());
        cliente.setObservacoes(request.getObservacoes());
        cliente.setEhCliente(Boolean.TRUE.equals(request.getEhCliente()));
        cliente.setEhFornecedor(Boolean.TRUE.equals(request.getEhFornecedor()));
        cliente.setTipoPessoa(request.getTipoPessoa() != null ? request.getTipoPessoa() : TipoPessoa.FISICA);
        // documento: normalizado e validado pelo ClienteService antes de chegar aqui
        cliente.setDocumento(request.getDocumento());
        // ativa não é atualizado aqui (só por inativar/reativar)
    }

    public List<ClienteResponse> toResponseList(List<Cliente> clientes) {
        return clientes.stream().map(this::toResponse).toList();
    }
}
