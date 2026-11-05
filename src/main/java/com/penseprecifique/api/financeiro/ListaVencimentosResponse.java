package com.penseprecifique.api.financeiro;

import java.util.List;

/** V0.19.0 (#1176, RN-NOVA-21) — «Ver todas»: vencidas e próximos 7 dias, pagar e receber juntos, na ordem estável. */
public record ListaVencimentosResponse(List<ParcelaFinanceiraResponse> itens, long total, int pagina, int tamanhoPagina) {
}
