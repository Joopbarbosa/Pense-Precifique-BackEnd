package com.penseprecifique.api.shared.dto.request.orcamento;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #382 (RN-NOVA-44) — criar/editar o orçamento Descritivo. Cliente opcional; de 1 a 100 opções (limite validado no
 * serviço, com a mensagem "Informe de 1 a 100 opções."); sem total, desconto, sinal nem forma de pagamento.
 */
public record DescritivoRequest(
        @NotBlank(message = "Informe o título do orçamento.")
        @Size(max = 200, message = "Máximo de 200 caracteres")
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String titulo,
        UUID clienteId,
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String observacoes,
        @Valid List<Opcao> opcoes) {

    /** Opção = Produto OU Item de catálogo (XOR, validado no serviço); preço de referência padrão = preço de venda atual. */
    public record Opcao(
            UUID produtoId,
            UUID itemCatalogoId,
            @DecimalMin(value = "0.00", message = "O preço de referência não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Preço de referência inválido (até 2 casas decimais).")
            BigDecimal precoReferencia,
            @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
            @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
            String quantidadeUnidade) {
    }
}
