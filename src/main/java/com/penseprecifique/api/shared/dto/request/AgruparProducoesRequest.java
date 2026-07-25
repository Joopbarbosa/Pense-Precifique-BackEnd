package com.penseprecifique.api.shared.dto.request;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class AgruparProducoesRequest {

    @NotEmpty(message = "Selecione ao menos 2 produções para agrupar")
    @Size(min = 2, message = "Selecione ao menos 2 produções para agrupar")
    private List<UUID> producaoIds;

    @NotNull(message = "O estado de destino é obrigatório")
    private EstadoProducao estadoDestino;

    // Se null, herda da produção com dataInicio mais recente entre as originais.
    private LocalDate dataInicio;

    // Se null, herda da produção com dataInicio mais recente entre as originais.
    private LocalDate dataTerminoPrevista;

    // RN-074 — obrigatória (por produção) se a produção estava EM_ANDAMENTO ou TRAVADA; mesma semântica
    // de CancelarProducaoRequest.consumoReal, mas por produção de origem.
    @Valid
    private Map<UUID, List<ConsumoRealRequest>> consumoRealPorProducao;

    @NotBlank(message = "A justificativa é obrigatória")
    @Size(min = 30, message = "Justificativa deve ter no mínimo 30 caracteres")
    private String justificativa;

    // RN-052 — mesma semântica de IniciarProducaoRequest.confirmarEstoqueNegativoInsumoIds, usada quando
    // estadoDestino=EM_ANDAMENTO baixa insumo dos produtos consolidados da nova produção.
    private List<UUID> confirmarEstoqueNegativoInsumoIds;
}
