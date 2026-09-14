package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** RN-NOVA-5 (V0.10.0, #450). */
@Getter
@Setter
public class DesagruparProducaoResponse {

    private List<ProducaoDetalheResponse> producoesNovas;
    private ProducaoDetalheResponse producaoOriginal;
}
