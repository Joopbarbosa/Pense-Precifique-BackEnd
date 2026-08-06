package com.penseprecifique.api.shared.dto.request.catalogo;

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
public class ItemCatalogoPreviewRequest {

    @NotNull(message = "O produto é obrigatório")
    private UUID produtoId;

    @NotNull(message = "A quantidade de pacote é obrigatória")
    @Min(value = 1, message = "A quantidade de pacote deve ser pelo menos 1")
    private Integer quantidadePacote;

    @Valid
    private List<CustomizacaoAnexadaRequest> customizacoesAnexadas = new ArrayList<>();
}
