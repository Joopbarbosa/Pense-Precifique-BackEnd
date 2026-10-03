package com.penseprecifique.api.shared.dto.response.compra;

/** #591/RN-NOVA-44 (V0.15.0) — contagem dos filtros de Minhas compras (total da conta). */
public record CompraContagensResponse(long todas, long rascunhos, long confirmadas, long canceladas) {}
