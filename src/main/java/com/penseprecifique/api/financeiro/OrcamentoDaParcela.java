package com.penseprecifique.api.financeiro;

import java.util.UUID;

/** V0.19.0 (#1174) — a que orçamento e a que papel (Sinal, Saldo, Total) pertence uma parcela a receber. */
public record OrcamentoDaParcela(UUID orcamentoId, PapelParcela papel) {
}
