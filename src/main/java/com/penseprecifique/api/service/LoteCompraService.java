package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.dto.response.ImpactoAgregadoResponseDTO;

import java.util.UUID;

public interface LoteCompraService {

    ImpactoAgregadoResponseDTO registrarLote(RegistrarLoteCompraRequestDTO request, UUID usuarioId);
}
