package com.penseprecifique.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoItemRequest {

    @NotNull
    private UUID produtoId;

    @NotNull
    @Min(1)
    private Integer quantidade;

    @Valid
    private List<OrcamentoItemCustomizacaoRequest> customizacoes = new ArrayList<>();
}
