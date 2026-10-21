package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.ConfiguracaoPrecificacaoRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 #974 e #1001 (RN-NOVA-25, CEN-NOVO-72 a 75): valor do estoque, potencial de venda, CMV, margem e insumos abaixo do mínimo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardCadastrosEstoqueIT {

    @Autowired DashboardCadastrosService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired InsumoRepository insumos;
    @Autowired ProdutoRepository produtos;
    @Autowired FichaTecnicaItemRepository fichas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ConfiguracaoPrecificacaoRepository configuracoes;
    @Autowired ClienteRepository clientes;
    @Autowired VendaCaixaRepository vendas;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private UnidadeMedida unidade;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-estoque-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        unidade = unidades.save(UnidadeMedida.builder().usuario(usuario).nome("un" + UUID.randomUUID()).sigla("u" + UUID.randomUUID().toString().substring(0, 6)).build());
        configuracoes.save(ConfiguracaoPrecificacao.builder().usuarioId(usuario.getId()).valorHora(bd("25.00")).build());
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Insumo insumo(String nome, int numero, String custo, String estoque, String minimo) {
        return insumos.save(Insumo.builder().usuario(usuario).numero(numero).nome(nome).unidadeMedida(unidade)
                .custoUnitario(bd(custo)).estoqueAtual(bd(estoque)).estoqueMinimo(minimo == null ? null : bd(minimo))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    @Test
    void cen72_materialProdutosProntosEPotencialConferemComAConta() {
        Insumo fita = insumo("Fita de cetim", 1, "3.00", "10", "15");
        insumo("Papel couchê", 2, "0.30", "100", "50");
        Insumo material = insumo("Material do laço", 3, "6.00", "0", null);
        Produto laco = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço pequeno").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("15.00")).estoqueAtual(bd("4")).ativo(true).build());
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(material).quantidade(BigDecimal.ONE).build());
        produtos.save(Produto.builder().usuario(usuario).numero(2).nome("Laço grande").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("20.00")).estoqueAtual(BigDecimal.ZERO).ativo(true).build());

        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(0, bd("60.00").compareTo(e.valorMaterial()));
        assertEquals(0, bd("40.67").compareTo(e.valorProdutosProntos()));
        assertEquals(0, bd("100.67").compareTo(e.valorTotal()));
        assertEquals(0, bd("60.00").compareTo(e.potencialVenda()));
        assertEquals(3, e.valorItens().size(), "fita, papel e laço pequeno; o laço grande (sem estoque) e o material (estoque zero) não entram");
        assertEquals(1, e.insumosAbaixoMinimo(), "só a fita (10 < 15); o papel tem 100 e o mínimo é 50");
        assertEquals(0, bd("5").compareTo(e.abaixoMinimoItens().get(0).falta()));
        assertEquals(0, bd("0").compareTo(e.percentualCmv().valor()), "sem vendas no período, 0%");
        assertEquals(0, bd("0").compareTo(e.margemMaterial().valor()), "sem faturamento, margem 0%");
        assertEquals(fita.getId(), e.abaixoMinimoItens().get(0).id());
    }

    /** Cadastros do CEN-NOVO-72: fita, papel, laço pequeno (custo gravado antigo R$ 8,0000) e laço grande sem estoque. */
    private Produto cadastrosDoCen72(String custoGravado) {
        insumo("Fita de cetim", 1, "3.00", "10", "15");
        insumo("Papel couchê", 2, "0.30", "100", "50");
        Insumo material = insumo("Material do laço", 3, "6.00", "0", null);
        Produto laco = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço pequeno").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("15.00")).precoCusto(bd(custoGravado)).estoqueAtual(bd("4")).ativo(true).build());
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(material).quantidade(BigDecimal.ONE).build());
        produtos.save(Produto.builder().usuario(usuario).numero(2).nome("Laço grande").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("20.00")).estoqueAtual(BigDecimal.ZERO).ativo(true).build());
        return laco;
    }

    @Test
    void cen73_produtoProntoUsaOCustoAtualEnaoOCustoGravado() {
        cadastrosDoCen72("8.0000");

        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(0, bd("40.67").compareTo(e.valorProdutosProntos()), "4 × R$ 10,1667 = R$ 40,6668, e não 4 × R$ 8,00 = R$ 32,00");
        DashboardCadastrosResponse.LinhaEstoque laco = e.valorItens().get(0);
        assertEquals("Laço pequeno", laco.nome());
        assertEquals(0, bd("10.17").compareTo(laco.custoUnitario().setScale(2, java.math.RoundingMode.HALF_UP)), "custo atual exibido R$ 10,17");
        assertEquals(0, bd("40.6668").compareTo(laco.valor()), "4 × R$ 10,1667; o arredondamento para R$ 40,67 vem só no total");
        assertEquals(0, bd("100.67").compareTo(e.valorTotal()));
    }

    @Test
    void cen75_listasDasLupasTemAsLinhasEAOrdemEspecificadas() {
        cadastrosDoCen72("8.0000");

        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(List.of("Laço pequeno", "Fita de cetim", "Papel couchê"), e.valorItens().stream().map(DashboardCadastrosResponse.LinhaEstoque::nome).toList(),
                "maior valor primeiro; o empate de R$ 30,00 desempata pelo nome");
        assertEquals(List.of("PRODUTO", "INSUMO", "INSUMO"), e.valorItens().stream().map(DashboardCadastrosResponse.LinhaEstoque::tipo).toList());
        assertEquals(0, bd("4").compareTo(e.valorItens().get(0).estoque()));
        assertEquals(0, bd("100.67").compareTo(e.valorTotal()), "total do rodapé");
        assertEquals(1, e.potencialItens().size());
        assertEquals("Laço pequeno", e.potencialItens().get(0).nome());
        assertEquals(0, bd("15.00").compareTo(e.potencialItens().get(0).precoVenda()));
        assertEquals(0, bd("60.00").compareTo(e.potencialItens().get(0).valor()));
        assertEquals(0, bd("60.00").compareTo(e.potencialVenda()));
        assertEquals(1, e.abaixoMinimoItens().size());
        DashboardCadastrosResponse.LinhaMinimo fita = e.abaixoMinimoItens().get(0);
        assertEquals("Fita de cetim", fita.nome());
        assertEquals(0, bd("10").compareTo(fita.estoque()));
        assertEquals(0, bd("15").compareTo(fita.minimo()));
        assertEquals(0, bd("5").compareTo(fita.falta()));
    }

    @Test
    void cen75_contaSemEstoqueMostraZeroSemErro() {
        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(0, BigDecimal.ZERO.compareTo(e.valorTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(e.potencialVenda()));
        assertTrue(e.valorItens().isEmpty());
        assertTrue(e.potencialItens().isEmpty());
        assertEquals(0, e.insumosAbaixoMinimo());
    }

    private void vendaCaixa(int numero, LocalDate dia, String total, String custo, Produto produto, CaixaTurno turno, Cliente cliente) {
        VendaCaixa v = vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(StatusVendaCaixa.CONCLUIDA)
                .caixaTurno(turno).dataVenda(dia.atTime(10, 0)).subtotal(bd(total)).total(bd(total)).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(produto).quantidade(BigDecimal.ONE).precoUnitario(bd(total))
                .subtotal(bd(total)).custoMaterialUnitario(bd(custo)).build());
    }

    @Test
    void cen74_percentualDeCmvEMargemComComparativoDoMesAnteriorEPeriodoSemFaturamento() {
        Cliente cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        CaixaTurno turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(LocalDate.of(2026, 9, 1).atStartOfDay())
                .valorAbertura(BigDecimal.ZERO).status(StatusCaixaTurno.ABERTO).build());
        Produto p = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço pequeno").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("80.00")).estoqueAtual(BigDecimal.ZERO).ativo(true).build());
        vendaCaixa(1, LocalDate.of(2026, 10, 5), "80.00", "20.00", p, turno, cliente);   // CX-1
        vendaCaixa(2, LocalDate.of(2026, 10, 10), "120.00", "30.00", p, turno, cliente); // CX-2
        vendaCaixa(3, LocalDate.of(2026, 9, 15), "100.00", "40.00", p, turno, cliente);  // mês anterior

        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(0, bd("25.00").compareTo(e.percentualCmv().valor()), "R$ 50,00 ÷ R$ 200,00");
        assertEquals(0, bd("40.00").compareTo(e.percentualCmv().anterior()), "R$ 40,00 ÷ R$ 100,00 no mês anterior");
        assertEquals(0, bd("75.00").compareTo(e.margemMaterial().valor()));
        assertEquals(0, bd("60.00").compareTo(e.margemMaterial().anterior()));

        DashboardCadastrosResponse.Estoque vazio = service.dashboard(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), null).estoque();
        assertEquals(0, BigDecimal.ZERO.compareTo(vazio.percentualCmv().valor()), "sem faturamento: 0%");
        assertEquals(0, BigDecimal.ZERO.compareTo(vazio.margemMaterial().valor()), "sem faturamento: 0%, não 100%");
    }
}
