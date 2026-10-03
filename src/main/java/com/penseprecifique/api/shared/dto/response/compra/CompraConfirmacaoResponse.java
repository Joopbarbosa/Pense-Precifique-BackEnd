package com.penseprecifique.api.shared.dto.response.compra;

/** #541/#543 (V0.15.0) — resposta de confirmar/cancelar: a compra + o modal de impacto (RN-NOVA-8). */
public record CompraConfirmacaoResponse(CompraResponse compra, ImpactoCompraResponse impacto) {}
