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
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private Usuario usuario;

    private void seedUsuario(String prefixo) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
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
                List.of(new VendaCaixaItemRequestDTO(produto.getId(), new BigDecimal("2"))),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"))),
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
                List.of(new VendaCaixaItemRequestDTO(produto.getId(), new BigDecimal("3"))),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("15.00"))),
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
                List.of(new VendaCaixaItemRequestDTO(produto.getId(), new BigDecimal("3"))),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("24.00"))),
                null);

        Object primeiraResposta = vendaCaixaService.registrarVenda(requestSemConfirmar);
        assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, primeiraResposta);

        VendaCaixaRequestDTO requestConfirmando = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(produto.getId(), new BigDecimal("3"))),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("24.00"))),
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
                List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("10.00"))),
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
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), new BigDecimal("2"))),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("40.00"))), null));
        assertEquals(0, new BigDecimal("8").compareTo(produtoRepository.findById(produto.getId()).orElseThrow().getEstoqueAtual()));

        VendaCaixaResponseDTO cancelada = vendaCaixaService.cancelarVenda(
                venda.id(), new CancelarVendaCaixaRequestDTO("Cliente desistiu da compra"));

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
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("5.00"))), null));

        caixaTurnoService.fecharTurno(turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("105.00")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                vendaCaixaService.cancelarVenda(venda.id(), new CancelarVendaCaixaRequestDTO("Tentativa tardia")));
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
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("30.00")),
                        new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("30.00"))),
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
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("60.00"))),
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
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(dinheiroId, new BigDecimal("30.00"))), null));
        vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(
                null, List.of(new VendaCaixaItemRequestDTO(produto.getId(), BigDecimal.ONE)),
                null, null, List.of(new VendaCaixaPagamentoRequestDTO(pixId, new BigDecimal("30.00"))), null));

        CaixaTurnoResponseDTO fechado = caixaTurnoService.fecharTurno(
                turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("130.00")));

        // esperado = 100 (abertura) + 30 (venda em dinheiro) = 130; venda em PIX não soma.
        assertEquals(0, new BigDecimal("130.00").compareTo(fechado.valorFechamentoEsperado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fechado.diferenca()));
    }
}
