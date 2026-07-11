package com.penseprecifique.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LancarProducaoLoteRequest {

    // RN-060 — cada item reaproveita os mesmos campos de POST /producoes; a validação de
    // estoque combinado (por componente somado entre todos os itens) acontece no Service.
    @NotEmpty(message = "Informe ao menos uma produção para lançar")
    @Valid
    private List<LancarProducaoRequest> producoes;
}
