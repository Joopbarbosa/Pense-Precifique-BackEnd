package com.penseprecifique.api.shared.dto.response.catalogo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ItemCatalogoBuscaResponse {

    private UUID id;
    /** RN-NOVA-23 (#313) — id do Catálogo dono do item, necessário para o Frontend chamar
     *  GET /catalogos/{catalogoId}/itens e montar a calculadora de preço. */
    private UUID catalogoId;
    /** V0.13.0 (RN-NOVA-1) — nome próprio do item; antes era o nome do único produto do item, que
     *  deixou de existir. */
    private String nome;
    private BigDecimal precoVenda;
    private String catalogoNome;
    private Integer catalogoNumero;
    /** V0.13.0 (RN-NOVA-1) — substitui {@code produtoId} + {@code customizacoesFixas}: o item agora
     *  pode ter N componentes (Produto, Customização ou Insumo), não mais 1 produto + customizações
     *  fixas. Consumidores existentes (Orçamento/Caixa) precisam ler esta lista em vez de um único
     *  produto — achado de contrato registrado em decisoes-catalogo.md. */
    private List<ItemCatalogoComponenteResponse> componentes;
    /** #238/DECISOES_GLOBAIS — tag global fracionável, generalizada de "algum insumo da ficha técnica
     *  do produto do item" para "algum componente Insumo deste item é não-fracionável" (V0.13.0). */
    private boolean algumComponenteNaoFracionavel;
    /** OpenProject #527 — true quando algum componente (Insumo ou Produto-base) está com
     *  {@code estoqueAtual <= 0} e {@code permitirEstoqueNegativo == false} (bloqueado pra venda
     *  agora, mesmo critério de "bloqueio duro" usado no resto do sistema). Não considera a
     *  quantidade que a usuária ainda vai digitar no carrinho — isso só se sabe depois de
     *  adicionar (painel `avisosEstoque`), este campo é só o sinal grosseiro pra busca. */
    private boolean algumComponenteSemEstoque;
}
