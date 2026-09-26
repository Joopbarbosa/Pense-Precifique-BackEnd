package com.penseprecifique.api.shared.dto.request.caixa;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FecharCaixaTurnoRequestDTO(
        @NotNull(message = "O valor contado na gaveta é obrigatório")
        @DecimalMin(value = "0", message = "O valor contado não pode ser negativo")
        BigDecimal valorFechamentoInformado,

        /**
         * #488 (V0.12.0) — obrigatória (mín. 30 caracteres) SOMENTE quando o valor contado diverge
         * do esperado. Por ser condicional, a regra vive no Service, não como @Size aqui: o
         * fechamento que bate certinho continua em um clique.
         */
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
        String justificativa
) {}
