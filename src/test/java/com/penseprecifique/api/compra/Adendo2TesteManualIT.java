package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteHistoricoService;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.RegraPrecoReferencia;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.GerarListaCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PrecoReferenciaRequest;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteGraficosResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraContagensResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.FornecedorInsumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 — adendo 2 do teste manual: #590 (preço de referência), #596/#595 (status da lista),
 * #597 (parcelas), #593 (duplicar sem descontos), #591/#585 (contagens e filtros), #587 (gráficos de
 * fornecedor), #583/#616 (inativos nos seletores), #605 (unidades do sistema).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Adendo2TesteManualIT {

    @Autowired CompraService compraService;
    @Autowired ListaCompraService listaCompraService;
    @Autowired FornecedorInsumoService fornecedorInsumoService;
    @Autowired InsumoService insumoService;
    @Autowired ClienteService clienteService;
    @Autowired ClienteHistoricoService clienteHistoricoService;
    @Autowired UnidadeMedidaService unidadeMedidaService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroCadastro = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("adendo2-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Peça").sigla("pc").build());
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private Cliente cadastro(String nome, boolean cliente, boolean fornecedor, boolean ativa) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(cliente).ehFornecedor(fornecedor).ativa(ativa).build());
    }

    private static CompraItemRequest linha(Insumo i, String qtd, String preco) {
        return new CompraItemRequest(i.getId(), null, new BigDecimal(qtd), preco != null ? new BigDecimal(preco) : null);
    }

    private CompraResponse confirmar(Cliente fornecedor, LocalDate data, CompraItemRequest... linhas) {
        return compraService.confirmarNova(new CompraRequest(data, false, fornecedor.getId(), false, null, null,
                List.of(linhas))).compra();
    }

    private FornecedorInsumoResponse vinculo(Cliente fornecedor) {
        return fornecedorInsumoService.listar(fornecedor.getId(), null).get(0);
    }

    private static void assertValor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", veio " + real);
    }

    // ------------------------------------------------------------------ #590 — preço de referência

    @Test
    void cen65_mediaPonderadaEReflexoDoCancelamento() {
        Cliente papelaria = cadastro("Papelaria Central", false, true, true);
        Insumo fita = insumo("Fita de cetim");
        confirmar(papelaria, LocalDate.now().minusDays(20), linha(fita, "10", "25.65")); // 2,565
        CompraResponse segunda = confirmar(papelaria, LocalDate.now(), linha(fita, "20", "56.00")); // 2,80

        FornecedorInsumoResponse v = vinculo(papelaria);
        assertValor("2.7217", v.precoReferencia()); // (25,65 + 56,00) ÷ 30
        assertEquals(RegraPrecoReferencia.MEDIA, v.regraPrecoReferencia());
        assertEquals(segunda.id(), v.ultimaCompra().compraId());
        assertValor("2.8000", v.ultimaCompra().precoUnitario());

        compraService.cancelar(segunda.id(), new CancelarCompraRequest(
                "O fornecedor entregou o pedido errado e devolvemos", true));
        assertValor("2.5650", vinculo(papelaria).precoReferencia());
    }

    @Test
    void cen66_menorValorEManual() {
        Cliente papelaria = cadastro("Papelaria Central", false, true, true);
        Insumo fita = insumo("Fita de cetim");
        confirmar(papelaria, LocalDate.now().minusDays(5), linha(fita, "10", "25.65"));
        confirmar(papelaria, LocalDate.now(), linha(fita, "20", "56.00"));

        insumoService.editar(fita.getId(), comRegra(fita, RegraPrecoReferencia.MENOR_VALOR));
        assertValor("2.5650", vinculo(papelaria).precoReferencia());

        insumoService.editar(fita.getId(), comRegra(fita, RegraPrecoReferencia.MANUAL));
        fornecedorInsumoService.atualizarPreco(vinculo(papelaria).id(), new PrecoReferenciaRequest(new BigDecimal("2.50")));
        confirmar(papelaria, LocalDate.now(), linha(fita, "5", "15.00")); // 3,00
        assertValor("2.50", vinculo(papelaria).precoReferencia());
    }

    @Test
    void compraForaDaJanelaDe12MesesNaoEntraNaMedia() {
        Cliente papelaria = cadastro("Papelaria Central", false, true, true);
        Insumo fita = insumo("Fita de cetim");
        confirmar(papelaria, LocalDate.now().minusMonths(13), linha(fita, "10", "10.00")); // 1,00, fora
        confirmar(papelaria, LocalDate.now(), linha(fita, "10", "30.00")); // 3,00
        assertValor("3.0000", vinculo(papelaria).precoReferencia());
    }

    private InsumoRequestDTO comRegra(Insumo i, RegraPrecoReferencia regra) {
        return new InsumoRequestDTO(i.getNome(), null, un.getId(), true, null, true, null, null, regra, null);
    }

    // ------------------------------------------------------------------ #596/#595 — lista

    private GerarListaCompraRequest lista(Insumo... insumos) {
        return new GerarListaCompraRequest(java.util.Arrays.stream(insumos)
                .map(i -> new GerarListaCompraRequest.Linha(i.getId(), new BigDecimal("12"), null)).toList());
    }

    @Test
    void cen69_statusDaListaAcompanhaAsCompras() {
        Insumo barbante = insumo("Barbante cru");
        Insumo kraft = insumo("Papel Kraft");
        ListaCompraResponse lst = listaCompraService.gerar(lista(barbante, kraft));
        assertEquals(StatusListaCompra.GERADA, lst.status());

        CompraResponse c1 = listaCompraService.criarCompra(lst.id());
        assertEquals(lst.identificador(), c1.listaCompra().identificador());
        CompraResponse c1Editada = compraService.atualizarRascunho(c1.id(), new CompraRequest(LocalDate.now(), false, null,
                false, null, null, List.of(linha(barbante, "12", "30.00"))));
        compraService.confirmar(c1Editada.id(), null);
        assertEquals(StatusListaCompra.PARCIALMENTE_COMPRADA, listaCompraService.buscar(lst.id()).status());

        CompraResponse c2 = listaCompraService.criarCompra(lst.id());
        compraService.atualizarRascunho(c2.id(), new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(linha(kraft, "12", "18.00"))));
        compraService.confirmar(c2.id(), null);
        assertEquals(StatusListaCompra.COMPRADA, listaCompraService.buscar(lst.id()).status());

        compraService.cancelar(c2.id(), new CancelarCompraRequest("Papel veio rasgado e o fornecedor aceitou devolver", true));
        assertEquals(StatusListaCompra.PARCIALMENTE_COMPRADA, listaCompraService.buscar(lst.id()).status());
    }

    @Test
    void cen71_trocaManualLivreSemVoltarParaRascunho() {
        ListaCompraResponse lst = listaCompraService.gerar(lista(insumo("Barbante cru")));
        assertEquals(StatusListaCompra.COMPRADA, listaCompraService.alterarStatus(lst.id(), StatusListaCompra.COMPRADA).status());
        assertThrows(BusinessException.class, () -> listaCompraService.alterarStatus(lst.id(), StatusListaCompra.RASCUNHO));
        // lista Comprada não gera compra nova
        assertThrows(BusinessException.class, () -> listaCompraService.criarCompra(lst.id()));
        listaCompraService.alterarStatus(lst.id(), StatusListaCompra.GERADA);
        assertNotNull(listaCompraService.criarCompra(lst.id()));
    }

    @Test
    void cen72_rascunhoDeListaEditaEGera() {
        Insumo barbante = insumo("Barbante cru");
        Insumo kraft = insumo("Papel Kraft");
        ListaCompraResponse rascunho = listaCompraService.salvarRascunho(new GerarListaCompraRequest(List.of(
                new GerarListaCompraRequest.Linha(barbante.getId(), null, null))));
        assertEquals(StatusListaCompra.RASCUNHO, rascunho.status());
        assertNull(rascunho.geradaEm());
        assertTrue(rascunho.identificador().startsWith("LST-"));
        assertThrows(BusinessException.class, () -> listaCompraService.criarCompra(rascunho.id()));
        // sem quantidade não gera
        assertThrows(BusinessException.class, () -> listaCompraService.gerarRascunho(rascunho.id()));
        // rascunho só vai para Cancelada pela troca manual
        assertThrows(BusinessException.class, () -> listaCompraService.alterarStatus(rascunho.id(), StatusListaCompra.COMPRADA));

        listaCompraService.atualizarRascunho(rascunho.id(), lista(barbante, kraft));
        ListaCompraResponse gerada = listaCompraService.gerarRascunho(rascunho.id());
        assertEquals(StatusListaCompra.GERADA, gerada.status());
        assertNotNull(gerada.geradaEm());
        assertEquals(2, gerada.itens().size());
        assertThrows(BusinessException.class, () -> listaCompraService.atualizarRascunho(rascunho.id(), lista(barbante)));
    }

    @Test
    void cen73_historicoOrdenaPorStatus() {
        ListaCompraResponse a = listaCompraService.gerar(lista(insumo("A")));
        listaCompraService.gerar(lista(insumo("B")));
        listaCompraService.alterarStatus(a.id(), StatusListaCompra.CANCELADA);
        List<StatusListaCompra> porStatus = listaCompraService.historico(PageRequest.of(0, 10, Sort.by("status")))
                .map(ListaCompraResumoResponse::status).getContent();
        assertEquals(List.of(StatusListaCompra.CANCELADA, StatusListaCompra.GERADA), porStatus);
        assertThrows(BusinessException.class,
                () -> listaCompraService.historico(PageRequest.of(0, 10, Sort.by("usuario.email"))));
    }

    // ------------------------------------------------------------------ #597, #593, #591, #585

    private MetodoPagamentoConfiguravel metodo(String nome, TipoMetodoPagamento tipo, Integer maxParcelas) {
        return metodoPagamentoRepository.save(MetodoPagamentoConfiguravel.builder().usuario(usuario).nome(nome)
                .tipo(tipo).maxParcelas(maxParcelas).ativo(true).ordem(1).build());
    }

    @Test
    void cen74_parcelasSoNoCredito() {
        MetodoPagamentoConfiguravel credito = metodo("Cartão Nubank", TipoMetodoPagamento.CARTAO_CREDITO, 6);
        MetodoPagamentoConfiguravel pix = metodo("Pix", TipoMetodoPagamento.PIX, null);
        Insumo fita = insumo("Fita de cetim");
        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, true,
                credito.getId(), null, List.of(linha(fita, "1", "10.00")), null, null, 3)).compra();
        assertEquals(3, c.parcelas());

        assertThrows(BusinessException.class, () -> compraService.atualizarPagamento(c.id(),
                new PagamentoCompraRequest(true, credito.getId(), 7)));
        assertNull(compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, pix.getId(), 3)).parcelas());
        assertEquals(1, compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(true, credito.getId(), null)).parcelas());
        assertNull(compraService.atualizarPagamento(c.id(), new PagamentoCompraRequest(false, null, 2)).parcelas());
    }

    @Test
    void cen75_duplicarSemDescontos() {
        Insumo fita = insumo("Fita de cetim");
        CompraResponse original = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("10"), null, new BigDecimal("30.00"),
                        TipoDesconto.PERCENTUAL, new BigDecimal("10"))),
                TipoDesconto.PERCENTUAL, new BigDecimal("5"))).compra();
        assertValor("25.65", original.total());

        CompraResponse sem = compraService.duplicar(original.id(), false);
        assertValor("30.00", sem.total());
        assertNull(sem.descontoNotaTipo());
        assertValor("0", sem.totalDescontos());
        CompraResponse com = compraService.duplicar(original.id(), true);
        assertValor("25.65", com.total());
    }

    @Test
    void cen76_contagensEFiltrosCombinados() {
        Insumo fita = insumo("Fita de cetim");
        compraService.criarRascunho(new CompraRequest(LocalDate.now(), false, null, false, null, null, List.of(linha(fita, "1", "1.00"))));
        CompraResponse naoPaga = confirmar(cadastro("F1", false, true, true), LocalDate.now(), linha(fita, "1", "2.00"));
        MetodoPagamentoConfiguravel pix = metodo("Pix", TipoMetodoPagamento.PIX, null);
        CompraResponse paga = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, true, pix.getId(), null,
                List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, null, new BigDecimal("3.00"),
                        TipoDesconto.VALOR, new BigDecimal("0.50"))))).compra();
        CompraResponse cancelada = confirmar(cadastro("F2", false, true, true), LocalDate.now(), linha(fita, "1", "4.00"));
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest("Pedido duplicado por engano no fornecedor", true));

        CompraContagensResponse cont = compraService.contagens();
        assertEquals(4, cont.todas());
        assertEquals(1, cont.rascunhos());
        assertEquals(2, cont.confirmadas());
        assertEquals(1, cont.canceladas());

        List<UUID> naoPagasConfirmadas = compraService.listar(List.of(StatusCompra.CONFIRMADA), null, null, null, null, null,
                false, false, PageRequest.of(0, 20)).map(CompraResumoResponse::id).getContent();
        assertEquals(List.of(naoPaga.id()), naoPagasConfirmadas);
        List<UUID> comDesconto = compraService.listar(List.of(), null, null, null, null, null, null, true,
                PageRequest.of(0, 20)).map(CompraResumoResponse::id).getContent();
        assertEquals(List.of(paga.id()), comDesconto);
        assertEquals(3, compraService.listar(List.of(StatusCompra.CONFIRMADA, StatusCompra.CANCELADA), null, null, null,
                null, null, null, false, PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void cen52_variosInsumosSomamComoOuEFiltrosDoHistoricoDoFornecedor() {
        Cliente papelaria = cadastro("Papelaria Central", false, true, true);
        Insumo fita = insumo("Fita de cetim");
        Insumo cola = insumo("Cola Branca 1L");
        Insumo kraft = insumo("Papel Kraft");
        CompraResponse comFita = confirmar(papelaria, LocalDate.now(), linha(fita, "10", "25.65"));
        CompraResponse comCola = confirmar(papelaria, LocalDate.now(), linha(cola, "2", "30.60"));
        confirmar(papelaria, LocalDate.now(), linha(kraft, "5", "12.00"));
        MetodoPagamentoConfiguravel pix = metodo("Pix", TipoMetodoPagamento.PIX, null);
        CompraResponse pagaComDesconto = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, papelaria.getId(),
                true, pix.getId(), null, List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, null,
                        new BigDecimal("3.00"), TipoDesconto.VALOR, new BigDecimal("0.50"))))).compra();

        // GET /compras?insumoId=fita&insumoId=cola → OU entre os insumos
        List<UUID> fitaOuCola = compraService.listarComFiltros(List.of(), List.of(papelaria.getId()), null, null,
                List.of(fita.getId(), cola.getId()), null, null, false, PageRequest.of(0, 20))
                .map(CompraResumoResponse::id).getContent();
        assertEquals(3, fitaOuCola.size());
        assertTrue(fitaOuCola.containsAll(List.of(comFita.id(), comCola.id(), pagaComDesconto.id())));

        // Histórico do fornecedor: Pagamento "Paga", "Com desconto" e vários insumos
        assertEquals(List.of(pagaComDesconto.id()), clienteHistoricoService.registrosComFiltros(papelaria.getId(),
                PapelCadastro.FORNECEDOR, null, null, false, false, null, null, List.of(), List.of(), true, false,
                PageRequest.of(0, 20)).map(r -> r.id()).getContent());
        assertEquals(List.of(pagaComDesconto.id()), clienteHistoricoService.registrosComFiltros(papelaria.getId(),
                PapelCadastro.FORNECEDOR, null, null, false, false, null, null, List.of(), List.of(), null, true,
                PageRequest.of(0, 20)).map(r -> r.id()).getContent());
        assertEquals(2, clienteHistoricoService.registrosComFiltros(papelaria.getId(), PapelCadastro.FORNECEDOR, null, null,
                false, false, null, null, List.of(cola.getId(), kraft.getId()), List.of("COMPRA"), null, false,
                PageRequest.of(0, 20)).getTotalElements());
    }

    // ------------------------------------------------------------------ #587 — gráficos de fornecedor

    @Test
    void cen55_graficosDoFornecedor() {
        Cliente papelaria = cadastro("Papelaria Central", false, true, true);
        Insumo fita = insumo("Fita de cetim");
        Insumo cola = insumo("Cola Branca 1L");
        LocalDate mesPassado = LocalDate.now().minusMonths(1).withDayOfMonth(10);
        confirmar(papelaria, mesPassado, linha(fita, "10", "120.00"));
        confirmar(papelaria, LocalDate.now(), linha(cola, "3", "44.00"), linha(fita, "1", "23.45"));
        CompraResponse cancelada = confirmar(papelaria, LocalDate.now(), linha(cola, "1", "99.00"));
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest("Cobraram em dobro e o pedido foi desfeito", true));

        ClienteGraficosResponse g = clienteHistoricoService.graficos(papelaria.getId(), PapelCadastro.FORNECEDOR, null, null);
        List<BigDecimal> ultimos = g.gastoMensal().subList(g.gastoMensal().size() - 2, g.gastoMensal().size())
                .stream().map(ClienteGraficosResponse.GastoMensal::total).toList();
        assertValor("120.00", ultimos.get(0));
        assertValor("67.45", ultimos.get(1));
        assertEquals("Fita de cetim", g.itensMaisComprados().get(0).nome()); // 143,45
        assertValor("143.45", g.itensMaisComprados().get(0).valor());
        assertEquals("INSUMO", g.itensMaisComprados().get(0).tipo());
        assertValor("44.00", g.itensMaisComprados().get(1).valor());
    }

    // ------------------------------------------------------------------ #583/#616 — inativos nos seletores

    @Test
    void cen68_inativosDepoisDosAtivosSoComIncluirInativos() {
        cadastro("Ana Souza", true, false, false);
        cadastro("Ana Lima", true, false, true);
        cadastro("Ana Fornecedora", false, true, true);
        List<String> seletor = clienteService.listar("Ana", null, PapelCadastro.CLIENTE, true,
                PageRequest.of(0, 20, Sort.by("nome"))).map(ClienteResponse::getNome).getContent();
        assertEquals(List.of("Ana Lima", "Ana Souza"), seletor);
        assertEquals(List.of("Ana Lima"), clienteService.listar("Ana", null, PapelCadastro.CLIENTE,
                PageRequest.of(0, 20)).map(ClienteResponse::getNome).getContent());

        Insumo inativo = insumo("Alfinete");
        inativo.setAtivo(false);
        insumoRepository.save(inativo);
        insumo("Botão");
        List<String> insumos = insumoService.listar(null, true, true, PageRequest.of(0, 20, Sort.by("nome")))
                .map(InsumoResponseDTO::nome).getContent();
        assertEquals(List.of("Botão", "Alfinete"), insumos);
    }

    // ------------------------------------------------------------------ #605 — unidades do sistema

    @Test
    void cen78_unidadesDoSistemaNaoEditamNemExcluem() {
        unidadeMedidaService.seedUnidadesPadrao(usuario);
        var unidades = unidadeMedidaService.listar();
        assertEquals(12, unidades.size()); // 11 do sistema + "Peça" da pessoa
        var kg = unidades.stream().filter(u -> u.sigla().equals("kg")).findFirst().orElseThrow();
        assertTrue(kg.padrao());
        assertFalse(unidades.stream().filter(u -> u.sigla().equals("pc")).findFirst().orElseThrow().padrao());

        BusinessException editar = assertThrows(BusinessException.class, () -> unidadeMedidaService.editar(kg.id(),
                new com.penseprecifique.api.shared.dto.request.unidademedida.UnidadeMedidaRequestDTO("Quilograma", "kg")));
        assertEquals("Unidade do sistema", editar.getTitulo());
        assertThrows(BusinessException.class, () -> unidadeMedidaService.excluir(kg.id()));
        assertEquals("Já existe uma unidade de medida com esta sigla.", assertThrows(BusinessException.class,
                () -> unidadeMedidaService.cadastrar(new com.penseprecifique.api.shared.dto.request.unidademedida
                        .UnidadeMedidaRequestDTO("Quilos", "kg"))).getMessage());
    }
}
