package com.penseprecifique.api.shared.dto.response.producao;

import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class HistoricoStatusResponse {

    private EstadoProducao statusAnterior;
    private EstadoProducao statusNovo;
    private LocalDateTime dataTransicao;
    private String justificativa;
    private OrigemHistoricoStatus origem;
    private TipoEventoHistoricoProducao tipoEvento;
    private UUID produtoId;
    private String nomeProduto;
    private BigDecimal quantidade;
    private UUID referenciaOrcamentoId;
    private String identificadorOrcamento;
}
