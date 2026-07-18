package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoOrigemProducao;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProducaoDetalheResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private EstadoProducao estado;
    private LocalDate dataInicio;
    private LocalDate dataTerminoPrevista;
    private LocalDate dataTerminoReal;
    private String observacoes;
    private String justificativaCancelamento;
    private String justificativaNaoRealizada;
    private UUID producaoOrigemId;
    private TipoOrigemProducao tipoOrigem;

    private List<ProducaoProdutoResponse> produtos;
    private List<AlertaInsumoResponse> alertasInsumos;
    private List<InsumoConsumidoResponse> insumosConsumidos;
    private List<HistoricoStatusResponse> historicoStatus;

    // RN-073/UC-036 — produções geradas a partir desta (divisão ou agrupamento) via producaoOrigemId.
    private List<ProducaoResumoResponse> producoesFilhas;
}
