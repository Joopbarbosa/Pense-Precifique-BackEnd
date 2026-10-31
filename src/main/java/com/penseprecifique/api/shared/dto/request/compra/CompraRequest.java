package com.penseprecifique.api.shared.dto.request.compra;

import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
        List<CompraItemRequest> itens,

        /** #576/RN-NOVA-28 — desconto na nota: VALOR (R$) ou PERCENTUAL, rateado nas linhas. */
        TipoDesconto descontoNotaTipo,

        @Positive(message = "O desconto da nota deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "Desconto da nota com no máximo 2 casas decimais")
        BigDecimal descontoNotaValor,

        /** #597/RN-NOVA-42 — só com método Cartão de crédito e pago; ignorado nos demais. */
        Integer parcelas,

        /** #957/RN-NOVA-4 — nulo = Sim (entrega imediata, como antes da V0.17.0). */
        Boolean entregaImediata,

        /** #957/RN-NOVA-4 — só vale com entregaImediata = false; exigida ao confirmar. */
        LocalDate previsaoEntrega,

        /**
         * V0.19.0 (#1173, RN-NOVA-7) — vencimento da parcela a pagar: da única parcela da compra Não paga (obrigatório) ou da
         * 1ª parcela do cartão de crédito (padrão: 1 mês após a data da compra). Não é gravado no rascunho; vale ao confirmar.
         */
        LocalDate vencimento
) {
    /** Forma anterior ao #576 (sem desconto na nota). */
    public CompraRequest(LocalDate dataCompra, Boolean multiplosFornecedores, UUID fornecedorId, Boolean pago,
                         UUID metodoPagamentoId, String observacoes, List<CompraItemRequest> itens) {
        this(dataCompra, multiplosFornecedores, fornecedorId, pago, metodoPagamentoId, observacoes, itens, null, null, null, null, null, dataCompra);
    }

    /** Forma anterior ao #597 (sem parcelas). */
    public CompraRequest(LocalDate dataCompra, Boolean multiplosFornecedores, UUID fornecedorId, Boolean pago,
                         UUID metodoPagamentoId, String observacoes, List<CompraItemRequest> itens,
                         TipoDesconto descontoNotaTipo, BigDecimal descontoNotaValor) {
        this(dataCompra, multiplosFornecedores, fornecedorId, pago, metodoPagamentoId, observacoes, itens,
                descontoNotaTipo, descontoNotaValor, null, null, null, dataCompra);
    }

    /** Forma anterior ao #957 (sem entrega imediata nem previsão). */
    public CompraRequest(LocalDate dataCompra, Boolean multiplosFornecedores, UUID fornecedorId, Boolean pago,
                         UUID metodoPagamentoId, String observacoes, List<CompraItemRequest> itens,
                         TipoDesconto descontoNotaTipo, BigDecimal descontoNotaValor, Integer parcelas) {
        this(dataCompra, multiplosFornecedores, fornecedorId, pago, metodoPagamentoId, observacoes, itens,
                descontoNotaTipo, descontoNotaValor, parcelas, null, null, dataCompra);
    }

    /** Forma anterior ao V0.19.0 (sem vencimento): o vencimento fica na data da compra, como no padrão da tela. */
    public CompraRequest(LocalDate dataCompra, Boolean multiplosFornecedores, UUID fornecedorId, Boolean pago,
                         UUID metodoPagamentoId, String observacoes, List<CompraItemRequest> itens,
                         TipoDesconto descontoNotaTipo, BigDecimal descontoNotaValor, Integer parcelas,
                         Boolean entregaImediata, LocalDate previsaoEntrega) {
        this(dataCompra, multiplosFornecedores, fornecedorId, pago, metodoPagamentoId, observacoes, itens,
                descontoNotaTipo, descontoNotaValor, parcelas, entregaImediata, previsaoEntrega, dataCompra);
    }

    public List<CompraItemRequest> itensOuVazio() {
        return itens != null ? itens : List.of();
    }
}
