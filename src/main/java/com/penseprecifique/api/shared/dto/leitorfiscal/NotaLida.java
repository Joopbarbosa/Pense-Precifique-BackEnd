package com.penseprecifique.api.shared.dto.leitorfiscal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * V0.16.0 (#683, RN-NOVA-2, DT-NOVA-1) — nota lida devolvida pelo leitor-fiscal. Valores e quantidades
 * chegam como lidos na nota; {@code origem} e {@code metodo} dizem de onde veio o dado
 * ({@code NFCE_QR}, {@code NFE_PDF}, {@code NFE_FOTO}, {@code NFE_XML}; {@code LEITOR_UF}, {@code IA}, {@code XML}).
 * Campos desconhecidos são ignorados para o serviço poder evoluir sem quebrar o produto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotaLida(
        Emitente emitente,
        String chaveAcesso,
        String numero,
        String serie,
        String dataEmissao,
        BigDecimal totalPago,
        BigDecimal descontoGeral,
        BigDecimal acrescimos,
        List<Item> itens,
        String origem,
        String metodo,
        Boolean doCache,
        String uf,
        String leiaute,
        List<String> avisos
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Emitente(String cnpj, String nome, String uf) {}

    /** {@code valorFinal} já tem o desconto do item; {@code valorBruto} e {@code desconto} só vêm quando a nota os traz. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String nome, BigDecimal quantidade, BigDecimal valorFinal, BigDecimal valorBruto,
                       BigDecimal desconto, String unidade, String codigo, String ean) {}

    public List<Item> itensOuVazio() {
        return itens != null ? itens : List.of();
    }

    public List<String> avisosOuVazio() {
        return avisos != null ? avisos : List.of();
    }
}
