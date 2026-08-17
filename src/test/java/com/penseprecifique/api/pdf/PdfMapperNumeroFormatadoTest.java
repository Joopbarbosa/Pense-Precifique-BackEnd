package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Frente 6/P-BE-CONSOLIDADO-001 (decisão de 2026-07-29) — RN-053 continua valendo em PDF (prefixo
 * "ORC-" nunca aparece), mas o zero-padding artificial ("%04d") saiu: numeroFormatado agora é o
 * número puro, sem zero à esquerda, nos 5 DTOs de PDF/recibo. Teste unitário puro (sem
 * @SpringBootTest) — PdfMapper não tem dependência nenhuma, só formatação.
 */
class PdfMapperNumeroFormatadoTest {

    private final PdfMapper pdfMapper = new PdfMapper();

    private Orcamento orcamentoComNumero(int numero) {
        return Orcamento.builder().numero(numero).build();
    }

    @Test
    void orcamentoPdfDataNaoTemZeroAEsquerdaNemPrefixo() {
        String numeroFormatado = pdfMapper.toOrcamentoPdfData(orcamentoComNumero(47), null, List.of(), Map.of())
                .getNumeroFormatado();
        assertEquals("47", numeroFormatado);
    }

    @Test
    void reciboPdfDataNaoTemZeroAEsquerdaNemPrefixo() {
        String numeroFormatado = pdfMapper.toReciboPdfData(orcamentoComNumero(7), null, List.of(), Map.of())
                .getNumeroFormatado();
        assertEquals("7", numeroFormatado);
    }

    @Test
    void reciboPagamentoPdfDataNaoTemZeroAEsquerdaNemPrefixo() {
        ReciboPagamento recibo = ReciboPagamento.builder()
                .valorTotal(BigDecimal.ZERO).valorSinalPago(BigDecimal.ZERO)
                .valorRestantePago(BigDecimal.ZERO).totalQuitado(BigDecimal.ZERO).build();
        String numeroFormatado = pdfMapper.toReciboPagamentoPdfData(orcamentoComNumero(123), recibo, null, List.of(), Map.of())
                .getNumeroFormatado();
        assertEquals("123", numeroFormatado);
    }

    @Test
    void reciboPdfDataMultaNaoTemZeroAEsquerdaNemPrefixo() {
        String numeroFormatado = pdfMapper.toReciboPdfDataMulta(orcamentoComNumero(9), null, List.of(), Map.of())
                .getNumeroFormatado();
        assertEquals("9", numeroFormatado);
    }

    @Test
    void reciboPdfDataEstornoNaoTemZeroAEsquerdaNemPrefixo() {
        String numeroFormatado = pdfMapper.toReciboPdfDataEstorno(orcamentoComNumero(1000), null).getNumeroFormatado();
        assertEquals("1000", numeroFormatado);
    }
}
