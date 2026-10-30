package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** RN-NOVA-49 (V0.18.0, #1184) — produções que caem entre a aprovação do orçamento e a entrega estimada dele. */
@Getter
@Setter
public class ProducoesPeriodoResponse {

    private boolean periodoDisponivel;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private List<CandidataJuntarResponse> producoes;
}
