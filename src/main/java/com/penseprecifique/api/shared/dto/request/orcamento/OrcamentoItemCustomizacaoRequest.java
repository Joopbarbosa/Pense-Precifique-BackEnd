package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrcamentoItemCustomizacaoRequest {

    @NotNull
    private UUID produtoId;

    @NotNull
    @Min(1)
    private Integer quantidade;
}
