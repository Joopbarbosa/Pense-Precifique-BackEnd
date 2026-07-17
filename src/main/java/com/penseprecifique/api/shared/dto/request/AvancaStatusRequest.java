package com.penseprecifique.api.shared.dto.request;

import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class AvancaStatusRequest {

    private MetodoPagamento metodoSinalRecebido;
    private String metodoSinalRecebidoObs;

    private String motivoCancelamento;
    private TipoCancelamento tipoCancelamento;
    private BigDecimal percentualMulta;
    private boolean estornarSinal;
    private LocalDateTime dataEstornoSinal;

    @Size(min = 50, message = "A justificativa deve ter no mínimo 50 caracteres")
    private String justificativa;
}
