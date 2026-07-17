package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.EmpresaResponseDTO;

public interface EmpresaService {
    EmpresaResponseDTO getEmpresa();
    EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request);
}
