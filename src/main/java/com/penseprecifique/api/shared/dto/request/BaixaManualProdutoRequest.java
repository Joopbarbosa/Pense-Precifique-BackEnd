package com.penseprecifique.api.shared.dto.request;

import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BaixaManualProdutoRequest {

    @NotNull(message = "A quantidade é obrigatória")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O motivo é obrigatório")
    private MotivoMovimentacaoProduto motivo;

    @NotBlank(message = "A observação é obrigatória")
    @Size(min = 30, message = "A observação deve ter no mínimo 30 caracteres")
    private String observacao;
}
