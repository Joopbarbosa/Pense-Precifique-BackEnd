package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AgruparProducoesResponse {

    private ProducaoDetalheResponse producaoNova;
    private List<ProducaoDetalheResponse> producoesOriginais;
}
