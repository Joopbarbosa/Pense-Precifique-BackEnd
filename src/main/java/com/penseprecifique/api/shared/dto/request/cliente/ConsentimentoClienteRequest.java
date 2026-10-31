package com.penseprecifique.api.shared.dto.request.cliente;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ConsentimentoClienteRequest {
    @NotNull(message = "A data é obrigatória")
    private LocalDate data;

    @Size(min = 10, max = 200, message = "Informe de 10 a 200 caracteres")
    private String descricao;
}
