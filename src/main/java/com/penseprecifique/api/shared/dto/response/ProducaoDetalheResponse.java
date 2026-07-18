package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoOrigemProducao;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // Cancelamento do fluxo legado (RN-anterior, mantido para produções ATIVA/CANCELADA antigas)
    private String observacaoCancelamento;
    private LocalDateTime dataCancelamento;

    private List<ProducaoProdutoResponse> produtos;
    private List<AlertaInsumoResponse> alertasInsumos;
    private List<InsumoConsumidoResponse> insumosConsumidos;
}
