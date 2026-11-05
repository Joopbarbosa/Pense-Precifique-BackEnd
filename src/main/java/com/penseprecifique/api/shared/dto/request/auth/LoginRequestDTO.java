package com.penseprecifique.api.shared.dto.request.auth;

import com.penseprecifique.api.shared.texto.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail muito longo")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 200, message = "E-mail ou senha inválidos")
        String senha
) {

    /** #699 — o e-mail chega aparado e em minúsculas antes de a validação rodar. */
    public LoginRequestDTO {
        email = EmailNormalizer.normalizar(email);
    }
}
