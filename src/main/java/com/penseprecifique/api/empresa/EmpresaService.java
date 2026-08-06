package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;

public interface EmpresaService {
    EmpresaResponseDTO getEmpresa();
    EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request);
}
