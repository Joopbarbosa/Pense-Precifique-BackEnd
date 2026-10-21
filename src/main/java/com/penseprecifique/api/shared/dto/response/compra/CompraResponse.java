package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.ComprovanteTipo;
import com.penseprecifique.api.shared.domain.enums.OrigemCompra;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** #541 (V0.15.0) — detalhe da compra. {@code total} = soma dos preços totais informados. */
public record CompraResponse(
        UUID id,
        Integer numero,
        String identificador,
        StatusCompra status,
        LocalDate dataCompra,
        boolean multiplosFornecedores,
        CadastroRefResponse fornecedor,
        boolean pago,
        MetodoPagamentoRefResponse metodoPagamento,
        String observacoes,
        OrigemCompra origem,
        BigDecimal total,
        List<CompraItemResponse> itens,
        LocalDateTime confirmadaEm,
        LocalDateTime canceladaEm,
        String observacaoCancelamento,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** #576/RN-NOVA-28 — desconto da nota como digitado e em R$; totais cheio e de descontos. */
        TipoDesconto descontoNotaTipo,
        BigDecimal descontoNotaInformado,
        BigDecimal descontoNota,
        BigDecimal totalCheio,
        BigDecimal totalDescontos,
        /** #597/RN-NOVA-42 — só com Cartão de crédito. */
        Integer parcelas,
        /** #596/RN-NOVA-41 — lista de onde a compra foi criada (nula se não veio de lista). */
        ListaCompraRef listaCompra,
        /** #683/DT-NOVA-10 (V0.16.0) — compra por nota: chave de acesso e comprovante (nulos nas manuais). */
        String chaveAcesso,
        ComprovanteTipo comprovanteTipo,
        String comprovanteNome,
        String comprovanteUrl,
        /** #957/RN-NOVA-4 (V0.17.0) — nulo nas compras antigas (não se aplica). */
        Boolean entregaImediata,
        LocalDate previsaoEntrega,
        /** #957/DT-NOVA-1 — data em que a compra passou a contar nos números. */
        LocalDate dataEntrada,
        /** #958/RN-NOVA-6 — dias inteiros de atraso da pendente (previsão anterior a hoje); nulo nos demais casos. */
        Integer diasAtraso
) {
    public record ListaCompraRef(UUID id, String identificador) {}
}
