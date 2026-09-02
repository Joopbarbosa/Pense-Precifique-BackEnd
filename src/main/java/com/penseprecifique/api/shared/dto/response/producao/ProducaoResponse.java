package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProducaoResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private EstadoProducao estado;
    private LocalDate dataInicio;
    private LocalDate dataTerminoPrevista;
    private String observacoes;
    private List<ProducaoProdutoResponse> produtos;
    private List<AlertaInsumoResponse> alertasInsumos;

    // #156 — mesmo formato de ProducaoDetalheResponse.historicoStatus; front usa pra distinguir
    // TRAVADA_USUARIO de TRAVADA_SISTEMA na listagem sem precisar abrir o detalhe (getBadgeEstado).
    private List<HistoricoStatusResponse> historicoStatus;

    // RN-NOVA-16 (V0.8.3, #375+308, P-B002) — mesmo campo/DTO de ProducaoDetalheResponse
    // (RN-NOVA-15), populado via query batched (findByProducaoIdIn), não findByProducaoId por linha.
    private List<ProducaoOrcamentoResponse> orcamentosVinculados;
}
