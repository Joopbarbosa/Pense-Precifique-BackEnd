package com.penseprecifique.api.shared.dto.response.orcamento;

import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** #382 — o Descritivo com as opções. Sem total, subtotal, desconto, sinal nem forma de pagamento. */
public record DescritivoResponse(
        UUID id,
        Integer numero,
        String identificador,
        String tipo,
        String titulo,
        StatusOrcamento status,
        UUID clienteId,
        String nomeCliente,
        String observacoes,
        LocalDateTime dataEnvio,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Opcao> opcoes) {

    public record Opcao(
            UUID id,
            UUID produtoId,
            UUID itemCatalogoId,
            String nome,
            BigDecimal precoReferencia,
            /** Preço de venda atual do cadastro (só informativo; a Venda gerada usa este valor, não a referência). */
            BigDecimal precoAtual,
            String quantidadeUnidade,
            Integer ordem) {
    }
}
