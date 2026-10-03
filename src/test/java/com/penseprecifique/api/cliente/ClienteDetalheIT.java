package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteGraficosResponse;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresClienteResponse;
import com.penseprecifique.api.shared.dto.response.cliente.PedidoClienteResponse;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #560 (RN-NOVA-19, alteração da Decisão 13) e #451 (RN-NOVA-20) — indicadores, histórico e
 * gráficos do cliente. Compra = orçamento ENTREGUE (data de entrega) + venda do Caixa CONCLUIDA.
 *
 * <p>Massa (valores não redondos): ORC-1 ENTREGUE R$ 137,50 em 10/03 (Laço ×3, R$ 90,00 + Caixa ×1,
 * R$ 47,50); ORC-2 ENTREGUE R$ 62,30 em 02/05 (Caixa ×2); ORC-3 APROVADO R$ 45,00; ORC-4 CANCELADO
 * R$ 80,10; ORC-5 RASCUNHO R$ 20,00; CX-1 CONCLUIDA R$ 28,90 em 20/05 (Laço ×1); CX-2 CANCELADA
 * R$ 10,00.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClienteDetalheIT {

    @Autowired ClienteHistoricoService historicoService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;
    @Autowired VendaCaixaRepository vendaCaixaRepository;
    @Autowired VendaCaixaItemRepository vendaCaixaItemRepository;
    @Autowired CaixaTurnoRepository caixaTurnoRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private Produto laco;
    private Produto caixa;
    private CaixaTurno turno;
    private int numeroOrc = 1;
    private int numeroVenda = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-detalhe-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Papelaria Central").ativa(true).build());
        laco = produto(1, "Laço");
        caixa = produto(2, "Caixa presente");
        turno = caixaTurnoRepository.save(CaixaTurno.builder().usuario(usuario)
                .dataAbertura(LocalDateTime.of(2026, 5, 20, 8, 0)).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.FECHADO).build());
    }

    private Produto produto(int numero, String nome) {
        return produtoRepository.save(Produto.builder().usuario(usuario).numero(numero).nome(nome)
                .tipo(TipoProduto.PRODUTO).tempoProducao(10).estoqueAtual(BigDecimal.TEN)
                .precoVenda(BigDecimal.ONE).build());
    }

    private Orcamento orcamento(StatusOrcamento status, String total, LocalDateTime entrega) {
        return orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente)
                .numero(numeroOrc++).status(status).total(new BigDecimal(total)).subtotal(new BigDecimal(total))
                .dataEntrega(entrega).build());
    }

    private void itemOrc(Orcamento o, Produto p, int qtd, String subtotal) {
        orcamentoItemRepository.save(OrcamentoItem.builder().orcamento(o).produto(p).quantidade(qtd)
                .precoUnitario(new BigDecimal(subtotal)).subtotal(new BigDecimal(subtotal)).build());
    }

    private VendaCaixa venda(StatusVendaCaixa status, String total, LocalDateTime data) {
        return vendaCaixaRepository.save(VendaCaixa.builder().usuario(usuario).cliente(cliente)
                .numero(numeroVenda++).dataVenda(data).caixaTurno(turno).status(status)
                .subtotal(new BigDecimal(total)).total(new BigDecimal(total)).build());
    }

    private void massaCompleta() {
        Orcamento orc1 = orcamento(StatusOrcamento.ENTREGUE, "137.50", LocalDateTime.of(2026, 3, 10, 14, 0));
        itemOrc(orc1, laco, 3, "90.00");
        itemOrc(orc1, caixa, 1, "47.50");
        Orcamento orc2 = orcamento(StatusOrcamento.ENTREGUE, "62.30", LocalDateTime.of(2026, 5, 2, 9, 30));
        itemOrc(orc2, caixa, 2, "62.30");
        orcamento(StatusOrcamento.APROVADO, "45.00", null);
        orcamento(StatusOrcamento.CANCELADO, "80.10", null);
        orcamento(StatusOrcamento.RASCUNHO, "20.00", null);
        VendaCaixa cx1 = venda(StatusVendaCaixa.CONCLUIDA, "28.90", LocalDateTime.of(2026, 5, 20, 16, 45));
        vendaCaixaItemRepository.save(VendaCaixaItem.builder().vendaCaixa(cx1).produto(laco)
                .quantidade(BigDecimal.ONE).precoUnitario(new BigDecimal("28.90")).subtotal(new BigDecimal("28.90")).build());
        venda(StatusVendaCaixa.CANCELADA, "10.00", LocalDateTime.of(2026, 5, 21, 10, 0));
    }

    @Test
    void indicadoresDoCliente() {
        massaCompleta();
        IndicadoresClienteResponse ind = historicoService.indicadores(cliente.getId()).cliente();

        assertEquals(0, new BigDecimal("228.70").compareTo(ind.totalGasto()));
        assertEquals(3, ind.numeroPedidos());
        assertEquals(new BigDecimal("76.23"), ind.ticketMedio()); // 228,70 ÷ 3 = 76,2333…
        assertEquals("CX-1", ind.ultimaCompra().identificador());
        assertEquals(LocalDateTime.of(2026, 3, 10, 14, 0), ind.clienteDesde());
        // Laço 3 + 1 = 4 un. vence Caixa presente 1 + 2 = 3 un.
        assertEquals("Laço", ind.itemMaisComprado().nome());
        assertEquals(0, new BigDecimal("4").compareTo(ind.itemMaisComprado().quantidade()));
        assertEquals(0, new BigDecimal("118.90").compareTo(ind.itemMaisComprado().valor()));
        assertEquals(2, ind.orcamentosEmAberto().quantidade()); // APROVADO + RASCUNHO
        assertEquals(0, new BigDecimal("65.00").compareTo(ind.orcamentosEmAberto().valor()));
        assertEquals(1, ind.orcamentosCancelados().quantidade());
        assertEquals(0, new BigDecimal("80.10").compareTo(ind.orcamentosCancelados().valor()));
    }

    @Test
    void semComprasIndicadoresVazios() {
        orcamento(StatusOrcamento.APROVADO, "45.00", null);
        IndicadoresClienteResponse ind = historicoService.indicadores(cliente.getId()).cliente();
        assertEquals(0, ind.numeroPedidos());
        assertEquals(0, BigDecimal.ZERO.compareTo(ind.totalGasto()));
        assertNull(ind.ticketMedio());
        assertNull(ind.ultimaCompra());
        assertNull(ind.itemMaisComprado());
        assertNull(ind.clienteDesde());
    }

    @Test
    void historicoListaTodosOsPedidosComStatus() {
        massaCompleta();
        Page<PedidoClienteResponse> p1 = historicoService.historicoPedidos(cliente.getId(), PageRequest.of(0, 5));
        assertEquals(7, p1.getTotalElements());
        assertEquals(5, p1.getContent().size());
        Page<PedidoClienteResponse> p2 = historicoService.historicoPedidos(cliente.getId(), PageRequest.of(1, 5));
        assertEquals(2, p2.getContent().size());

        List<PedidoClienteResponse> todos = historicoService.historicoPedidos(cliente.getId(), PageRequest.of(0, 20)).getContent();
        long contam = todos.stream().filter(PedidoClienteResponse::contaComoCompra).count();
        assertEquals(3, contam);
        PedidoClienteResponse cancelada = todos.stream().filter(p -> p.identificador().equals("CX-2")).findFirst().orElseThrow();
        assertEquals("CANCELADA", cancelada.status());
        assertNull(cancelada.dataCompra());
    }

    @Test
    void graficoGastoMensalPreencheMesesVazios() {
        massaCompleta();
        ClienteGraficosResponse g = historicoService.graficos(cliente.getId(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31));

        assertEquals(3, g.gastoMensal().size());
        assertEquals(LocalDate.of(2026, 3, 1), g.gastoMensal().get(0).mes());
        assertEquals(0, new BigDecimal("137.50").compareTo(g.gastoMensal().get(0).total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(g.gastoMensal().get(1).total()));
        assertEquals(0, new BigDecimal("91.20").compareTo(g.gastoMensal().get(2).total())); // 62,30 + 28,90
        assertEquals(List.of("Laço", "Caixa presente"),
                g.itensMaisComprados().stream().map(i -> i.nome()).toList());
    }

    @Test
    void graficoRespeitaPeriodo() {
        massaCompleta();
        ClienteGraficosResponse g = historicoService.graficos(cliente.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        assertEquals(1, g.gastoMensal().size());
        assertEquals(0, new BigDecimal("91.20").compareTo(g.gastoMensal().get(0).total()));
        // só ORC-2 (Caixa ×2) e CX-1 (Laço ×1) no período
        assertEquals("Caixa presente", g.itensMaisComprados().get(0).nome());
    }

    @Test
    void cadastroDeOutraUsuariaDa404() {
        Usuario outra = usuarioRepository.save(Usuario.builder()
                .email("cli-detalhe-outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        Cliente deOutra = clienteRepository.save(Cliente.builder().usuario(outra).numero(1).nome("X").ativa(true).build());
        assertThrows(ResourceNotFoundException.class, () -> historicoService.indicadores(deOutra.getId()));
    }
}
