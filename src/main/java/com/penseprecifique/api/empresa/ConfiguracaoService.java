package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.dto.request.config.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.ConfiguracaoResponseDTO;

public interface ConfiguracaoService {
    ConfiguracaoResponseDTO getConfiguracao();
    ConfiguracaoResponseDTO upsertConfiguracao(ConfiguracaoRequestDTO request);
}
