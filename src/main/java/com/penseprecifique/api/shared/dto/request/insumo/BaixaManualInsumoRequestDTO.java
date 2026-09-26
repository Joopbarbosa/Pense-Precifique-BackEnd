package com.penseprecifique.api.shared.dto.request.insumo;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// RN-NOVA-5/DT-NOVA-4 (V0.14.0, #514) — "Baixa manual" generalizada para "Edição manual": campo
// tipo (ENTRADA/SAIDA) decide a direção; mesmo endpoint, mesma validação de motivo/observação
// (INS-007) para as duas direções — evita duplicar a validação num endpoint espelho.
public record BaixaManualInsumoRequestDTO(

        @NotNull(message = "O tipo é obrigatório")
        TipoMovimentacaoInsumo tipo,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @NotNull(message = "O motivo é obrigatório")
        MotivoMovimentacaoInsumo motivo,

        @NotBlank(message = "A observação é obrigatória")
        @Size(min = 30, message = "A observação deve ter no mínimo 30 caracteres")
        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
        String observacao
) {}
