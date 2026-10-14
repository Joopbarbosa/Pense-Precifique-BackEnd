package com.penseprecifique.api.shared.dto.response.produto;

import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
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
    private String catalogoReferencia;
    private BigDecimal precoVendido;
    private boolean estornada;
    private LocalDateTime createdAt;

    /** #672/DT-NOVA-7 — MOVIMENTACAO (estoque) ou PRECO_SUGERIDO (mudança do preço sugerido; sem quantidade). */
    private String tipoLinha = "MOVIMENTACAO";
    private BigDecimal precoSugeridoAnterior;
    private BigDecimal precoSugeridoNovo;
    /** Causa da mudança do preço sugerido (COMPRA_ENTRADA, COMPRA_CANCELAMENTO, PRODUTO_EDICAO, INSUMO_SUBSTITUICAO, VALOR_HORA). */
    private String causaPrecoSugerido;
    /** Referência legível do evento (COM-12, PRO-3, INS-4); nula para o valor da hora. */
    private String referenciaTexto;
}
