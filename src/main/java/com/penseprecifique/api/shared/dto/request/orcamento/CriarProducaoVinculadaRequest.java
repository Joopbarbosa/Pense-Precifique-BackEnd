package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * P-B020 (V0.8.2, #320) — cria uma produção nova já vinculada ao orçamento, com os produtos vindos
 * do próprio orçamento (não deste request) — mesmo motivo de {@code VincularProducaoRequest} não
 * carregar produtos: a fonte da verdade dos itens é sempre o orçamento.
 */
@Getter
@Setter
public class CriarProducaoVinculadaRequest {

    private LocalDate dataInicio;

    @NotNull(message = "A data de término prevista é obrigatória")
    private LocalDate dataTerminoPrevista;

    private String observacoes;
}
