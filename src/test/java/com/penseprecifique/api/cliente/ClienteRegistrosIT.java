package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.cliente.RegistroCadastroResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 (ajuste do teste manual de #560/#451) — {@code GET /clientes/{id}/registros}: modal de
 * listagem do detalhe do cadastro, com busca, filtros e ordenação no servidor.
 *
 * <p>Massa do cliente (a mesma de ClienteDetalheIT): ORC-1 ENTREGUE R$ 137,50 em 10/03 (Laço ×3 + Caixa
 * presente ×1); ORC-2 ENTREGUE R$ 62,30 em 02/05 (Caixa presente ×2); ORC-3 APROVADO R$ 45,00; ORC-4
 * CANCELADO R$ 80,10; CX-1 CONCLUIDA R$ 28,90 em 20/05 (Laço ×1); CX-2 CANCELADA R$ 10,00.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClienteRegistrosIT {

    @Autowired ClienteHistoricoService historicoService;
    @Autowired CompraService compraService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;
    @Autowired VendaCaixaRepository vendaCaixaRepository;
    @Autowired VendaCaixaItemRepository vendaCaixaItemRepository;
    @Autowired CaixaTurnoRepository caixaTurnoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private Produto laco;
    private CaixaTurno turno;
    private int numeroOrc = 1;
    private int numeroVenda = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-registros-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Papelaria Central")
                .ehCliente(true).ehFornecedor(true).ativa(true).build());
        laco = produto(1, "Laço");
        Produto caixa = produto(2, "Caixa presente");
        turno = caixaTurnoRepository.save(CaixaTurno.builder().usuario(usuario)
                .dataAbertura(LocalDateTime.of(2026, 5, 20, 8, 0)).valorAbertura(BigDecimal.ZERO)
                .status(StatusCaixaTurno.FECHADO).build());

        Orcamento orc1 = orcamento(StatusOrcamento.ENTREGUE, "137.50", LocalDateTime.of(2026, 3, 10, 14, 0));
        itemOrc(orc1, laco, 3, "90.00");
        itemOrc(orc1, caixa, 1, "47.50");
        Orcamento orc2 = orcamento(StatusOrcamento.ENTREGUE, "62.30", LocalDateTime.of(2026, 5, 2, 9, 30));
        itemOrc(orc2, caixa, 2, "62.30");
        orcamento(StatusOrcamento.APROVADO, "45.00", null);
        orcamento(StatusOrcamento.CANCELADO, "80.10", null);
        VendaCaixa cx1 = venda(StatusVendaCaixa.CONCLUIDA, "28.90", LocalDateTime.of(2026, 5, 20, 16, 45));
        vendaCaixaItemRepository.save(VendaCaixaItem.builder().vendaCaixa(cx1).produto(laco)
                .quantidade(BigDecimal.ONE).precoUnitario(new BigDecimal("28.90")).subtotal(new BigDecimal("28.90")).build());
        venda(StatusVendaCaixa.CANCELADA, "10.00", LocalDateTime.of(2026, 5, 21, 10, 0));
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

    private List<RegistroCadastroResponse> cliente(String busca, List<String> status, boolean somenteCompras,
                                                   LocalDate de, LocalDate ate, UUID itemId, Pageable p) {
        return historicoService.registros(cliente.getId(), PapelCadastro.CLIENTE, busca, status, somenteCompras,
                false, de, ate, itemId, p).getContent();
    }

    private static List<String> ids(List<RegistroCadastroResponse> r) {
        return r.stream().map(RegistroCadastroResponse::identificador).toList();
    }

    @Test
    void listaTodosOsPedidosComColunasPadronizadas() {
        List<RegistroCadastroResponse> todos = cliente(null, null, false, null, null, null, PageRequest.of(0, 20));
        assertEquals(6, todos.size());

        RegistroCadastroResponse orc1 = todos.stream().filter(r -> r.identificador().equals("ORC-1")).findFirst().orElseThrow();
        assertEquals("ORCAMENTO", orc1.tipo());
        assertEquals(LocalDate.of(2026, 3, 10), orc1.data()); // conta como compra → data de entrega
        assertEquals(2, orc1.quantidadeItens());
        assertEquals("Laço ×3, Caixa presente ×1", orc1.resumoItens());
        assertTrue(orc1.contaComoCompra());
        assertNull(orc1.pago());
    }

    /** #763 — itens da venda do Caixa saem na ordem em que foram lançados (antes dependia da ordem do PostgreSQL). */
    @Test
    void resumoDosItensDaVendaDoCaixaSegueAOrdemDeLancamento() {
        Produto cartao = produto(3, "Cartão");
        Produto caixa = produto(4, "Caixa grande");
        VendaCaixa cx3 = venda(StatusVendaCaixa.CONCLUIDA, "60.00", LocalDateTime.of(2026, 5, 22, 10, 0));
        for (Object[] item : new Object[][] {{cartao, 2}, {laco, 3}, {caixa, 1}}) {
            vendaCaixaItemRepository.save(VendaCaixaItem.builder().vendaCaixa(cx3).produto((Produto) item[0])
                    .quantidade(BigDecimal.valueOf((int) item[1])).precoUnitario(BigDecimal.TEN)
                    .subtotal(BigDecimal.TEN).build());
        }
        for (int repeticao = 0; repeticao < 3; repeticao++) {
            RegistroCadastroResponse r = cliente(null, null, false, null, null, null, PageRequest.of(0, 20)).stream()
                    .filter(x -> x.identificador().equals("CX-" + cx3.getNumero())).findFirst().orElseThrow();
            assertEquals("Cartão ×2, Laço ×3, Caixa grande ×1", r.resumoItens());
        }
    }

    @Test
    void somenteComprasEPeriodoDoMesDoGrafico() {
        // Clique na barra de maio no gráfico: só o que conta como compra, dentro do mês.
        List<RegistroCadastroResponse> maio = cliente(null, null, true,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), null, PageRequest.of(0, 20));
        assertEquals(List.of("CX-1", "ORC-2"), ids(maio)); // mais recente primeiro
        assertEquals(0, new BigDecimal("91.20").compareTo(maio.stream().map(RegistroCadastroResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add))); // 28,90 + 62,30 = total da barra de maio
    }

    @Test
    void filtroPorItemDoRankingEBuscaSemAcento() {
        // Clique em "Laço" no "O que mais comprou": pedidos que têm o item.
        assertEquals(List.of("CX-1", "ORC-1"),
                ids(cliente(null, null, true, null, null, laco.getId(), PageRequest.of(0, 20))));
        assertEquals(List.of("CX-1", "ORC-1"),
                ids(cliente("laco", null, false, null, null, null, PageRequest.of(0, 20))));
        assertEquals(List.of("ORC-4"), ids(cliente("orc-4", null, false, null, null, null, PageRequest.of(0, 20))));
        assertEquals(List.of("ORC-4"), ids(cliente(null, List.of("CANCELADO"), false, null, null, null, PageRequest.of(0, 20))));
        // "Orçamentos em aberto" = vários status de uma vez.
        assertEquals(List.of("ORC-3"), ids(cliente(null, List.of("RASCUNHO", "ENVIADO", "APROVADO", "EM_PRODUCAO", "FINALIZADO", "PAGO"),
                false, null, null, null, PageRequest.of(0, 20))));
    }

    @Test
    void ordenaPorValorENumero() {
        assertEquals(List.of("ORC-1", "ORC-4", "ORC-2", "ORC-3", "CX-1", "CX-2"),
                ids(cliente(null, null, false, null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "valor")))));
        // Número: ORCAMENTO antes de VENDA_CAIXA, número numérico (ORC-10 viria depois de ORC-9).
        assertEquals(List.of("ORC-1", "ORC-2", "ORC-3", "ORC-4", "CX-1", "CX-2"),
                ids(cliente(null, null, false, null, null, null, PageRequest.of(0, 20, Sort.by("identificador")))));
        Page<RegistroCadastroResponse> pagina = historicoService.registros(cliente.getId(), PapelCadastro.CLIENTE,
                null, null, false, false, null, null, null, PageRequest.of(1, 4));
        assertEquals(6, pagina.getTotalElements());
        assertEquals(2, pagina.getContent().size());
    }

    @Test
    void campoDeOrdenacaoInvalidoEPeriodoInvertidoSao400() {
        assertThrows(BusinessException.class, () -> cliente(null, null, false, null, null, null,
                PageRequest.of(0, 20, Sort.by("senha"))));
        assertThrows(BusinessException.class, () -> cliente(null, null, false,
                LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1), null, PageRequest.of(0, 20)));
    }

    @Test
    void ladoFornecedorComNaoPagasEInsumo() {
        UnidadeMedida un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
        Insumo fita = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Fita de cetim")
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
        Insumo cola = insumoRepository.save(Insumo.builder().usuario(usuario).numero(2).nome("Cola branca")
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
        LocalDate d = LocalDate.of(2026, 4, 7);
        CompraResponse paga = compraService.confirmarNova(new CompraRequest(d, false, cliente.getId(), false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("12.5"), new BigDecimal("23.75")))) ).compra();
        CompraResponse naoPaga = compraService.confirmarNova(new CompraRequest(d.plusDays(1), false, cliente.getId(), false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, new BigDecimal("3"), new BigDecimal("41.90"))))).compra();
        CompraResponse cancelada = compraService.confirmarNova(new CompraRequest(d.plusDays(2), false, cliente.getId(), false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, new BigDecimal("2.10"))))).compra();
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest("Pedido duplicado por engano no sistema.", true));
        MetodoPagamentoConfiguravel pix = metodoPagamentoRepository.save(MetodoPagamentoConfiguravel.builder()
                .usuario(usuario).tipo(TipoMetodoPagamento.PIX).ativo(true).ordem(1).build());
        compraService.atualizarPagamento(paga.id(), new PagamentoCompraRequest(true, pix.getId()));

        List<RegistroCadastroResponse> todas = historicoService.registros(cliente.getId(), PapelCadastro.FORNECEDOR,
                null, null, false, false, null, null, null, PageRequest.of(0, 20)).getContent();
        assertEquals(3, todas.size());
        RegistroCadastroResponse primeira = todas.get(todas.size() - 1);
        assertEquals(paga.identificador(), primeira.identificador());
        assertEquals("COMPRA", primeira.tipo());
        assertEquals("Fita de cetim ×12,5 m", primeira.resumoItens());
        assertEquals(0, new BigDecimal("23.75").compareTo(primeira.valor()));

        List<RegistroCadastroResponse> naoPagas = historicoService.registros(cliente.getId(), PapelCadastro.FORNECEDOR,
                null, null, false, true, null, null, null, PageRequest.of(0, 20)).getContent();
        assertEquals(List.of(naoPaga.identificador()), ids(naoPagas));
        assertFalse(naoPagas.get(0).pago());

        assertEquals(List.of(paga.identificador()), ids(historicoService.registros(cliente.getId(), PapelCadastro.FORNECEDOR,
                null, null, true, false, null, null, fita.getId(), PageRequest.of(0, 20)).getContent()));
    }
}
