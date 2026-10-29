package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** RN-NOVA-42 (V0.18.0, #1169) — lote criado e AVISOS (nunca bloqueiam), ex.: data posterior ao prazo. */
@Getter
@Setter
public class JuntarProducoesResponse {

    private ProducaoDetalheResponse producaoNova;
    private List<ProducaoDetalheResponse> producoesOriginais;
    private List<String> avisos = List.of();
}
