package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface EmpresaService {
    EmpresaResponseDTO getEmpresa();
    EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request);
    // #532 (DT-NOVA-5) — logoUrl já existe/já é gravável via upsertEmpresa; só faltava upload de arquivo.
    EmpresaResponseDTO uploadLogo(MultipartFile arquivo);
    EmpresaResponseDTO removerLogo();
}
