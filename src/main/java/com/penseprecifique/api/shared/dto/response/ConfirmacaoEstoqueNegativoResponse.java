package com.penseprecifique.api.shared.dto.response;

import com.penseprecifique.api.shared.dto.response.producao.ProducaoResumoResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * RN-052 — retornado no lugar da resposta normal quando há componente com estoque negativo
 * permitido (permitirEstoqueNegativo=true) ainda não confirmado. Nada é salvo/baixado nesta chamada;
 * o chamador deve reenviar confirmando os itens listados para a baixa prosseguir.
 *
 * <p>RN-NOVA-20 (V0.8.3, #375+308, P-B004) — reaproveitado também para o aviso de vínculo órfão
 * (produção `CANCELADA`/`NAO_REALIZADA` ainda vinculada) na transição `EM_PRODUCAO → FINALIZADO`
 * de Orçamento: {@code vinculosOrfaos} coexiste com {@code avisos} na mesma resposta — os dois
 * avisos "pendente de confirmação" desta transição, sem um suprimir o outro. Reaproveita
 * {@code ProducaoResumoResponse} (id/identificador/estado) em vez de um DTO novo — mesmo shape já
 * usado por {@code producoesFilhas}.
 */
@Getter
@Setter
public class ConfirmacaoEstoqueNegativoResponse {

    private List<AvisoEstoqueNegativoResponse> avisos;
    private List<ProducaoResumoResponse> vinculosOrfaos;
}
