package com.penseprecifique.api.shared.dto.request.orcamento;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * P-B020 (V0.8.2, #320) — cria uma produção nova já vinculada ao orçamento, com os produtos vindos
 * do próprio orçamento (não deste request) — mesmo motivo de {@code VincularProducaoRequest} não
 * carregar produtos: a fonte da verdade dos itens é sempre o orçamento.
 *
 * <p>RN-NOVA-13 (V0.8.3, #375+308) — {@code produtoIds} é a extensão de contrato compartilhada com
 * RN-NOVA-25 (#319+387, checkbox unificado no Detalhe): {@code null}/ausente preserva o
 * comportamento padrão (todos os itens do orçamento, consumido hoje por
 * {@code ModalVincularProducao}/{@code modoCriarNova}); uma lista explícita restringe a produção
 * criada a só os produtos marcados (identificados por {@code produtoId}, mesma chave usada pelo
 * card de estoque insuficiente — não {@code orcamentoItemId}). Lista vazia explícita ({@code []})
 * é rejeitada, distinta de {@code null} — ver {@code OrcamentoService#criarProducaoVinculada}.
 */
@Getter
@Setter
public class CriarProducaoVinculadaRequest {

    private LocalDate dataInicio;

    @NotNull(message = "A data de término prevista é obrigatória")
    private LocalDate dataTerminoPrevista;

    private String observacoes;

    private List<UUID> produtoIds;
}
