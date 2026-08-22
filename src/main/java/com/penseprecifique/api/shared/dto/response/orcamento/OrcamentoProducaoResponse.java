package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoProducaoResponse {

    private UUID id;
    private UUID producaoId;
    private String identificadorProducao;
    private LocalDateTime createdAt;
}
