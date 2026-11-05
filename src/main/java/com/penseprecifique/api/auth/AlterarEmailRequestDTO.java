package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.texto.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** #334 (RN-NOVA-1) — troca do e-mail de acesso: novo e-mail e senha atual. */
public record AlterarEmailRequestDTO(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail muito longo")
        String novoEmail,

        @NotBlank(message = "Senha atual é obrigatória")
        @Size(max = 200, message = "Senha atual incorreta")
        String senhaAtual
) {

    /** #699 — o novo e-mail chega aparado e em minúsculas antes de a validação rodar. */
    public AlterarEmailRequestDTO {
        novoEmail = EmailNormalizer.normalizar(novoEmail);
    }
}
