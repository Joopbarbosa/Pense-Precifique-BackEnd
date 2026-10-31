package com.penseprecifique.api.financeiro;

import java.util.List;
import java.util.UUID;

/** Pedido de criação do lançamento (uso interno: Compra, Orçamento e conta avulsa chamam o serviço). Origem COMPRA exige compraId; ORCAMENTO, orcamentoId; AVULSO, nenhum. */
public record NovoLancamento(TipoLancamento tipo, String descricao, OrigemLancamento origem, UUID compraId, UUID orcamentoId,
                             List<NovaParcela> parcelas) {}
