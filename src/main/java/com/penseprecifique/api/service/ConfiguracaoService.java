package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.ConfiguracaoRequestDTO;
import com.penseprecifique.api.dto.response.ConfiguracaoResponseDTO;

public interface ConfiguracaoService {
    ConfiguracaoResponseDTO getConfiguracao();
    ConfiguracaoResponseDTO upsertConfiguracao(ConfiguracaoRequestDTO request);
}
