package com.penseprecifique.api.shared.dto.request.insumo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * V0.16.0 (#687, RN-NOVA-18, UC-NOVO-4) — insumo em rascunho a partir do item da nota. O backend propõe
 * unidade (só se a sigla da nota coincidir com uma unidade cadastrada pela artesã) e custo
 * (valor final ÷ quantidade, só quando a unidade coincide).
 *
 * @param nome         nome do item da nota, editável pela artesã
 * @param unidadeNota  sigla da unidade como veio na nota (pode faltar)
 * @param quantidadeNota quantidade do item na nota
 * @param valorFinalNota valor final do item na nota (já com o desconto do item)
 */
public record InsumoRascunhoRequestDTO(
        @NotBlank(message = "O nome do insumo é obrigatório")
        @Size(max = 255, message = "O nome do insumo pode ter no máximo 255 caracteres")
        String nome,

        String marca,

        String unidadeNota,

        @DecimalMin(value = "0", inclusive = false, message = "A quantidade da nota deve ser maior que zero")
        BigDecimal quantidadeNota,

        @DecimalMin(value = "0", message = "O valor final da nota não pode ser negativo")
        BigDecimal valorFinalNota
) {}
