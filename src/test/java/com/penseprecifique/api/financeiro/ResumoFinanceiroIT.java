package com.penseprecifique.api.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V0.19.0 (#1176, RN-NOVA-17 a 23, DT-NOVA-6) — resumo financeiro: realizado sem dupla contagem (CEN-NOVO-23), mês vazio
 * (24), projeção (25), maiores despesas por categoria (26), próximos vencimentos (64), cancelada e "fora da projeção" (60, RN-NOVA-16),
 * isolamento por conta e entrada hostil (CEN-NOVO-55): nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ResumoFinanceiroIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired LancamentoFinanceiroService lancamentoService;
    @Autowired CategoriaFinanceiraService categoriaService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired CaixaTurnoRepository turnoRepository;
    @Autowired VendaCaixaRepository vendaRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @Autowired com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository metodoRepository;
    @Autowired Clock relogioBrasilia;
    private final ObjectMapper json = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    private int numeroVenda = 0;

    private record Conta(String email, String token, Usuario usuario) {}

    private Conta conta() throws Exception {
        String email = "fin-res-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}")).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        return new Conta(email, token, usuarioRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow());
    }

    private void logar(Conta c) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(c.email(), null, List.of()));
    }

    private LocalDate hoje() {
        return LocalDate.now(relogioBrasilia);
    }

    /** Cria o lançamento de uma parcela só; {@code baixaEm} != null a baixa direto no banco (data livre, sem passar pela regra de data futura). */
    private Lancamento lancar(Conta c, TipoLancamento tipo, String descricao, String valor, LocalDate vencimento, LocalDate baixaEm,
                              UUID categoriaId, boolean foraProjecao, boolean cancelada) {
        logar(c);
        Lancamento l = lancamentoService.criar(new NovoLancamento(tipo, descricao, OrigemLancamento.AVULSO, null, null,
                DivisaoParcelas.dividir(new BigDecimal(valor), 1, vencimento), categoriaId));
        if (foraProjecao) {
            lancamentoService.marcarForaProjecao(l.getId(), true);
        }
        Parcela p = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId()).get(0);
        if (baixaEm != null) {
            p.setSituacao(SituacaoParcela.BAIXADA);
            p.setDataBaixa(baixaEm);
            p.setOrigemBaixa(OrigemBaixa.USUARIA);
            p.setMetodoPagamento(com.penseprecifique.api.testsupport.MetodosPagamentoTeste.pix(metodoRepository, c.usuario()));
            parcelaRepository.save(p);
        } else if (cancelada) {
            p.setSituacao(SituacaoParcela.CANCELADA);
            parcelaRepository.save(p);
        }
        return l;
    }

    private CaixaTurno turno(Conta c, LocalDateTime abertura, String valor) {
        return turnoRepository.save(CaixaTurno.builder().usuario(c.usuario()).dataAbertura(abertura)
                .valorAbertura(new BigDecimal(valor)).dataFechamento(abertura.plusHours(8)).status(StatusCaixaTurno.FECHADO).build());
    }

    private void venda(Conta c, CaixaTurno t, LocalDateTime quando, String total, StatusVendaCaixa status) {
        vendaRepository.save(VendaCaixa.builder().usuario(c.usuario()).numero(++numeroVenda).dataVenda(quando).caixaTurno(t)
                .status(status).subtotal(new BigDecimal(total)).total(new BigDecimal(total)).build());
    }

    private JsonNode resumo(Conta c, String mes) throws Exception {
        MvcResult r = mvc.perform(get("/financeiro/resumo" + (mes == null ? "" : "?mes=" + mes)).header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static BigDecimal d(JsonNode n) {
        return n.decimalValue();
    }

    private static void igual(String esperado, JsonNode n) {
        assertEquals(0, new BigDecimal(esperado).compareTo(n.decimalValue()), "esperado " + esperado + " obtido " + n);
    }

    /** CEN-NOVO-23 (com extras) — abertura 200, venda 100, baixas 500 e 150: sem dupla contagem; cancelada, venda cancelada e outro mês ficam de fora. */
    @Test
    void resumoDoMesSemDuplaContagem() throws Exception {
        Conta c = conta();
        CaixaTurno t = turno(c, LocalDateTime.of(2025, 11, 3, 9, 0), "200.00");
        venda(c, t, LocalDateTime.of(2025, 11, 10, 11, 30), "100.00", StatusVendaCaixa.CONCLUIDA);
        venda(c, t, LocalDateTime.of(2025, 11, 11, 11, 30), "77.00", StatusVendaCaixa.CANCELADA);
        venda(c, t, LocalDateTime.of(2025, 12, 1, 0, 0), "999.00", StatusVendaCaixa.CONCLUIDA);
        lancar(c, TipoLancamento.A_RECEBER, "Sinal", "500.00", LocalDate.of(2025, 11, 12), LocalDate.of(2025, 11, 12), null, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "Compra", "150.00", LocalDate.of(2025, 11, 15), LocalDate.of(2025, 11, 15), null, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "Compra cancelada", "100.00", LocalDate.of(2025, 11, 20), null, null, false, true);

        JsonNode r = resumo(c, "2025-11");
        assertEquals("2025-11", r.get("mes").asText());
        igual("200.00", r.get("saldoInicial"));
        assertTrue(r.get("saldoInicialInformado").asBoolean());
        assertTrue(r.get("avisoSaldoInicial").isNull());
        igual("600.00", r.get("entradasRealizadas"));
        igual("150.00", r.get("saidasRealizadas"));
        igual("450.00", r.get("movimentoPeriodo"));
        igual("650.00", r.get("saldoFinal"));
        assertEquals("650.00", d(r.get("saldoFinal")).toPlainString());
        assertFalse(r.get("semMovimentos").asBoolean());
        igual("500.00", r.get("previstoRealizado").get("previstoEntradas"));
        igual("150.00", r.get("previstoRealizado").get("previstoSaidas"));
        JsonNode semanas = r.get("entrouSaiu");
        assertEquals(5, semanas.size());
        assertEquals("29 a 30", semanas.get(4).get("rotulo").asText());
        igual("600.00", semanas.get(1).get("entradas"));
        igual("0.00", semanas.get(1).get("saidas"));
        igual("150.00", semanas.get(2).get("saidas"));
        igual("800.00", semanas.get(1).get("saldoAcumulado"));
        igual("650.00", semanas.get(4).get("saldoAcumulado"));
    }

    /** CEN-NOVO-24 — mês sem nada: tudo zero, texto e aviso do saldo inicial; JSON com 2 casas. */
    @Test
    void mesSemMovimentoEAvisoDeSaldoInicial() throws Exception {
        Conta c = conta();
        JsonNode r = resumo(c, "2024-10");
        assertTrue(r.get("semMovimentos").asBoolean());
        assertEquals("Sem movimentos neste mês.", r.get("mensagemSemMovimentos").asText());
        assertEquals("Informe o saldo inicial ao abrir o caixa.", r.get("avisoSaldoInicial").asText());
        assertFalse(r.get("saldoInicialInformado").asBoolean());
        for (String campo : new String[]{"saldoInicial", "entradasRealizadas", "saidasRealizadas", "movimentoPeriodo", "saldoFinal"}) {
            assertEquals("0.00", d(r.get(campo)).toPlainString(), campo);
        }
        assertEquals(0, r.get("maioresDespesas").size());
        assertEquals(0, r.get("proximosVencimentos").get("total").asInt());
        assertEquals("Nenhum vencimento nos próximos 7 dias.", r.get("proximosVencimentos").get("mensagemVazio").asText());
        assertTrue(r.get("projecao").get("semDados").asBoolean());
        assertEquals("Sem movimentos para mostrar.", r.get("projecao").get("mensagemSemDados").asText());
        // sem mes = mês atual
        assertEquals(java.time.YearMonth.from(hoje()).toString(), resumo(c, null).get("mes").asText());
    }

    /** CEN-NOVO-25 + RN-NOVA-16/20 — projeção 30/60/90: vencida entra, "fora da projeção" e Cancelada não; a mais distante só no marco de 90. */
    @Test
    void projecaoDe30Dias() throws Exception {
        Conta c = conta();
        LocalDate h = hoje();
        turno(c, h.atTime(0, 1), "200.00");
        lancar(c, TipoLancamento.A_RECEBER, "Recebido hoje", "450.00", h, h, null, false, false);
        lancar(c, TipoLancamento.A_RECEBER, "A receber 25d", "400.00", h.plusDays(25), null, null, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "A pagar 5d", "150.00", h.plusDays(5), null, null, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "Vencida", "20.00", h.minusDays(5), null, null, false, false);
        lancar(c, TipoLancamento.A_RECEBER, "Distante", "300.00", h.plusDays(75), null, null, false, false);
        lancar(c, TipoLancamento.A_RECEBER, "Fora da projecao", "1000.00", h.plusDays(10), null, null, true, false);
        lancar(c, TipoLancamento.A_RECEBER, "Cancelada", "777.00", h.plusDays(11), null, null, false, true);
        lancar(c, TipoLancamento.A_RECEBER, "Alem de 90", "5000.00", h.plusDays(120), null, null, false, false);

        JsonNode pr = resumo(c, null).get("projecao");
        igual("650.00", pr.get("saldoAtual"));
        assertEquals(3, pr.get("marcos").size());
        assertEquals(30, pr.get("marcos").get(0).get("dias").asInt());
        igual("880.00", pr.get("marcos").get(0).get("saldo"));
        igual("880.00", pr.get("marcos").get(1).get("saldo"));
        igual("1180.00", pr.get("marcos").get(2).get("saldo"));
        assertEquals(91, pr.get("serie").size());
        igual("1180.00", pr.get("serie").get(90).get("saldo"));
        igual("630.00", pr.get("serie").get(0).get("saldo"));
        assertFalse(pr.get("semDados").asBoolean());
    }

    /** CEN-NOVO-26 — empate de valor ordena por nome; percentuais com uma casa; estorno sem categoria fica fora do indicador mas entra na saída. */
    @Test
    void maioresDespesasPorCategoriaComEmpateEEstorno() throws Exception {
        Conta c = conta();
        logar(c);
        UUID insumos = idCategoria("Insumos e materiais");
        UUID aluguel = idCategoria("Aluguel");
        UUID frete = idCategoria("Frete");
        LocalDate dia = LocalDate.of(2025, 11, 5);
        lancar(c, TipoLancamento.A_PAGAR, "Compra", "400.00", dia, dia, insumos, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "Aluguel", "400.00", dia, dia, aluguel, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "Frete", "50.00", dia, dia, frete, false, false);
        // estorno de sinal: despesa baixada sem categoria
        logar(c);
        Lancamento estorno = lancamentoRepository.save(Lancamento.builder().usuario(c.usuario()).tipo(TipoLancamento.A_PAGAR)
                .descricao("Estorno de sinal").valorTotal(new BigDecimal("30.00")).origem(OrigemLancamento.AVULSO).build());
        parcelaRepository.save(Parcela.builder().lancamento(estorno).usuario(c.usuario()).numero(1).valor(new BigDecimal("30.00"))
                .vencimento(dia).situacao(SituacaoParcela.BAIXADA).dataBaixa(dia).origemBaixa(OrigemBaixa.USUARIA)
                .metodoPagamento(com.penseprecifique.api.testsupport.MetodosPagamentoTeste.pix(metodoRepository, c.usuario())).build());

        JsonNode r = resumo(c, "2025-11");
        JsonNode cat = r.get("maioresDespesas");
        assertEquals(3, cat.size());
        assertEquals("Aluguel", cat.get(0).get("nome").asText());
        assertEquals("Insumos e materiais", cat.get(1).get("nome").asText());
        assertEquals("Frete", cat.get(2).get("nome").asText());
        assertEquals("47.1", d(cat.get(0).get("percentual")).toPlainString());
        assertEquals("47.1", d(cat.get(1).get("percentual")).toPlainString());
        assertEquals("5.9", d(cat.get(2).get("percentual")).toPlainString());
        igual("880.00", r.get("saidasRealizadas"));
    }

    /** RN-NOVA-23 — mais de cinco categorias: cinco maiores e «Outras categorias» somadas. */
    @Test
    void maisDeCincoCategoriasViramOutras() throws Exception {
        Conta c = conta();
        logar(c);
        String[] nomes = {"Insumos e materiais", "Embalagem", "Frete", "Aluguel", "Contas (água, luz, internet)", "Outros"};
        LocalDate dia = LocalDate.of(2025, 11, 5);
        String[] valores = {"100.00", "90.00", "80.00", "70.00", "60.00", "50.00"};
        for (int i = 0; i < nomes.length; i++) {
            lancar(c, TipoLancamento.A_PAGAR, nomes[i], valores[i], dia, dia, idCategoria(nomes[i]), false, false);
        }
        lancar(c, TipoLancamento.A_PAGAR, "Mais outros", "25.00", dia, dia, idCategoria("Outros"), false, false);
        JsonNode cat = resumo(c, "2025-11").get("maioresDespesas");
        assertEquals(6, cat.size());
        assertEquals("Outras categorias", cat.get(5).get("nome").asText());
        assertTrue(cat.get(5).get("outras").asBoolean());
        igual("60.00", cat.get(5).get("valor"));
        assertTrue(cat.get(5).get("categoriaId").isNull());
        BigDecimal soma = BigDecimal.ZERO;
        for (JsonNode n : cat) soma = soma.add(n.get("percentual").decimalValue());
        assertTrue(soma.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.3")) <= 0, "soma=" + soma);
    }

    /** CEN-NOVO-64 — 12 vencimentos iguais: dez na ordem de criação, total 12, "Ver todas" com os 12 e mesma ordem ao recarregar. */
    @Test
    void proximosVencimentosEmpateTotalDeOrdem() throws Exception {
        Conta c = conta();
        LocalDate h = hoje();
        List<String> esperados = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String nome = String.format("L%02d", i);
            esperados.add(nome);
            lancar(c, TipoLancamento.A_PAGAR, nome, "50.00", h, null, null, false, false);
        }
        // vencida primeiro (fica na frente) e uma além de 7 dias (fica de fora)
        lancar(c, TipoLancamento.A_RECEBER, "Atrasada", "10.00", h.minusDays(30), null, null, false, false);
        lancar(c, TipoLancamento.A_RECEBER, "Longe", "10.00", h.plusDays(8), null, null, false, false);
        lancar(c, TipoLancamento.A_PAGAR, "No limite", "10.00", h.plusDays(7), null, null, false, false);

        JsonNode pv = resumo(c, null).get("proximosVencimentos");
        assertEquals(14, pv.get("total").asInt());
        assertTrue(pv.get("temMais").asBoolean());
        assertEquals(10, pv.get("itens").size());
        assertEquals("Atrasada", pv.get("itens").get(0).get("descricao").asText());
        assertEquals("L01", pv.get("itens").get(1).get("descricao").asText());
        assertEquals("L09", pv.get("itens").get(9).get("descricao").asText());
        JsonNode pv2 = resumo(c, null).get("proximosVencimentos");
        assertEquals(pv.get("itens").toString(), pv2.get("itens").toString());

        MvcResult todas = mvc.perform(get("/financeiro/proximos-vencimentos").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, todas.getResponse().getStatus());
        JsonNode l = json.readTree(todas.getResponse().getContentAsString());
        assertEquals(14, l.get("total").asInt());
        assertEquals(14, l.get("itens").size());
        List<String> nomes = new ArrayList<>();
        for (JsonNode n : l.get("itens")) nomes.add(n.get("descricao").asText());
        assertEquals(esperados, nomes.subList(1, 13));
        assertEquals("No limite", nomes.get(13));
        // valor maior primeiro na mesma data
        lancar(c, TipoLancamento.A_PAGAR, "Grande", "900.00", h.plusDays(7), null, null, false, false);
        MvcResult de = mvc.perform(get("/financeiro/proximos-vencimentos?size=100").header("Authorization", "Bearer " + c.token())).andReturn();
        JsonNode l2 = json.readTree(de.getResponse().getContentAsString());
        assertEquals("Grande", l2.get("itens").get(13).get("descricao").asText());
        assertEquals("No limite", l2.get("itens").get(14).get("descricao").asText());
    }

    /** Cada conta só enxerga os próprios dados. */
    @Test
    void contaNaoVeDadosDeOutra() throws Exception {
        Conta a = conta();
        Conta b = conta();
        lancar(a, TipoLancamento.A_PAGAR, "Da A", "10.00", hoje(), null, null, false, false);
        assertEquals(0, resumo(b, null).get("proximosVencimentos").get("total").asInt());
        assertEquals(1, resumo(a, null).get("proximosVencimentos").get("total").asInt());
    }

    /** O resumo não semeia categorias (DT-NOVA-7): conta nova continua sem categoria depois de consultar. */
    @Test
    void resumoNuncaSemeiaCategorias() throws Exception {
        Conta c = conta();
        resumo(c, null);
        MvcResult r = mvc.perform(get("/financeiro/resumo?mes=2025-11").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(0, new org.springframework.transaction.support.TransactionTemplate(tm).execute(
                st -> categoriaRepository.findAll().stream().filter(x -> x.getUsuario().getId().equals(c.usuario().getId())).count()).intValue());
    }

    /** CEN-NOVO-55 — mês e paginação hostis, sem token, método errado: sempre 4xx, nunca 500. */
    @Test
    void entradaHostilNuncaDa500() throws Exception {
        Conta c = conta();
        String[] meses = {"abc", "2026-13", "2026-00", "2026-1", "26-10", "1999-12", "2101-01", "0000-01", "-2026-10", "+2026-10",
                "2026-10-01", "%00", "2026-10%00", "x".repeat(501), "x".repeat(100_001), "2026-10&mes=2026-11", "'", "%27OR%271", " ", "１２３４-０１"};
        for (String m : meses) {
            MvcResult r = mvc.perform(get("/financeiro/resumo").param("mes", m).header("Authorization", "Bearer " + c.token())).andReturn();
            int st = r.getResponse().getStatus();
            if (m.isBlank()) assertEquals(200, st, "mes em branco = mês atual");
            else assertTrue(st >= 400 && st < 500, "mes=" + m.substring(0, Math.min(20, m.length())) + " -> " + st);
        }
        String nulo = "2026-10\u0000";
        assertEquals(400, mvc.perform(get("/financeiro/resumo").param("mes", nulo).header("Authorization", "Bearer " + c.token())).andReturn().getResponse().getStatus());
        String[] paginas = {"page=-1", "size=0", "size=-5", "size=abc", "page=abc", "size=99999999999999999999", "page=2147483647&size=100",
                "sort=valor,desc", "sort=naoexiste", "page=1&page=2", "size=1000000"};
        for (String q : paginas) {
            MvcResult r = mvc.perform(get("/financeiro/proximos-vencimentos?" + q).header("Authorization", "Bearer " + c.token())).andReturn();
            assertTrue(r.getResponse().getStatus() < 500, q + " -> " + r.getResponse().getStatus());
        }
        assertTrue(mvc.perform(get("/financeiro/resumo")).andReturn().getResponse().getStatus() / 100 == 4);
        assertTrue(mvc.perform(get("/financeiro/proximos-vencimentos")).andReturn().getResponse().getStatus() / 100 == 4);
        assertTrue(mvc.perform(post("/financeiro/resumo").header("Authorization", "Bearer " + c.token())).andReturn().getResponse().getStatus() / 100 == 4);
        assertTrue(mvc.perform(get("/financeiro/resumo").header("Authorization", "Bearer lixo")).andReturn().getResponse().getStatus() / 100 == 4);
        // limite do tamanho de página: nunca devolve mais de 100
        MvcResult grande = mvc.perform(get("/financeiro/proximos-vencimentos?size=100000").header("Authorization", "Bearer " + c.token())).andReturn();
        if (grande.getResponse().getStatus() == 200) {
            assertTrue(json.readTree(grande.getResponse().getContentAsString()).get("tamanhoPagina").asInt() <= 100);
        } else {
            assertTrue(grande.getResponse().getStatus() / 100 == 4, "status " + grande.getResponse().getStatus());
        }
    }

    private UUID idCategoria(String nome) {
        return categoriaService.listar(false).stream().filter(x -> x.nome().equals(nome)).findFirst().orElseThrow().id();
    }
}
