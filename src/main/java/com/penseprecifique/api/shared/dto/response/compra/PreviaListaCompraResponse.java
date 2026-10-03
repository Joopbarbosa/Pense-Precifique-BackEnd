package com.penseprecifique.api.shared.dto.response.compra;

import java.math.BigDecimal;
import java.util.List;

/**
 * #546/RN-NOVA-12 (V0.15.0) — prévia calculada na hora, não salva. {@code quantidadeSugerida} nula =
 * sem sugestão (a artesã preenche). {@code fornecedores}: vínculos válidos do insumo (fornecedor ativo
 * com papel Fornecedor), com preço, para trocar a sugestão na linha.
 */
public record PreviaListaCompraResponse(List<Linha> linhas) {

    public record Linha(
            InsumoRefResponse insumo,
            BigDecimal estoqueAtual,
            BigDecimal estoqueMinimo,
            BigDecimal quantidadeSugerida,
            CadastroRefResponse fornecedorSugerido,
            BigDecimal precoReferencia,
            List<OpcaoFornecedor> fornecedores
    ) {}

    public record OpcaoFornecedor(CadastroRefResponse fornecedor, BigDecimal precoReferencia) {}
}
