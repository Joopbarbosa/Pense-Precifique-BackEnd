package com.penseprecifique.api.shared.dto.request.agenda;

import com.penseprecifique.api.shared.domain.enums.EscopoEdicaoCompromisso;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** V0.18.0 (#1039, RN-NOVA-21) — cancelar uma ocorrência ou a série inteira; a observação tem no mínimo 30 caracteres. */
public record CancelarCompromissoRequest(
        EscopoEdicaoCompromisso escopo,
        @NotBlank(message = "A observação é obrigatória")
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String observacao
) {}
