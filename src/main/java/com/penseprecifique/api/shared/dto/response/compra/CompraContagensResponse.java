package com.penseprecifique.api.shared.dto.response.compra;

/**
 * #591/RN-NOVA-44 — contagens dos filtros de Minhas compras (total da conta). #958/RN-NOVA-6: pendentes de
 * recebimento e, entre elas, as atrasadas (previsão anterior a hoje).
 */
public record CompraContagensResponse(long todas, long rascunhos, long confirmadas, long canceladas,
                                      long pendentes, long atrasadas) {}
