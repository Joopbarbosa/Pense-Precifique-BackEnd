package com.penseprecifique.api.shared.dto.response.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrcamentoProducaoResponse {

    private UUID id;
    private UUID producaoId;
    private String identificadorProducao;
    private LocalDate dataTerminoPrevista;
    private boolean estouroPrazo;
    private LocalDateTime createdAt;
    /** RN-NOVA-41 (V0.18.0, #1168) — situação e início da produção, para a seção Produção do Detalhe. */
    private String estado;
    private LocalDate dataInicio;
    /** RN-NOVA-41 — produtos e quantidades da produção (não só os do orçamento). */
    private List<ProdutoProducao> produtos = List.of();
    /** RN-NOVA-42 (V0.18.0, #1169) — outros pedidos no mesmo lote ("junto com ORC-18"); vazio fora de lote. */
    private List<String> outrosOrcamentos = List.of();

    @Getter
    @Setter
    public static class ProdutoProducao {
        private UUID produtoId;
        private String nomeProduto;
        private BigDecimal quantidade;
        /** RN-NOVA-42 — quanto deste produto é deste pedido (null quando o histórico não registra). */
        private BigDecimal quantidadePedido;
    }
}
