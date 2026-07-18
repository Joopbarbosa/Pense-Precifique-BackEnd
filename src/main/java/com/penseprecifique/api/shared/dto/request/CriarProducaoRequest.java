package com.penseprecifique.api.shared.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CriarProducaoRequest {

    private LocalDate dataInicio;

    @NotNull(message = "A data de término prevista é obrigatória")
    private LocalDate dataTerminoPrevista;

    private String observacoes;

    @NotEmpty(message = "Informe ao menos um produto")
    @Valid
    private List<ProducaoProdutoRequest> produtos;
}
