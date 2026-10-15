package com.penseprecifique.api.shared.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroRequestDTO(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @io.swagger.v3.oas.annotations.media.Schema(minLength = 8)
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        String senha,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        String confirmarSenha,

        /** #339 (RN-NOVA-3) — opcional para não quebrar clientes antigos; o frontend sempre envia. */
        @Size(max = 255, message = "O nome da empresa deve ter no máximo 255 caracteres")
        String nomeEmpresa
) {}
