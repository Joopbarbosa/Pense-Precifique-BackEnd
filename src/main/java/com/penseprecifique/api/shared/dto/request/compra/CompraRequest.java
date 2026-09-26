package com.penseprecifique.api.shared.dto.request.compra;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #541/RN-NOVA-4 (V0.15.0) — cabeçalho + linhas da compra. Usado para salvar rascunho (POST/PUT) e
 * para confirmar (POST /compras/confirmar, POST /compras/{id}/confirmar). Quantidade e preço das
 * linhas podem faltar no rascunho; a confirmação exige.
 */
public record CompraRequest(
        @NotNull(message = "Informe a data da compra")
        LocalDate dataCompra,

        /** Nulo = false (fornecedor único). */
        Boolean multiplosFornecedores,

        /** Fornecedor do cabeçalho, opcional. No modo fornecedor único vale para todas as linhas. */
        UUID fornecedorId,

        /** Nulo = false (Não pago). */
        Boolean pago,

        /** #550/RN-NOVA-23 — obrigatório quando pago; ignorado (limpo) quando não pago. */
        UUID metodoPagamentoId,

        @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
        @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
        String observacoes,

        @Valid
        List<CompraItemRequest> itens
) {
    public List<CompraItemRequest> itensOuVazio() {
        return itens != null ? itens : List.of();
    }
}
