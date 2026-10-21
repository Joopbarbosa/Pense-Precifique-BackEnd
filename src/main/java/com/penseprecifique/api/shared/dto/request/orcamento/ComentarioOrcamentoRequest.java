package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** V0.18.0 (#1038, RN-NOVA-22) — POST /orcamentos/{id}/comentarios: texto de 1 a 500 caracteres (aparado nas pontas). */
public record ComentarioOrcamentoRequest(
        @NotBlank(message = "Escreva o comentário")
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String texto
) {}
