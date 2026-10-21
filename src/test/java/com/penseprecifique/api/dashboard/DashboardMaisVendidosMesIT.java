package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.dashboard.ProdutoVendidoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V0.17.0 #999 (RN-NOVA-35, CEN-NOVO-109, 110, 112, 113, 114, 115, 116): o "Mais vendidos" da Início conta só vendas do mês atual de
 * Brasília (orçamento ENTREGUE pela data de entrega e venda do Caixa CONCLUÍDA), agrupa por tipo e cadastro e desempata como a Spec manda.
 * O relógio de Brasília é trocado por um relógio fixo; a virada do mês também roda com a JVM em UTC.
 */
// contexto próprio (relógio fixo): pool pequeno e fechado ao fim, para não esgotar as conexões do banco de teste
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.datasource.hikari.maximum-pool-size=3")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DashboardMaisVendidosMesIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static volatile Instant agora = Instant.parse("2026-10-19T15:00:00Z"); // 19/10/2026 12:00 em Brasília

    @TestConfiguration
    static class RelogioFixo {
        @Bean @Primary
        Clock relogioDeTeste() {
            return new Clock() {
                @Override public ZoneId getZone() { return BRASILIA; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return agora; }
            };
        }
    }

    @Autowired DashboardService dashboard;
    @Autowired DashboardVendasService dashboardVendas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired CatalogoRepository catalogos;
    @Autowired ItemCatalogoRepository itensCatalogo;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itensOrcamento;
    @Autowired VendaCaixaRepository vendasCaixa;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private Cliente cliente;
    private CaixaTurno turno;
    private Catalogo catalogo;
    private int numero;
    private TimeZone fusoOriginal;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        fusoOriginal = TimeZone.getDefault();
        agora = Instant.parse("2026-10-19T15:00:00Z");
        usuario = usuarios.save(Usuario.builder().email("mv-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(LocalDateTime.of(2026, 9, 1, 8, 0)).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
        catalogo = catalogos.save(Catalogo.builder().usuario(usuario).numero(1).nome("Natal").build());
    }

    @AfterEach
    void limpar() {
        TimeZone.setDefault(fusoOriginal);
        SecurityContextHolder.clearContext();
    }

    private Produto produto(String nome) {
        return produtos.save(Produto.builder().usuario(usuario).numero(++numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("10.00")).estoqueAtual(BigDecimal.ZERO).build());
    }

    private ItemCatalogo item(String nome) {
        return itensCatalogo.save(ItemCatalogo.builder().catalogo(catalogo).nome(nome).tempoProducao(0).precoVenda(bd("10.00")).build());
    }

    /** Orçamento com um item de produto ou de catálogo (um dos dois é nulo). */
    private void orcamento(StatusOrcamento status, LocalDateTime entrega, LocalDateTime criacao, Produto p, ItemCatalogo ic, int qtd) {
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(++numero).status(status)
                .dataEntrega(entrega).createdAt(criacao).subtotal(bd("10")).total(bd("10")).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(p).itemCatalogo(ic).quantidade(qtd).precoUnitario(bd("10"))
                .subtotal(bd("10")).custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private void caixa(StatusVendaCaixa status, LocalDateTime data, Produto p, ItemCatalogo ic, String qtd) {
        VendaCaixa v = vendasCaixa.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(++numero).status(status).caixaTurno(turno)
                .dataVenda(data).subtotal(bd("10")).total(bd("10")).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(p).itemCatalogo(ic).quantidade(bd(qtd)).precoUnitario(bd("10"))
                .subtotal(bd("10")).custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private List<String> ranking() {
        return dashboard.getDashboard().getProdutosMaisVendidos().stream()
                .map(p -> p.getTipo() + ":" + p.getNomeProduto() + ":" + p.getQuantidade().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    void cen109ContaSoVendasDoMesPelaDataDeEntregaEIgnoraRascunhoECancelada() {
        Produto laco = produto("Laço pequeno");
        Produto caderno = produto("Caderno");
        Produto fita = produto("Fita de cetim");
        // ORC-1: criado em 28/09, entregue em 05/10 → conta em outubro; CX-1 concluída em 12/10
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 5, 10, 0), LocalDateTime.of(2026, 9, 28, 9, 0), laco, null, 3);
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 12, 11, 0), laco, null, "2");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 12, 11, 0), caderno, null, "1");
        // ORC-2: criado em 10/09, entregue em 30/09 → fora do mês
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 9, 30, 10, 0), LocalDateTime.of(2026, 9, 10, 9, 0), caderno, null, 10);
        // ORC-3 rascunho e CX-2 cancelada → não contam
        orcamento(StatusOrcamento.RASCUNHO, null, LocalDateTime.of(2026, 10, 10, 9, 0), fita, null, 8);
        caixa(StatusVendaCaixa.CANCELADA, LocalDateTime.of(2026, 10, 14, 9, 0), caderno, null, "5");

        assertEquals(List.of("PRODUTO:Laço pequeno:5", "PRODUTO:Caderno:1"), ranking());
    }

    @Test
    void cen110SemVendaNoMesDevolveListaVazia() {
        Produto caderno = produto("Caderno");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 9, 30, 10, 0), LocalDateTime.of(2026, 9, 10, 9, 0), caderno, null, 10);

        assertEquals(List.of(), ranking());
    }

    @Test
    void cen112EmpateDeQuantidadeDesempataPeloNome() {
        Produto laco = produto("Laço pequeno");
        Produto caderno = produto("Caderno");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 3, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), laco, null, 4);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 4, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), caderno, null, 4);

        assertEquals(List.of("PRODUTO:Caderno:4", "PRODUTO:Laço pequeno:4"), ranking());
    }

    @Test
    void cen113ViradaDoMesNoFusoDeBrasiliaMesmoComAJvmEmUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Produto fita = produto("Fita de cetim");
        // entregue em 31/10/2026 às 23:30 de Brasília (= 01/11 02:30 UTC)
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 31, 23, 30), LocalDateTime.of(2026, 10, 20, 9, 0), fita, null, 6);

        agora = LocalDateTime.of(2026, 10, 31, 23, 45).atZone(BRASILIA).toInstant();
        assertEquals(List.of("PRODUTO:Fita de cetim:6"), ranking(), "ainda é outubro em Brasília");

        agora = LocalDateTime.of(2026, 11, 1, 0, 0).atZone(BRASILIA).toInstant();
        assertEquals(List.of(), ranking(), "novembro começa às 00:00 de Brasília e a venda é de outubro");
    }

    @Test
    void cen114CadastrosDiferentesComOMesmoNomeFicamEmLinhasSeparadas() {
        Produto cad12 = produto("Caderno");
        Produto cad31 = produto("Caderno");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 6, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), cad12, null, 3);
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 8, 10, 0), cad31, null, "2");

        List<ProdutoVendidoDTO> r = dashboard.getDashboard().getProdutosMaisVendidos();
        assertEquals(2, r.size());
        assertEquals(cad12.getId(), r.get(0).getId());
        assertEquals(0, bd("3").compareTo(r.get(0).getQuantidade()));
        assertEquals(cad31.getId(), r.get(1).getId());
        assertEquals(0, bd("2").compareTo(r.get(1).getQuantidade()));
    }

    @Test
    void cen115ItemDeCatalogoFicaSeparadoDoProdutoDeMesmoNome() {
        ItemCatalogo fitaCatalogo = item("Fita de cetim");
        Produto fitaProduto = produto("Fita de cetim");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 7, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), null, fitaCatalogo, 4);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), null, fitaCatalogo, 2);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 9, 11, 0), LocalDateTime.of(2026, 10, 1, 9, 0), fitaProduto, null, 3);

        assertEquals(List.of("CATALOGO:Fita de cetim:6", "PRODUTO:Fita de cetim:3"), ranking());
    }

    @Test
    void cen116EmpateDeQuantidadeENomeColocaItemDeCatalogoAntesDoProduto() {
        ItemCatalogo fitaCatalogo = item("Fita de cetim");
        Produto fitaProduto = produto("Fita de cetim");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 8, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), null, fitaCatalogo, 3);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), fitaProduto, null, 3);

        assertEquals(List.of("CATALOGO:Fita de cetim:3", "PRODUTO:Fita de cetim:3"), ranking());
    }

    @Test
    void nomesQueSoDiferemPorAcentoDesempatamPelosCaracteresOriginais() {
        Produto sem = produto("Laco");
        Produto com = produto("Laço");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 8, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), com, null, 2);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), sem, null, 2);

        assertEquals(List.of("PRODUTO:Laco:2", "PRODUTO:Laço:2"), ranking(), "letra sem acento antes da acentuada");
    }

    @Test
    void quantidadeFracionariaDoCaixaNaoPerdeUnidades() {
        Produto fita = produto("Fita de cetim");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 8, 10, 0), fita, null, "0.5");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 9, 10, 0), fita, null, "1.25");

        assertEquals(List.of("PRODUTO:Fita de cetim:1.75"), ranking());
    }

    @Test
    void soOsCincoPrimeirosEntram() {
        for (int i = 1; i <= 7; i++) {
            orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 3, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), produto("P" + i), null, i);
        }
        assertEquals(List.of("PRODUTO:P7:7", "PRODUTO:P6:6", "PRODUTO:P5:5", "PRODUTO:P4:4", "PRODUTO:P3:3"), ranking());
    }

    @Test
    void dashboardDeVendasMantemASuaOrdemNoEmpateDeNomesQueSoDiferemPorAcento() {
        // regressão (plano da #999): o Dashboard de Vendas NÃO ganha o desempate pelos caracteres originais; segue por tipo e id
        Produto sem = produto("Laco");
        Produto com = produto("Laço");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 8, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), com, null, 2);
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 1, 9, 0), sem, null, 2);

        List<UUID> ordem = dashboardVendas.dashboard(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31))
                .produtosMaisVendidos().stream().map(DashboardVendasResponse.ProdutoVendido::id).toList();

        assertEquals(java.util.stream.Stream.of(sem.getId(), com.getId()).sorted(java.util.Comparator.comparing(UUID::toString)).toList(), ordem,
                "empate total na normalização: desempata por tipo e id, como antes");
    }

    @Test
    void cen123RankingTrazAMarcaDeCadaCadastro() {
        Produto pense = produtos.save(Produto.builder().usuario(usuario).numero(++numero).nome("Caderno").marca("Pense").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("10.00")).estoqueAtual(BigDecimal.ZERO).build());
        Produto escolar = produtos.save(Produto.builder().usuario(usuario).numero(++numero).nome("Caderno").marca("Escolar").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("10.00")).estoqueAtual(BigDecimal.ZERO).build());
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 8, 10, 0), pense, null, "3");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 9, 10, 0), escolar, null, "2");

        List<ProdutoVendidoDTO> r = dashboard.getDashboard().getProdutosMaisVendidos();
        assertEquals(List.of("Pense", "Escolar"), r.stream().map(ProdutoVendidoDTO::getMarca).toList());
        assertEquals(List.of("Caderno", "Caderno"), r.stream().map(ProdutoVendidoDTO::getNomeProduto).toList());
    }
}
