package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.caixa.*;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelUpdateRequestDTO;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** #487 — RN-NOVA-1 a 11, CEN-NOVO-1/2/3/4/6/7/8/9. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendaCaixaServiceIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CaixaTurnoService caixaTurnoService;
    @Autowired VendaCaixaService vendaCaixaService;
    @Autowired MetodoPagamentoConfiguravelService metodoPagamentoService;

    private final AtomicInteger contadorProduto = new AtomicInteger(1);
    @Autowired PasswordEncoder passwordEncoder;

    /** #487 (V0.12.0) — cancelar venda exige a senha da usuaria logada. */
    private static final String SENHA_TESTE = "senha-de-teste-123";

    private Usuario usuario;

    private void seedUsuario(String prefixo) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash(passwordEncoder.encode(SENHA_TESTE)).ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metodoPagamentoService.seedMetodosPadrao(usuario);
    }

    private Produto criarProduto(String nome, BigDecimal precoVenda, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(contadorProduto.getAndIncrement()).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(1).rendimento(BigDecimal.ONE).precoVenda(precoVenda)
                .estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo).ativo(true).build());
    }

    private UUID metodoIdPorTipo(TipoMetodoPagamento tipo) {
        return metodoPagamentoService.listar().stream()
                .filter(m -> m.tipo() == tipo).findFirst().orElseThrow().id();
    }

    private CaixaTurnoResponseDTO abrirTurno(BigDecimal valorAbertura) {
        return caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(valorAbertura));
    }

    @Test
    void vendaConcluidaComSucessoBaixaEstoqueEMovimentacao() {
        seedUsuario("venda-ok");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Caixa de Bombom", new BigDecimal("20.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("2"), null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"), null)),
                null);

        Object resultado = vendaCaixaService.registrarVenda(request);
        assertInstanceOf(VendaCaixaResponseDTO.class, resultado);
        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) resultado;

        assertEquals(StatusVendaCaixa.CONCLUIDA, venda.status());
        assertEquals(0, new BigDecimal("40.00").compareTo(venda.total()));
        assertTrue(venda.identificador().startsWith("CX-"));

        Produto produtoAtualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("8").compareTo(produtoAtualizado.getEstoqueAtual()));
    }

    @Test
    void estoqueInsuficienteComBloqueioDuro() {
        seedUsuario("bloqueio-duro");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Caneta Especial", new BigDecimal("5.00"), BigDecimal.ONE, false);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("3"), null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("15.00"), null)),
                null);

        assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
        Produto produtoInalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ONE.compareTo(produtoInalterado.getEstoqueAtual()));
    }

    @Test
    void estoqueInsuficienteComAvisoPermiteContinuarAposConfirmacao() {
        seedUsuario("aviso-negativo");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Tinta Acrílica", new BigDecimal("8.00"), BigDecimal.ONE, true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaRequestDTO requestSemConfirmar = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("3"), null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("24.00"), null)),
                null);

        Object primeiraResposta = vendaCaixaService.registrarVenda(requestSemConfirmar);
        assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, primeiraResposta);

        VendaCaixaRequestDTO requestConfirmando = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("3"), null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("24.00"), null)),
                List.of(produto.getId()));

        Object segundaResposta = vendaCaixaService.registrarVenda(requestConfirmando);
        assertInstanceOf(VendaCaixaResponseDTO.class, segundaResposta);

        Produto produtoAtualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-2").compareTo(produtoAtualizado.getEstoqueAtual()));
    }

    @Test
    void vendaSemTurnoAbertoEBloqueada() {
        seedUsuario("sem-turno-venda");
        Produto produto = criarProduto("Papel A4", new BigDecimal("10.00"), new BigDecimal("50"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("10.00"), null)),
                null);

        BusinessException ex = assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
        assertTrue(ex.getMessage().contains("Abra o caixa"));
    }

    @Test
    void cancelamentoDeVendaReverteEstoque() {
        seedUsuario("cancelar-ok");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Caixa de Bombom", new BigDecimal("20.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("2"), null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"), null)), null));
        assertEquals(0, new BigDecimal("8").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));

        VendaCaixaResponseDTO cancelada = vendaCaixaService.cancelarVenda(
                venda.id(), new CancelarVendaCaixaRequestDTO("Cliente desistiu da compra ainda no balcao", SENHA_TESTE, true));

        assertEquals(StatusVendaCaixa.CANCELADA, cancelada.status());
        assertEquals(0, new BigDecimal("10").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void cancelamentoDeVendaComTurnoJaFechadoEBloqueado() {
        seedUsuario("cancelar-turno-fechado");
        CaixaTurnoResponseDTO turno = abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Caneta", new BigDecimal("5.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("5.00"), null)), null));

        caixaTurnoService.fecharTurno(turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("105.00"), null));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                vendaCaixaService.cancelarVenda(venda.id(), new CancelarVendaCaixaRequestDTO("Tentativa tardia de cancelamento apos o fechamento", SENHA_TESTE, true)));
        assertEquals("O turno em que esta venda ocorreu já foi encerrado.", ex.getMessage());
    }

    @Test
    void pagamentoDivididoComTrocoEmDinheiro() {
        seedUsuario("troco-ok");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Kit Presente", new BigDecimal("50.00"), new BigDecimal("10"), true);
        UUID pixId = metodoIdPorTipo(TipoMetodoPagamento.PIX);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("30.00"), null),
                        new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("30.00"), null)),
                null));

        assertEquals(0, new BigDecimal("50.00").compareTo(venda.total()));
        assertEquals(0, new BigDecimal("10.00").compareTo(venda.troco()));
    }

    @Test
    void pagamentoExcedenteSemNenhumaLinhaEmDinheiroEBloqueado() {
        seedUsuario("troco-sem-dinheiro");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Kit Presente", new BigDecimal("50.00"), new BigDecimal("10"), true);
        UUID pixId = metodoIdPorTipo(TipoMetodoPagamento.PIX);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("60.00"), null)),
                null);

        BusinessException ex = assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
        assertEquals("Não é possível dar troco fora de dinheiro.", ex.getMessage());
    }

    @Test
    void fechamentoDeTurnoSomaVendasEmDinheiroNoValorEsperado() {
        seedUsuario("fechamento-com-venda");
        CaixaTurnoResponseDTO turno = abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Produto Simples", new BigDecimal("30.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);
        UUID pixId = metodoIdPorTipo(TipoMetodoPagamento.PIX);

        // venda em DINHEIRO conta para o esperado; venda em PIX não conta.
        vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("30.00"), null)), null));
        vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("30.00"), null)), null));

        CaixaTurnoResponseDTO fechado = caixaTurnoService.fecharTurno(
                turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("130.00"), null));

        // esperado = 100 (abertura) + 30 (venda em dinheiro) = 130; venda em PIX não soma.
        assertEquals(0, new BigDecimal("130.00").compareTo(fechado.valorFechamentoEsperado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fechado.diferenca()));
    }

    /* ── #487 (V0.12.0) — cancelamento exige senha e escolha de devolução de estoque ──── */

    @Test
    void cancelamentoComSenhaIncorretaEBloqueadoESemMexerNoEstoque() {
        seedUsuario("cancelar-senha-errada");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Caderno", new BigDecimal("20.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("2"), null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"), null)), null));

        BusinessException ex = assertThrows(BusinessException.class, () -> vendaCaixaService.cancelarVenda(
                venda.id(), new CancelarVendaCaixaRequestDTO(
                        "Tentativa de cancelamento sem autorizacao da responsavel", "senha-errada", true)));
        assertEquals("Senha incorreta.", ex.getMessage());

        // Estoque segue baixado e a venda segue concluída — senha errada não pode ter efeito parcial.
        assertEquals(0, new BigDecimal("8").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));
        assertEquals(StatusVendaCaixa.CONCLUIDA, vendaCaixaService.buscarPorId(venda.id()).status());
    }

    @Test
    void cancelamentoSemDevolverEstoqueMantemEstoqueBaixado() {
        seedUsuario("cancelar-sem-estoque");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Vaso de Vidro", new BigDecimal("20.00"), new BigDecimal("10"), true);
        UUID dinheiroId = metodoIdPorTipo(TipoMetodoPagamento.DINHEIRO);

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), new BigDecimal("2"), null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"), null)), null));
        assertEquals(0, new BigDecimal("8").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));

        VendaCaixaResponseDTO cancelada = vendaCaixaService.cancelarVenda(
                venda.id(), new CancelarVendaCaixaRequestDTO(
                        "Produto quebrou no balcao e nao volta para o estoque", SENHA_TESTE, false));

        assertEquals(StatusVendaCaixa.CANCELADA, cancelada.status());
        assertEquals(Boolean.FALSE, cancelada.estoqueRetornado());
        // Estoque permanece em 8: a mercadoria não voltou para a prateleira.
        assertEquals(0, new BigDecimal("8").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void parcelamentoAcimaDoMaximoConfiguradoEBloqueado() {
        seedUsuario("parcela-acima-do-max");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Kit Festa", new BigDecimal("60.00"), new BigDecimal("10"), true);
        UUID creditoId = metodoIdPorTipo(TipoMetodoPagamento.CARTAO_CREDITO);
        metodoPagamentoService.atualizar(creditoId, new MetodoPagamentoConfiguravelUpdateRequestDTO(
                null, new BigDecimal("2.50"), null, 3, true, null));

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(creditoId, new BigDecimal("60.00"), 6)), null);

        BusinessException ex = assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
        assertEquals("O máximo configurado é 3 parcelas.", ex.getMessage());
    }

    @Test
    void parcelamentoDentroDoMaximoCongelaATaxaVigente() {
        seedUsuario("parcela-ok");
        abrirTurno(new BigDecimal("100.00"));
        Produto produto = criarProduto("Kit Presente", new BigDecimal("60.00"), new BigDecimal("10"), true);
        UUID creditoId = metodoIdPorTipo(TipoMetodoPagamento.CARTAO_CREDITO);
        metodoPagamentoService.atualizar(creditoId, new MetodoPagamentoConfiguravelUpdateRequestDTO(
                null, new BigDecimal("2.50"), null, 3, true, null));

        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(null, produto.getId(), BigDecimal.ONE, null)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(creditoId, new BigDecimal("60.00"), 3)), null));

        assertEquals(3, venda.pagamentos().get(0).parcelas());
        assertEquals(0, new BigDecimal("2.50").compareTo(venda.pagamentos().get(0).taxaPercentualAplicada()));
    }
}
