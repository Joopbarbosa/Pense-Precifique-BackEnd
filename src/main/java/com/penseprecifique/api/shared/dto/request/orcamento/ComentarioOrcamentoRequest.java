package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** V0.18.0 (#1038, RN-NOVA-22) — POST /orcamentos/{id}/comentarios: texto de 1 a 500 caracteres; o limite é conferido no serviço, depois de aparar as pontas. */
public record ComentarioOrcamentoRequest(
        @NotBlank(message = "Escreva o comentário")
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String texto
) {}
