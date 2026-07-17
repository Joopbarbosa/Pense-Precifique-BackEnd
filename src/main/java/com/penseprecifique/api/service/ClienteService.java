package com.penseprecifique.api.service;

import com.penseprecifique.api.shared.dto.request.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.ClienteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClienteService {

    Page<ClienteResponse> listar(String nome, Pageable pageable);

    ClienteResponse buscarPorId(UUID id);

    ClienteResponse cadastrar(ClienteRequest request);

    ClienteResponse editar(UUID id, ClienteRequest request);

    void inativar(UUID id);
}
