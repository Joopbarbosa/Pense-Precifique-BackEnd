package com.penseprecifique.api.shared.dto.response.agenda;

/** V0.18.0 (RN-NOVA-3) — faixa de atrasados e de hoje, independente do período exibido. */
public record AgendaResumoResponse(long atrasados, long hoje) {}
