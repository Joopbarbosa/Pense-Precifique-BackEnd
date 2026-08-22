package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClienteService {

    Page<ClienteResponse> listar(String busca, Pageable pageable);

    ClienteResponse buscarPorId(UUID id);

    ClienteResponse cadastrar(ClienteRequest request);

    ClienteResponse editar(UUID id, ClienteRequest request);

    void inativar(UUID id);
}
