package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// RN-NOVA-5/DT-NOVA-4 (V0.14.0, #534 — réplica de #514) — "Baixa manual" generalizada para
// "Edição manual": campo tipo (ENTRADA/SAIDA) decide a direção; mesma validação de motivo/
// observação para as duas direções, evita duplicar endpoint espelho.
@Getter
@Setter
public class BaixaManualProdutoRequest {

    @NotNull(message = "O tipo é obrigatório")
    private TipoMovimentacaoProduto tipo;

    @NotNull(message = "A quantidade é obrigatória")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O motivo é obrigatório")
    private MotivoMovimentacaoProduto motivo;

    @NotBlank(message = "A observação é obrigatória")
    @Size(min = 30, message = "A observação deve ter no mínimo 30 caracteres")
    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String observacao;
}
