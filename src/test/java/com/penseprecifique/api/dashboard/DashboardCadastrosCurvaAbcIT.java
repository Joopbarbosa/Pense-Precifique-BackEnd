package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V0.17.0 #1004 (QA da #990, RN-NOVA-24, CEN-NOVO-93): lucros do período — Laço grande R$ 70,00 (classe A), Caderno R$ 20,00 (B, de duas
 * vendas: ORC-21 e CX-8) e Laço pequeno R$ 10,00 (C). A curva traz os acumulados 70%, 90% e 100%; a lista de vendas do Caderno (o que a
 * barra abre) traz só ORC-21 e CX-8.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardCadastrosCurvaAbcIT {

    @Autowired DashboardCadastrosService cadastros;
    @Autowired DashboardVendasService vendasDashboard;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoItemRepository itensOrcamento;
    @Autowired VendaCaixaRepository vendasCaixa;
    @Autowired VendaCaixaItemRepository itensCaixa;
    @Autowired CaixaTurnoRepository turnos;

    private static final LocalDate DE = LocalDate.of(2026, 10, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 10, 31);

    private Usuario usuario;
    private Cliente cliente;
    private CaixaTurno turno;
    private Produto grande;
    private Produto caderno;
    private Produto pequeno;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("abc-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(DE.atStartOfDay()).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.ABERTO).build());
        grande = produto("Laço grande", 1);
        caderno = produto("Caderno", 2);
        pequeno = produto("Laço pequeno", 3);
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Produto produto(String nome, int numero) {
        return produtos.save(Produto.builder().usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(0).precoVenda(bd("100.00")).estoqueAtual(BigDecimal.ZERO).build());
    }

    /** Venda do Caixa de 1 unidade com custo de material R$ 1,00: o lucro é o total menos R$ 1,00. */
    private void caixa(int numero, Produto produto, String lucro, int dia) {
        BigDecimal total = bd(lucro).add(BigDecimal.ONE);
        VendaCaixa v = vendasCaixa.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(StatusVendaCaixa.CONCLUIDA)
                .caixaTurno(turno).dataVenda(DE.plusDays(dia).atTime(10, 0)).subtotal(total).total(total).build());
        itensCaixa.save(VendaCaixaItem.builder().vendaCaixa(v).produto(produto).quantidade(BigDecimal.ONE).precoUnitario(total)
                .subtotal(total).custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private void orcamento(int numero, Produto produto, String lucro, int dia) {
        BigDecimal total = bd(lucro).add(BigDecimal.ONE);
        Orcamento o = orcamentos.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(numero).status(StatusOrcamento.ENTREGUE)
                .dataEntrega(DE.plusDays(dia).atTime(12, 0)).createdAt(DE.plusDays(dia).atTime(9, 0)).subtotal(total).total(total).build());
        itensOrcamento.save(OrcamentoItem.builder().orcamento(o).produto(produto).quantidade(1).precoUnitario(total).subtotal(total)
                .custoMaterialUnitario(BigDecimal.ONE).build());
    }

    private void cenario() {
        caixa(1, grande, "70", 2);
        orcamento(21, caderno, "10", 3);
        caixa(8, caderno, "10", 4);
        caixa(2, pequeno, "10", 5);
    }

    @Test
    void tresClassesComOsAcumuladosDe70Por90E100PorCento() {
        cenario();

        DashboardCadastrosResponse.CurvaAbc abc = cadastros.dashboard(DE, ATE, null).curvaAbc();

        assertEquals(0, bd("100").compareTo(abc.totalLucro()));
        assertEquals(List.of("Laço grande:A:70.00", "Caderno:B:90.00", "Laço pequeno:C:100.00"),
                abc.itens().stream().map(i -> i.nome() + ":" + i.classe() + ":" + i.acumulado().toPlainString()).toList(),
                "do maior lucro para o menor, com as classes A até 80%, B até 95% e C acima");
        assertEquals(0, bd("20").compareTo(abc.itens().get(1).lucro()), "o Caderno junta o lucro das duas vendas, a do orçamento e a do Caixa");
        assertEquals(0, bd("20.00").compareTo(abc.itens().get(1).percentual()));
    }

    @Test
    void aBarraDoCadernoAbreSoAsVendasORC21ECX8() {
        cenario();

        ListaVendasResponse r = vendasDashboard.lista(DE, ATE, null, null, null, true, caderno.getId().toString(), null, PageRequest.of(0, 100));

        assertEquals(2, r.quantidadeFiltrada());
        assertEquals(List.of("CX-8", "ORC-21"), r.vendas().getContent().stream().map(ListaVendasResponse.Linha::identificador).sorted().toList(),
                "só as vendas do Caderno; nada de Laço grande nem de Laço pequeno");
    }
}
