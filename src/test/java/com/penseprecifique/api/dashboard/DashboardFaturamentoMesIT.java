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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V0.17.0 #1022 (RN-NOVA-37, CEN-NOVO-124 a 127): o "Faturamento do mês" da Início soma as vendas da RN-NOVA-12 (orçamento ENTREGUE pela
 * data de entrega e venda do Caixa CONCLUÍDA) no mês atual de Brasília, igual ao Dashboard de Vendas; pago e não entregue, cancelado e
 * outro mês não contam. O relógio de Brasília é fixo e a JVM roda em UTC para provar a virada do mês.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.datasource.hikari.maximum-pool-size=3")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DashboardFaturamentoMesIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static volatile Instant agora = Instant.parse("2026-10-20T15:00:00Z"); // 20/10/2026 12:00 em Brasília

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
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itensOrcamento;
    @Autowired VendaCaixaRepository vendasCaixa;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private Cliente cliente;
    private CaixaTurno turno;
    private Produto produto;
    private int numero;
    private TimeZone fusoOriginal;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        fusoOriginal = TimeZone.getDefault();
        agora = Instant.parse("2026-10-20T15:00:00Z");
        usuario = usuarios.save(Usuario.builder().email("fm-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(LocalDateTime.of(2026, 9, 1, 8, 0)).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
        produto = produtos.save(Produto.builder().usuario(usuario).numero(++numero).nome("Caderno").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("10.00")).estoqueAtual(BigDecimal.ZERO).build());
    }

    @AfterEach
    void limpar() {
        TimeZone.setDefault(fusoOriginal);
        SecurityContextHolder.clearContext();
    }

    private void orcamento(StatusOrcamento status, LocalDateTime entrega, LocalDateTime pagamento, String total) {
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(++numero).status(status)
                .dataEntrega(entrega).dataPagamento(pagamento).createdAt(LocalDateTime.of(2026, 9, 1, 9, 0)).subtotal(bd(total)).total(bd(total)).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private void caixa(StatusVendaCaixa status, LocalDateTime data, String total) {
        VendaCaixa v = vendasCaixa.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(++numero).status(status).caixaTurno(turno)
                .dataVenda(data).subtotal(bd(total)).total(bd(total)).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(produto).quantidade(BigDecimal.ONE).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private BigDecimal receitaMes() {
        return dashboard.getDashboard().getReceitaMes();
    }

    @Test
    void cen124SomaOrcamentoEntregueEVendaDoCaixaEIgualAoDashboardDeVendas() {
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 5, 10, 0), null, "100.00");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 10, 12, 10, 0), "40.00");

        assertEquals(0, bd("140.00").compareTo(receitaMes()));
        BigDecimal vendas = dashboardVendas.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).faturamento().valor();
        assertEquals(0, vendas.compareTo(receitaMes()), "Início e Dashboard de Vendas mostram o mesmo número");
    }

    @Test
    void cen125PagoENaoEntregueCanceladoEOutroMesNaoContam() {
        orcamento(StatusOrcamento.APROVADO, null, LocalDateTime.of(2026, 10, 3, 10, 0), "200.00");
        caixa(StatusVendaCaixa.CANCELADA, LocalDateTime.of(2026, 10, 4, 10, 0), "30.00");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 9, 28, 10, 0), LocalDateTime.of(2026, 10, 2, 10, 0), "90.00");
        orcamento(StatusOrcamento.ENTREGUE, LocalDateTime.of(2026, 10, 6, 10, 0), null, "70.00");

        assertEquals(0, bd("70.00").compareTo(receitaMes()));
    }

    @Test
    void cen126ViradaDoMesNoFusoDeBrasiliaMesmoComAJvmEmUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 9, 15, 10, 0), "25.00");
        caixa(StatusVendaCaixa.CONCLUIDA, LocalDateTime.of(2026, 9, 30, 21, 30), "50.00");

        agora = LocalDateTime.of(2026, 9, 30, 22, 0).atZone(BRASILIA).toInstant(); // já é 01/10 01:00 em UTC
        assertEquals(0, bd("75.00").compareTo(receitaMes()), "ainda é setembro em Brasília");

        agora = LocalDateTime.of(2026, 10, 1, 0, 5).atZone(BRASILIA).toInstant();
        assertEquals(0, BigDecimal.ZERO.compareTo(receitaMes()), "outubro começa às 00:00 de Brasília e as vendas são de setembro");
    }

    @Test
    void cen127MesSemVendaMostraZeroEOutraContaNaoEntra() {
        assertEquals(0, BigDecimal.ZERO.compareTo(receitaMes()));
        Usuario outra = usuarios.save(Usuario.builder().email("fm-outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        Cliente c2 = clientes.save(Cliente.builder().usuario(outra).numero(1).nome("Bia").ehCliente(true).ativa(true).build());
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(outra).cliente(c2).numero(++numero).status(StatusOrcamento.ENTREGUE)
                .dataEntrega(LocalDateTime.of(2026, 10, 7, 10, 0)).createdAt(LocalDateTime.of(2026, 9, 1, 9, 0)).subtotal(bd("500")).total(bd("500")).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(bd("500")).subtotal(bd("500"))
                .custoMaterialUnitario(BigDecimal.ONE).build());
        assertEquals(0, BigDecimal.ZERO.compareTo(receitaMes()), "venda de outra conta nunca entra");
    }
}
