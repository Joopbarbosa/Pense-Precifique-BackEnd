package com.penseprecifique.api.service;

import com.penseprecifique.api.shared.dto.request.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.EmpresaResponseDTO;

public interface EmpresaService {
    EmpresaResponseDTO getEmpresa();
    EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request);
}
