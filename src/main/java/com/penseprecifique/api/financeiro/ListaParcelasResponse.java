package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.util.List;

/** V0.19.0 (RN-NOVA-4) — página de parcelas mais os totais Em aberto e vencido do período filtrado; {@code vazioDeLancamentos} = a empresa ainda não tem nenhum lançamento (RN-NOVA-6). */
public record ListaParcelasResponse(List<ParcelaFinanceiraResponse> itens, long total, int pagina, int tamanhoPagina,
                                    BigDecimal totalEmAberto, BigDecimal totalVencido, boolean vazioDeLancamentos) {
}
