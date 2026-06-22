package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.dto.response.ImpactoAgregadoResponseDTO;

public interface LoteCompraService {

    ImpactoAgregadoResponseDTO registrarLote(RegistrarLoteCompraRequestDTO request);
}
