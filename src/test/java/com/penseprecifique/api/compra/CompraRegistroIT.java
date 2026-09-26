package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.CaixaTurnoRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.cliente.ClienteHistoricoService;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.cliente.IndicadoresFornecedorResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.FornecedorInsumoResponse;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 — #541 (RN-NOVA-4), #540 (RN-NOVA-6), #550 (RN-NOVA-23), #542 (RN-NOVA-7) e o lado
 * fornecedor de #560. Cenários CEN-NOVO-7, 8, 9, 10, 12, 13.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraRegistroIT {

    @Autowired CompraService compraService;
    @Autowired FornecedorInsumoService fornecedorInsumoService;
    @Autowired ClienteHistoricoService clienteHistoricoService;
    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    @Autowired VendaCaixaRepository vendaCaixaRepository;
    @Autowired CaixaTurnoRepository caixaTurnoRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroCadastro = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome, String estoque, String custo, boolean permiteNegativo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(new BigDecimal(estoque)).custoUnitario(new BigDecimal(custo))
                .permitirEstoqueNegativo(permiteNegativo).build());
    }

    private Cliente cadastro(String nome, boolean cliente, boolean fornecedor) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(cliente).ehFornecedor(fornecedor).ativa(true).build());
    }

    private MetodoPagamentoConfiguravel metodo(TipoMetodoPagamento tipo, boolean ativo) {
        return metodoPagamentoRepository.save(MetodoPagamentoConfiguravel.builder()
                .usuario(usuario).tipo(tipo).ativo(ativo).ordem(1).build());
    }

    static CompraItemRequest linha(Insumo insumo, String qtd, String preco) {
        return new CompraItemRequest(insumo.getId(), null, qtd != null ? new BigDecimal(qtd) : null,
                preco != null ? new BigDecimal(preco) : null);
    }

    static CompraRequest compra(UUID fornecedorId, List<CompraItemRequest> itens) {
        return new CompraRequest(LocalDate.now(), false, fornecedorId, false, null, null, itens);
    }

    private Insumo recarregar(Insumo i) {
        return insumoRepository.findById(i.getId()).orElseThrow();
    }

    @Test
    void cen7_confirmarCompraComDoisInsumos() {
        Insumo papel = insumo("Papel Couché 250g", "0", "0.40", true);
        Insumo cola = insumo("Cola Branca 1L", "0", "12.00", true);

        CompraResponse r = compraService.confirmarNova(compra(null, List.of(
                linha(papel, "500", "250.00"), linha(cola, "3", "45.00")))).compra();

        assertEquals(StatusCompra.CONFIRMADA, r.status());
        assertEquals("COM-1", r.identificador());
        assertEquals(0, new BigDecimal("295.00").compareTo(r.total()));
        assertEquals(0, new BigDecimal("0.50").compareTo(recarregar(papel).getCustoUnitario()));
        assertEquals(0, new BigDecimal("15.00").compareTo(recarregar(cola).getCustoUnitario()));
        assertEquals(0, new BigDecimal("500").compareTo(recarregar(papel).getEstoqueAtual()));
        assertEquals(0, new BigDecimal("3").compareTo(recarregar(cola).getEstoqueAtual()));
        assertEquals(0, new BigDecimal("0.40").compareTo(r.itens().get(0).custoUnitarioAnterior()));
        assertEquals(0, new BigDecimal("0.50").compareTo(r.itens().get(0).custoUnitarioPosterior()));
        assertEquals(0, new BigDecimal("0.50").compareTo(r.itens().get(0).precoUnitarioPago()));

        // CEN-NOVO-13 / #542 — histórico mostra Entrada/Compra com referência COM-N, nunca o UUID
        MovimentacaoInsumoResponseDTO mov = insumoService.listarMovimentacoes(cola.getId(), PageRequest.of(0, 10))
                .getContent().get(0);
        assertEquals(TipoMovimentacaoInsumo.ENTRADA, mov.tipo());
        assertEquals(MotivoMovimentacaoInsumo.COMPRA, mov.motivo());
        assertEquals(ReferenciaMovimentacaoTipo.COMPRA, mov.referenciaTipo());
        assertEquals("COM-1", mov.referencia());
    }

    @Test
    void mediaPonderadaComEstoqueExistenteEValorNaoRedondo() {
        // 3 un. a R$ 12,00 + 7 un. por R$ 91,37 → 12,737 (preço pago 13,0529)
        Insumo cola = insumo("Cola Branca 1L", "3", "12.00", true);
        CompraResponse r = compraService.confirmarNova(compra(null, List.of(linha(cola, "7", "91.37")))).compra();
        assertEquals(0, new BigDecimal("12.7370").compareTo(recarregar(cola).getCustoUnitario()));
        assertEquals(0, new BigDecimal("13.0529").compareTo(r.itens().get(0).precoUnitarioPago()));
    }

    @Test
    void cen8_rascunhoNaoMexeEmEstoqueEExcluidoNaoReaproveitaNumero() {
        Insumo cola = insumo("Cola Branca 1L", "3", "12.00", true);
        CompraResponse rascunho = compraService.criarRascunho(compra(null, List.of(linha(cola, "10", null))));
        assertEquals(StatusCompra.RASCUNHO, rascunho.status());
        assertEquals("COM-1", rascunho.identificador());
        assertEquals(0, new BigDecimal("3").compareTo(recarregar(cola).getEstoqueAtual()));
        assertNull(rascunho.itens().get(0).precoUnitario());

        compraService.excluirRascunho(rascunho.id());
        CompraResponse proxima = compraService.criarRascunho(compra(null, List.of(linha(cola, "1", "12.00"))));
        assertEquals("COM-2", proxima.identificador());
        assertEquals(0, new BigDecimal("12.0000").compareTo(proxima.itens().get(0).precoUnitario()));
    }

    @Test
    void cen9_linhaInvalidaBloqueiaACompraToda() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        Insumo b = insumo("Cola", "0", "12.00", true);
        Insumo c = insumo("Fita", "0", "1.00", true);
        CompraResponse rascunho = compraService.criarRascunho(compra(null, List.of(
                linha(a, "10", "5.00"), linha(b, "2", null), linha(c, "4", "6.00"))));

        BusinessException ex = assertThrows(BusinessException.class, () -> compraService.confirmar(rascunho.id(), null));
        assertTrue(ex.getMessage().contains("Linha 2 (Cola): informe o preço total pago"), ex.getMessage());
        assertEquals(0, BigDecimal.ZERO.compareTo(recarregar(a).getEstoqueAtual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(recarregar(c).getEstoqueAtual()));
        assertEquals(StatusCompra.RASCUNHO, compraService.buscar(rascunho.id()).status());
    }

    @Test
    void confirmarNovaInvalidaNaoCriaNada() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        assertThrows(BusinessException.class, () -> compraService.confirmarNova(compra(null, List.of(linha(a, "10", null)))));
        // a transação é revertida inteira: o próximo número continua COM-1
        assertEquals("COM-1", compraService.criarRascunho(compra(null, List.of(linha(a, "1", "1.00")))).identificador());
    }

    @Test
    void cen10_dataFuturaBloqueada() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        CompraRequest futura = new CompraRequest(LocalDate.now().plusDays(1), false, null, false, null, null,
                List.of(linha(a, "1", "1.00")));
        assertEquals("A data da compra não pode ser futura",
                assertThrows(BusinessException.class, () -> compraService.criarRascunho(futura)).getMessage());
    }

    @Test
    void cen12_vinculoCriadoEPrecoAtualizadoAoConfirmar() {
        Cliente papelaria = cadastro("Papelaria Central", false, true);
        Insumo fita = insumo("Fita de cetim", "0", "1.00", true);

        compraService.confirmarNova(compra(papelaria.getId(), List.of(linha(fita, "10", "15.00"))));
        List<FornecedorInsumoResponse> v1 = fornecedorInsumoService.listar(papelaria.getId(), null);
        assertEquals(1, v1.size());
        assertEquals(0, new BigDecimal("1.50").compareTo(v1.get(0).precoReferencia()));

        compraService.confirmarNova(compra(papelaria.getId(), List.of(linha(fita, "10", "18.00"))));
        List<FornecedorInsumoResponse> v2 = fornecedorInsumoService.listar(null, fita.getId());
        assertEquals(1, v2.size());
        assertEquals(0, new BigDecimal("1.80").compareTo(v2.get(0).precoReferencia()));
    }

    @Test
    void modoFornecedorUnicoAplicaOCabecalhoEmTodasAsLinhas() {
        Cliente papelaria = cadastro("Papelaria Central", false, true);
        Insumo a = insumo("Papel", "0", "0.40", true);
        Insumo b = insumo("Cola", "0", "12.00", true);
        Cliente outro = cadastro("Outro", false, true);
        CompraResponse r = compraService.criarRascunho(new CompraRequest(LocalDate.now(), false, papelaria.getId(),
                false, null, null, List.of(new CompraItemRequest(a.getId(), outro.getId(), BigDecimal.ONE, BigDecimal.ONE),
                linha(b, "1", "1.00"))));
        assertEquals("Papelaria Central", r.itens().get(0).fornecedor().nome());
        assertEquals("Papelaria Central", r.itens().get(1).fornecedor().nome());
    }

    @Test
    void mesmoInsumoComMesmoFornecedorBloqueia_comFornecedoresDiferentesPermite() {
        Insumo cola = insumo("Cola", "0", "12.00", true);
        Cliente f1 = cadastro("F1", false, true);
        Cliente f2 = cadastro("F2", false, true);
        CompraRequest repetida = new CompraRequest(LocalDate.now(), true, null, false, null, null, List.of(
                new CompraItemRequest(cola.getId(), f1.getId(), BigDecimal.ONE, BigDecimal.TEN),
                new CompraItemRequest(cola.getId(), f1.getId(), BigDecimal.ONE, BigDecimal.TEN)));
        assertThrows(BusinessException.class, () -> compraService.criarRascunho(repetida));

        CompraRequest ok = new CompraRequest(LocalDate.now(), true, null, false, null, null, List.of(
                new CompraItemRequest(cola.getId(), f1.getId(), new BigDecimal("2"), new BigDecimal("24.00")),
                new CompraItemRequest(cola.getId(), f2.getId(), new BigDecimal("2"), new BigDecimal("30.00"))));
        compraService.confirmarNova(ok);
        // 0 + 24 → 12,00; depois 2 un. a 12,00 + 2 por 30,00 → 54 / 4 = 13,50
        assertEquals(0, new BigDecimal("13.50").compareTo(recarregar(cola).getCustoUnitario()));
        assertEquals(0, new BigDecimal("4").compareTo(recarregar(cola).getEstoqueAtual()));
    }

    @Test
    void fornecedorSemPapelBloqueia_fornecedorInativadoDepoisNaoImpedeConfirmar() {
        Cliente soCliente = cadastro("Mariana", true, false);
        Insumo a = insumo("Papel", "0", "0.40", true);
        assertEquals("Este cadastro não está ativo como Fornecedor.", assertThrows(BusinessException.class,
                () -> compraService.criarRascunho(compra(soCliente.getId(), List.of(linha(a, "1", "1.00"))))).getMessage());

        Cliente papelaria = cadastro("Papelaria Central", false, true);
        CompraResponse rascunho = compraService.criarRascunho(compra(papelaria.getId(), List.of(linha(a, "1", "1.00"))));
        papelaria.setAtiva(false);
        clienteRepository.save(papelaria);
        assertEquals(StatusCompra.CONFIRMADA, compraService.confirmar(rascunho.id(), null).compra().status());
    }

    @Test
    void insumoInativadoDepoisDoRascunhoBloqueiaConfirmacao() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        CompraResponse rascunho = compraService.criarRascunho(compra(null, List.of(linha(a, "1", "1.00"))));
        insumoService.inativar(a.getId());
        // editar mantendo o insumo já salvo é aceito…
        compraService.atualizarRascunho(rascunho.id(), compra(null, List.of(linha(a, "2", "2.00"))));
        // …mas confirmar, não
        BusinessException ex = assertThrows(BusinessException.class, () -> compraService.confirmar(rascunho.id(), null));
        assertTrue(ex.getMessage().contains("o insumo está inativo"), ex.getMessage());
    }

    @Test
    void rn23_pagoExigeMetodo_naoPagoLimpa_pagamentoEditavelDepoisDeConfirmada() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        MetodoPagamentoConfiguravel pix = metodo(TipoMetodoPagamento.PIX, true);
        MetodoPagamentoConfiguravel inativo = metodo(TipoMetodoPagamento.DINHEIRO, false);

        CompraRequest pagoSemMetodo = new CompraRequest(LocalDate.now(), false, null, true, null, null, List.of(linha(a, "1", "1.00")));
        assertEquals("Escolha como a compra foi paga.",
                assertThrows(BusinessException.class, () -> compraService.criarRascunho(pagoSemMetodo)).getMessage());
        CompraRequest comInativo = new CompraRequest(LocalDate.now(), false, null, true, inativo.getId(), null, List.of(linha(a, "1", "1.00")));
        assertThrows(BusinessException.class, () -> compraService.criarRascunho(comInativo));

        CompraResponse naoPaga = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false,
                pix.getId(), null, List.of(linha(a, "1", "1.00")))).compra();
        assertFalse(naoPaga.pago());
        assertNull(naoPaga.metodoPagamento());

        CompraResponse paga = compraService.atualizarPagamento(naoPaga.id(), new PagamentoCompraRequest(true, pix.getId()));
        assertTrue(paga.pago());
        assertEquals("Pix", paga.metodoPagamento().nome());

        // editar o resto de uma compra confirmada não é permitido
        assertThrows(BusinessException.class, () -> compraService.atualizarRascunho(naoPaga.id(), compra(null, List.of(linha(a, "5", "5.00")))));
    }

    @Test
    void listagemFiltraPorStatusEFornecedorDeQualquerLinha() {
        Insumo a = insumo("Papel", "0", "0.40", true);
        Cliente f1 = cadastro("Papelaria Central", false, true);
        Cliente f2 = cadastro("Atacado Arte", false, true);
        compraService.confirmarNova(compra(f1.getId(), List.of(linha(a, "1", "10.00"))));
        compraService.criarRascunho(new CompraRequest(LocalDate.now(), true, null, false, null, null, List.of(
                new CompraItemRequest(a.getId(), f2.getId(), BigDecimal.ONE, new BigDecimal("7.30")))));
        compraService.criarRascunho(compra(null, List.of(linha(a, "1", "1.00"))));

        PageRequest p = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "numero"));
        assertEquals(3, compraService.listar(null, null, null, null, p).getTotalElements());
        assertEquals(1, compraService.listar(StatusCompra.CONFIRMADA, null, null, null, p).getTotalElements());
        List<CompraResumoResponse> deF2 = compraService.listar(null, f2.getId(), null, null, p).getContent();
        assertEquals(1, deF2.size());
        assertEquals(List.of("Atacado Arte"), deF2.get(0).fornecedores());
        assertEquals(0, new BigDecimal("7.30").compareTo(deF2.get(0).total()));
    }

    @Test
    void cen29_indicadoresEHistoricoDoFornecedor() {
        Cliente papelaria = cadastro("Papelaria Central", true, true);
        Insumo papel = insumo("Papel", "0", "0.40", true);
        Insumo cola = insumo("Cola", "0", "12.00", true);
        MetodoPagamentoConfiguravel pix = metodo(TipoMetodoPagamento.PIX, true);
        compraService.confirmarNova(new CompraRequest(LocalDate.now().minusDays(3), false, papelaria.getId(), true,
                pix.getId(), null, List.of(linha(papel, "100", "50.00"), linha(cola, "1", "12.40"))));
        compraService.confirmarNova(compra(papelaria.getId(), List.of(linha(papel, "20", "17.60"))));
        compraService.criarRascunho(compra(papelaria.getId(), List.of(linha(cola, "9", "99.00"))));

        IndicadoresFornecedorResponse ind = clienteHistoricoService.indicadores(papelaria.getId()).fornecedor();
        assertEquals(2, ind.numeroCompras());
        assertEquals(0, new BigDecimal("80.00").compareTo(ind.totalComprado())); // 62,40 + 17,60
        assertEquals(new BigDecimal("40.00"), ind.compraMedia());
        assertEquals("COM-2", ind.ultimaCompra().identificador());
        assertEquals("Papel", ind.insumoMaisComprado().nome());
        assertEquals(0, new BigDecimal("120").compareTo(ind.insumoMaisComprado().quantidade()));
        assertEquals(2, ind.insumosVinculados());
        assertEquals(1, ind.comprasNaoPagas().quantidade());
        assertEquals(0, new BigDecimal("17.60").compareTo(ind.comprasNaoPagas().valor()));

        // o histórico lista todas, inclusive o rascunho
        assertEquals(3, clienteHistoricoService.historicoCompras(papelaria.getId(), PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void historicoDoInsumoResolveReferenciaCaixa() {
        // Bug corrigido junto com #542: movimentação de insumo com referência CAIXA (V0.13.0/#516)
        // derrubava o histórico com IllegalStateException.
        Insumo fita = insumo("Fita", "10", "1.00", true);
        CaixaTurno turno = caixaTurnoRepository.save(CaixaTurno.builder().usuario(usuario)
                .dataAbertura(LocalDateTime.now()).valorAbertura(BigDecimal.ZERO).status(StatusCaixaTurno.ABERTO).build());
        VendaCaixa venda = vendaCaixaRepository.save(VendaCaixa.builder().usuario(usuario).numero(7)
                .dataVenda(LocalDateTime.now()).caixaTurno(turno).status(StatusVendaCaixa.CONCLUIDA)
                .subtotal(BigDecimal.TEN).total(BigDecimal.TEN).build());
        movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder().insumo(fita)
                .tipo(TipoMovimentacaoInsumo.SAIDA).motivo(MotivoMovimentacaoInsumo.CAIXA)
                .quantidade(BigDecimal.ONE).referenciaId(venda.getId()).referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA)
                .build());

        assertEquals("CX-7", insumoService.listarMovimentacoes(fita.getId(), PageRequest.of(0, 10))
                .getContent().get(0).referencia());
    }
}
