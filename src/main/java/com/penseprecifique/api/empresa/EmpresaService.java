package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.EmpresaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.EmpresaResponseDTO;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface EmpresaService {
    EmpresaResponseDTO getEmpresa();
    EmpresaResponseDTO upsertEmpresa(EmpresaRequestDTO request);
    // #532 (DT-NOVA-5) — logoUrl já existe/já é gravável via upsertEmpresa; só faltava upload de arquivo.
    EmpresaResponseDTO uploadLogo(MultipartFile arquivo);
    EmpresaResponseDTO removerLogo();
    /** #339 (DT-NOVA-14) — chamado pelo cadastro, na mesma transação que cria o usuário (só o nome). */
    void criarEmpresaInicial(UUID usuarioId, String nome);
}
