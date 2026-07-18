package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DivisaoProducaoResponse {

    private ProducaoDetalheResponse producaoOriginal;
    private ProducaoDetalheResponse producaoA;
    private ProducaoDetalheResponse producaoB;
}
