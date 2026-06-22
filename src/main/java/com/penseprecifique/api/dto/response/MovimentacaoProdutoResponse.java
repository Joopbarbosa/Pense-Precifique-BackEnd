package com.penseprecifique.api.dto.response;

import com.penseprecifique.api.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoProduto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class MovimentacaoProdutoResponse {

    private UUID id;
    private TipoMovimentacaoProduto tipo;
    private MotivoMovimentacaoProduto motivo;
    private BigDecimal quantidade;
    private String observacao;
    private UUID referenciaId;
    private String referenciaTipo;
    private boolean estornada;
    private LocalDateTime createdAt;
}
