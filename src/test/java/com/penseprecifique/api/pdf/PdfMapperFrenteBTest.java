package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #248 (Frente B) — os 4 campos pendentes do schema de orçamento (telefoneCliente, emailCliente,
 * percentualSinal, percentualDesconto). Teste unitário puro (sem @SpringBootTest), mesmo padrão de
 * {@link PdfMapperNumeroFormatadoTest} — PdfMapper não tem dependência de banco.
 */
class PdfMapperFrenteBTest {

    private final PdfMapper pdfMapper = new PdfMapper();

    @Test
    void telefoneClienteEEmailClienteVemDeWhatsappEEmailDoCliente() {
        Cliente cliente = Cliente.builder()
                .nome("Mariana")
                .email("mariana@teste.com")
                .whatsapp("(11) 98888-7777")
                .build();
        Orcamento orcamento = Orcamento.builder().numero(1).cliente(cliente).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertEquals("(11) 98888-7777", dados.getTelefoneCliente());
        assertEquals("mariana@teste.com", dados.getEmailCliente());
    }

    @Test
    void telefoneClienteEEmailClienteFicamNullQuandoOrcamentoNaoTemCliente() {
        Orcamento orcamento = Orcamento.builder().numero(1).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertNull(dados.getTelefoneCliente());
        assertNull(dados.getEmailCliente());
    }

    @Test
    void percentualSinalFormatadoComSufixoPercentualQuandoPresente() {
        Orcamento orcamento = Orcamento.builder().numero(1).percentualSinal(new BigDecimal("50")).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertEquals("50%", dados.getPercentualSinal());
    }

    @Test
    void percentualSinalFicaNullQuandoAusente() {
        Orcamento orcamento = Orcamento.builder().numero(1).build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertNull(dados.getPercentualSinal());
    }

    /**
     * Caso central da Frente B — descontoValor só É o percentual quando descontoTipo == PERCENTUAL
     * (confirmado em OrcamentoService.calcularTotal). Exemplo do prompt: descontoValor=15 vira "15%".
     */
    @Test
    void percentualDescontoExposaComoStringFormatadaQuandoTipoPercentual() {
        Orcamento orcamento = Orcamento.builder()
                .numero(1)
                .descontoTipo(TipoDesconto.PERCENTUAL)
                .descontoValor(new BigDecimal("15"))
                .subtotal(new BigDecimal("100"))
                .total(new BigDecimal("85"))
                .build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertEquals("15%", dados.getPercentualDesconto());
    }

    @Test
    void percentualDescontoFicaNullQuandoTipoValor() {
        Orcamento orcamento = Orcamento.builder()
                .numero(1)
                .descontoTipo(TipoDesconto.VALOR)
                .descontoValor(new BigDecimal("15"))
                .subtotal(new BigDecimal("100"))
                .total(new BigDecimal("85"))
                .build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertNull(dados.getPercentualDesconto(),
                "descontoValor=15 sob tipo VALOR é R$ 15, não 15% — não deve virar percentual");
    }

    @Test
    void percentualDescontoFicaNullQuandoDescontoValorZero() {
        Orcamento orcamento = Orcamento.builder()
                .numero(1)
                .descontoTipo(TipoDesconto.PERCENTUAL)
                .descontoValor(BigDecimal.ZERO)
                .subtotal(new BigDecimal("100"))
                .total(new BigDecimal("100"))
                .build();

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, List.of(), Map.of());

        assertNull(dados.getPercentualDesconto());
    }
}
