package com.penseprecifique.api.shared.dto.request;

import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Size(min = 30, message = "A justificativa deve ter no mínimo 30 caracteres")
    private String justificativa;

    // RN-052 — mesma semântica de IniciarProducaoRequest.confirmarEstoqueNegativoInsumoIds, usada ao
    // avançar EM_PRODUCAO → FINALIZADO: ids dos produtos cujo estoque resultaria negativo
    // (permitirEstoqueNegativo=true) e cuja baixa o usuário já confirmou.
    private List<UUID> confirmarEstoqueNegativoProdutoIds;
}
