package com.penseprecifique.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmpresaRequestDTO(
        @NotBlank(message = "O nome da empresa é obrigatório")
        String nome,

        @Email(message = "E-mail inválido")
        String email,

        String whatsapp,
        String endereco,
        String logoUrl
) {}
