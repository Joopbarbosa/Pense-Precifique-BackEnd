package com.penseprecifique.api.shared.dto.request.compra;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * #544/RN-NOVA-9 (V0.15.0) — cancelar compra CONFIRMADA. Observação obrigatória, mínimo 30 caracteres
 * (padrão INS-007). {@code confirmarManterCusto}: a artesã já viu o AVISO de insumos que manterão o
 * custo atual (POST /compras/{id}/simular-cancelamento) e confirma.
 */
public record CancelarCompraRequest(
        @NotBlank(message = "A observação é obrigatória")
        @Size(min = 30, message = "A observação precisa ter pelo menos 30 caracteres")
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String observacao,

        Boolean confirmarManterCusto
) {}
