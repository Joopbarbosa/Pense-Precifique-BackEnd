package com.penseprecifique.api.shared.dto.response.producao;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** RN-NOVA-42 (V0.18.0, #1169) — produção que pode ser juntada à atual (RN-NOVA-48: qualquer produto, outro pedido; Em andamento só aparece marcada). */
@Getter
@Setter
public class CandidataJuntarResponse {

    private UUID id;
    private String identificador;
    private String situacao;
    private boolean selecionavel;
    private LocalDate dataInicio;
    private LocalDate dataTerminoPrevista;
    private List<ProdutoQuantidade> produtos;
    private List<String> orcamentos;

    @Getter
    @Setter
    public static class ProdutoQuantidade {
        private UUID produtoId;
        private String nomeProduto;
        private BigDecimal quantidade;
    }
}
