package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ItemCatalogoRequest {

    @NotNull(message = "O produto é obrigatório")
    private UUID produtoId;

    @NotNull(message = "A quantidade de pacote é obrigatória")
    @Min(value = 1, message = "A quantidade de pacote deve ser pelo menos 1")
    private Integer quantidadePacote;

    /** Se nulo, o Service usa o precoSugerido calculado; se preenchido e diferente, aciona override (RN-038a). */
    private BigDecimal precoVenda;

    @Valid
    private List<CustomizacaoAnexadaRequest> customizacoesAnexadas = new ArrayList<>();
}
