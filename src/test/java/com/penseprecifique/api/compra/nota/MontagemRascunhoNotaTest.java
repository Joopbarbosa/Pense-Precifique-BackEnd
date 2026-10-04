package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida.Item;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.Escolha;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.16.0 (#683, RN-NOVA-11 e RN-NOVA-15) — montagem das linhas do rascunho, sem banco.
 * Cobre CEN-NOVO-42, 43, 44, 45, 60 e 61 (a parte de cálculo) e o fator de conversão.
 */
class MontagemRascunhoNotaTest {

    private static final UUID FITA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COLA = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static Item item(String nome, String qtd, String final_, String bruto, String desconto) {
        return new Item(nome, v(qtd), v(final_), bruto == null ? null : v(bruto), desconto == null ? null : v(desconto), "UN", null, null);
    }

    private static NotaLida nota(String total, String descontoGeral, String acrescimos, Item... itens) {
        return new NotaLida(new NotaLida.Emitente("11222333000181", "Papelaria", "SP"), "3".repeat(44), "1", "1",
                "2026-10-01", v(total), descontoGeral == null ? null : v(descontoGeral), acrescimos == null ? null : v(acrescimos),
                List.of(itens), "NFCE_QR", "LEITOR_UF", false, "SP", "SP-1", List.of());
    }

    private static Escolha liga(int pos, UUID insumo, String fator) {
        return new Escolha(pos, insumo, v(fator), false);
    }

    private static Escolha ignora(int pos) {
        return new Escolha(pos, null, null, true);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, v(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    @Test
    void linhaComDescontoDoItemUsaValorBrutoComoPrecoCheio() {
        var r = MontagemRascunhoNota.montar(nota("27.00", null, null, item("FITA CETIM", "10", "27.00", "30.00", "3.00")),
                List.of(liga(0, FITA, "1")));
        igual("30.00", r.linhas().get(0).precoCheio());
        igual("3.00", r.linhas().get(0).descontoLinha());
        assertFalse(MontagemRascunhoNota.temDiferenca(r));
    }

    @Test
    void linhaSemDescontoUsaValorFinalComoPrecoCheio() {
        var r = MontagemRascunhoNota.montar(nota("45.90", null, null, item("COLA 1L", "3", "45.90", null, null)),
                List.of(liga(0, COLA, "1")));
        igual("45.90", r.linhas().get(0).precoCheio());
        igual("0", r.linhas().get(0).descontoLinha());
    }

    @Test
    void cen60_doisItensDoMesmoInsumoViramUmaLinha() {
        var r = MontagemRascunhoNota.montar(nota("55.00", null, null,
                item("FITA CETIM 10MM", "10", "30.00", "33.00", "3.00"), item("FITA CETIM 15MM", "5", "25.00", null, null)),
                List.of(liga(0, FITA, "1"), liga(1, FITA, "1")));
        assertEquals(1, r.linhas().size());
        igual("15", r.linhas().get(0).quantidade());
        igual("58.00", r.linhas().get(0).precoCheio());
        igual("3.00", r.linhas().get(0).descontoLinha());
        assertEquals(List.of(0, 1), r.linhas().get(0).posicoes());
    }

    @Test
    void fatorDeConversaoMultiplicaAQuantidadeDaNota() {
        var r = MontagemRascunhoNota.montar(nota("20.00", null, null, item("PAPEL A4 PACOTE", "2", "20.00", null, null)),
                List.of(liga(0, FITA, "100")));
        igual("200", r.linhas().get(0).quantidade());
    }

    @Test
    void cen61_itemIgnoradoReduzOdescontoGeralNaMesmaProporcao() {
        var r = MontagemRascunhoNota.montar(nota("80.00", "20.00", null,
                item("A", "1", "10.00", null, null), item("B", "1", "90.00", null, null)),
                List.of(liga(0, FITA, "1"), ignora(1)));
        igual("2.00", r.descontoNota());   // 20 × 10 ÷ 100
        assertEquals(1, r.linhas().size());
        assertFalse(MontagemRascunhoNota.temDiferenca(r), "a conferência usa todos os itens lidos: 100 − 20 = 80");
    }

    @Test
    void cen43_descontosEAcrescimosLegitimosNaoGeramAviso() {
        var r = MontagemRascunhoNota.montar(nota("95.00", "10.00", "5.00", item("A", "1", "100.00", null, null)),
                List.of(liga(0, FITA, "1")));
        igual("5.00", r.acrescimos());
        igual("10.00", r.descontoNota());
        assertFalse(MontagemRascunhoNota.temDiferenca(r));
    }

    @Test
    void cen42_diferencaSemExplicacaoViraAviso() {
        var r = MontagemRascunhoNota.montar(nota("90.00", null, null, item("A", "1", "100.00", null, null)),
                List.of(liga(0, FITA, "1")));
        assertTrue(MontagemRascunhoNota.temDiferenca(r));
        igual("-10.00", r.diferenca());
    }

    @Test
    void cen44_todosIgnoradosBloqueia() {
        BusinessException e = assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(
                nota("10.00", null, null, item("A", "1", "10.00", null, null)), List.of(ignora(0))));
        assertTrue(e.getMessage().contains("Todos os itens da nota foram ignorados"), e.getMessage());
    }

    @Test
    void cen45_fatorVazioZeroOuNegativoBloqueia() {
        NotaLida n = nota("10.00", null, null, item("A", "1", "10.00", null, null));
        for (String fator : new String[]{"0", "-1"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(n, List.of(liga(0, FITA, fator))));
            assertTrue(e.getMessage().contains("fator de conversão"), e.getMessage());
        }
        BusinessException vazio = assertThrows(BusinessException.class,
                () -> MontagemRascunhoNota.montar(n, List.of(new Escolha(0, FITA, null, false))));
        assertTrue(vazio.getMessage().contains("fator de conversão"), vazio.getMessage());
    }

    @Test
    void todoItemPrecisaDeEscolhaSemRepetirNemSobrar() {
        NotaLida n = nota("20.00", null, null, item("A", "1", "10.00", null, null), item("B", "1", "10.00", null, null));
        assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(n, List.of(liga(0, FITA, "1"))));
        assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(n, List.of(liga(0, FITA, "1"), liga(0, COLA, "1"), liga(1, COLA, "1"))));
        assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(n, List.of(liga(0, FITA, "1"), liga(5, COLA, "1"))));
    }

    @Test
    void itemNaoIgnoradoSemInsumoBloqueia() {
        NotaLida n = nota("10.00", null, null, item("A", "1", "10.00", null, null));
        assertThrows(BusinessException.class, () -> MontagemRascunhoNota.montar(n, List.of(new Escolha(0, null, v("1"), false))));
    }
}
