package com.penseprecifique.api.shared.dto.request.caixa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * #487 (retrabalho do teste manual, V0.12.0) — cancelar venda passou a exigir reautenticação.
 * A senha é a da usuária logada: impede que um caixa desatendido seja usado para cancelar venda,
 * sem derrubar a sessão nem o turno aberto.
 */
public record CancelarVendaCaixaRequestDTO(
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(min = 30, message = "O motivo do cancelamento deve ter pelo menos 30 caracteres")
        String cancelamentoMotivo,

        @NotBlank(message = "Confirme sua senha para cancelar a venda")
        String senha,

        /** Até a V0.12.0 o estoque sempre voltava, sem registro da decisão. Agora é escolha
         *  explícita da usuária (produto danificado/perdido não deve voltar para o estoque). */
        @NotNull(message = "Informe se o estoque deve voltar")
        Boolean retornarEstoque
) {}
