package com.penseprecifique.api.pdf;

import com.penseprecifique.api.financeiro.PapelParcela;
import com.penseprecifique.api.financeiro.ParcelaOrcamentoResponse;
import com.penseprecifique.api.financeiro.SituacaoParcela;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCondicoesPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** V0.19.0 #445 (RN-NOVA-45, DT-NOVA-11): página 2 de condições, nos dois modos. */
class PdfMapperCondicoesTest {

    private final PdfMapper mapper = new PdfMapper();

    private static MetodoPagamentoConfiguravel metodo(TipoMetodoPagamento tipo, String nome) {
        return MetodoPagamentoConfiguravel.builder().tipo(tipo).nome(nome).build();
    }

    private static Orcamento orc(StatusOrcamento status) {
        return Orcamento.builder().numero(1).status(status).total(new BigDecimal("1000.00"))
            .metodoPagamento(metodo(TipoMetodoPagamento.PIX, null)).percentualSinal(new BigDecimal("30.00")).build();
    }

    private static ParcelaOrcamentoResponse parcela(PapelParcela papel, String valor, LocalDate venc, SituacaoParcela sit) {
        return new ParcelaOrcamentoResponse(UUID.randomUUID(), papel, new BigDecimal(valor), venc, sit, null, null);
    }

    @Test
    void enviadoListaMetodosAtivosNaOrdemRecebidaComAvisoFixo() {
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(orc(StatusOrcamento.ENVIADO),
            List.of(metodo(TipoMetodoPagamento.PIX, null), metodo(TipoMetodoPagamento.CARTAO_CREDITO, "Visa 12x")), List.of());
        assertEquals("ANTES_APROVACAO", c.getModo());
        assertEquals(List.of("Pix", "Visa 12x"), c.getFormasAceitas());
        assertEquals(PdfMapper.AVISO_CONDICOES_ANTES_APROVACAO, c.getAviso());
        assertNull(c.getSinal());
    }

    @Test
    void rascunhoSemMetodoAtivoTrazSoOAviso() {
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(orc(StatusOrcamento.RASCUNHO), List.of(), List.of());
        assertEquals("ANTES_APROVACAO", c.getModo());
        assertTrue(c.getFormasAceitas().isEmpty());
        assertNotNull(c.getAviso());
    }

    @Test
    void aprovadoMostraSinalSaldoERecebido() {
        List<ParcelaOrcamentoResponse> ps = List.of(
            parcela(PapelParcela.SINAL, "300.00", LocalDate.of(2026, 11, 5), SituacaoParcela.BAIXADA),
            parcela(PapelParcela.SALDO, "700.00", LocalDate.of(2026, 12, 1), SituacaoParcela.EM_ABERTO));
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(orc(StatusOrcamento.SINAL_PAGO), List.of(), ps);
        assertEquals("APROVADO", c.getModo());
        assertEquals("Pix", c.getForma());
        assertTrue(c.getSinal().contains("300,00") && c.getSinal().endsWith("(30%)"), c.getSinal());
        assertEquals("05/11/2026", c.getSinalAte());
        assertTrue(c.getSaldo().contains("700,00"));
        assertEquals("01/12/2026", c.getVencimentoSaldo());
        assertTrue(c.getRecebido().contains("300,00"));
        assertNull(c.getFormasAceitas());
    }

    @Test
    void semSinalMostraSemSinalEUsaParcelaTotalComoSaldo() {
        Orcamento o = orc(StatusOrcamento.APROVADO);
        o.setPercentualSinal(null);
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(o, List.of(),
            List.of(parcela(PapelParcela.TOTAL, "1000.00", LocalDate.of(2026, 12, 1), SituacaoParcela.EM_ABERTO)));
        assertEquals("Sem sinal", c.getSinal());
        assertNull(c.getSinalAte());
        assertTrue(c.getSaldo().contains("1.000,00"));
        assertTrue(c.getRecebido().contains("0,00"));
    }

    @Test
    void sinalDeCemPorCentoSemSaldoENaoContaMulta() {
        List<ParcelaOrcamentoResponse> ps = List.of(
            parcela(PapelParcela.SINAL, "1000.00", null, SituacaoParcela.EM_ABERTO),
            parcela(PapelParcela.MULTA, "50.00", null, SituacaoParcela.BAIXADA));
        Orcamento o = orc(StatusOrcamento.AGUARDANDO_SINAL);
        o.setPercentualSinal(null);
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(o, List.of(), ps);
        assertTrue(c.getSinal().endsWith("(100%)"), c.getSinal());
        assertNull(c.getSaldo());
        assertNull(c.getSinalAte());
        assertTrue(c.getRecebido().contains("0,00"));
    }

    @Test
    void aprovadoSemParcelasCaiNoModoAntesDaAprovacao() {
        assertEquals("ANTES_APROVACAO",
            mapper.toCondicoesPayload(orc(StatusOrcamento.CANCELADO), List.of(), List.of()).getModo());
    }

    @Test
    void limitesDoContratoSaoRespeitados() {
        List<MetodoPagamentoConfiguravel> muitos = java.util.stream.IntStream.range(0, 60)
            .mapToObj(i -> metodo(TipoMetodoPagamento.OUTRO, "M" + i)).toList();
        assertEquals(50, mapper.toCondicoesPayload(orc(StatusOrcamento.ENVIADO), muitos, List.of()).getFormasAceitas().size());
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(orc(StatusOrcamento.ENVIADO),
            List.of(metodo(TipoMetodoPagamento.OUTRO, "x".repeat(300))), List.of());
        assertEquals(100, c.getFormasAceitas().get(0).length());
    }

    @Test
    void condicoesChegamAoPayloadDoMicroservico() {
        Orcamento o = orc(StatusOrcamento.ENVIADO);
        PdfMicroservicoCondicoesPayload c = mapper.toCondicoesPayload(o, List.of(), List.of());
        var dados = mapper.toOrcamentoPdfData(o, null, List.of(), java.util.Map.of(), c);
        assertSame(c, mapper.toMicroservicoPayload(dados).getDocumento().getCondicoes());
    }
}
