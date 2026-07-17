package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.ConfiguracaoResponseDTO;

public interface ConfiguracaoService {
    ConfiguracaoResponseDTO getConfiguracao();
    ConfiguracaoResponseDTO upsertConfiguracao(ConfiguracaoRequestDTO request);
}
