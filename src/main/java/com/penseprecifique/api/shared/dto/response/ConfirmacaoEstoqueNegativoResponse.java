package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * RN-052 — retornado no lugar da resposta normal quando há componente com estoque negativo
 * permitido (permitirEstoqueNegativo=true) ainda não confirmado. Nada é salvo/baixado nesta chamada;
 * o chamador deve reenviar confirmando os itens listados para a baixa prosseguir.
 */
@Getter
@Setter
public class ConfirmacaoEstoqueNegativoResponse {

    private List<AvisoEstoqueNegativoResponse> avisos;
}
