package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RN-NOVA-1 (V0.8.2) — mini-estorno: quando o sinal já pago excede o valor bruto da multa, a
 * diferença é devolvida ao cliente (multa cobrada = R$ 0,00 + valorDevolvidoMulta preenchido).
 * Casos de sinal <= multa e sem sinal pago continuam com o comportamento anterior (piso zero,
 * sem devolução) — já cobertos por {@link OrcamentoCancelarMultaDescontoSinalIT}, reconfirmados
 * aqui com os exemplos numéricos do prompt P-B002.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoMiniEstornoMultaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-mini-estorno-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Mini-Estorno").ativa(true).build());
    }

    private Produto novoProduto(int numero, BigDecimal precoVenda) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(precoVenda).build());
    }

    /** Orçamento de total exato = precoVenda (1 item, quantidade 1), com ou sem sinal informado diretamente. */
    private UUID criarOrcamento(Produto produto, BigDecimal precoVenda, boolean sinalAtivo, BigDecimal valorSinal) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("0"));
        item.setPrecoUnitario(precoVenda);
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setValorSinal(valorSinal);
        }

        return orcamentoService.criar(req).getId();
    }

    /** RN-NOVA-6 — vincula uma produção nova ao orçamento (pré-requisito de avancarStatus() -> EM_PRODUCAO). */
    private void vincularNovaProducao(UUID orcamentoId) {
        Producao producao = producaoRepository.save(Producao.builder().usuario(usuario).numero(1).build());
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** RASCUNHO -> ... -> EM_PRODUCAO, passando por SINAL_PAGO quando sinalAtivo=true. */
    private void avancarAteEmProducao(UUID orcamentoId, boolean sinalAtivo) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        if (sinalAtivo) {
            AvancaStatusRequest sinalReq = new AvancaStatusRequest();
            sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
            orcamentoService.avancarStatus(orcamentoId, sinalReq); // APROVADO -> AGUARDANDO_SINAL
            orcamentoService.avancarStatus(orcamentoId, sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO
        }
        vincularNovaProducao(orcamentoId); // RN-NOVA-6 — pré-requisito da transição para EM_PRODUCAO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // (SINAL_PAGO|APROVADO) -> EM_PRODUCAO
    }

    private OrcamentoDetalheResponse cancelarComMulta(UUID orcamentoId, String percentual) {
        AvancaStatusRequest cancelReq = new AvancaStatusRequest();
        cancelReq.setPercentualMulta(new BigDecimal(percentual));
        cancelReq.setMotivoCancelamento("Cancelado a pedido da cliente");
        return orcamentoService.cancelar(orcamentoId, cancelReq);
    }

    @Test
    void sinalMaiorQueMultaGeraMiniEstorno() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("800.00"));
        UUID orcamentoId = criarOrcamento(produto, new BigDecimal("800.00"), true, new BigDecimal("300.00"));
        avancarAteEmProducao(orcamentoId, true);

        OrcamentoDetalheResponse resultado = cancelarComMulta(orcamentoId, "30"); // multa bruta = 240,00

        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getValorMulta()));
        assertEquals(0, new BigDecimal("60.00").compareTo(resultado.getValorDevolvidoMulta()));
    }

    @Test
    void sinalIgualMultaSemDevolucao() {
        seedUsuarioECliente();
        Produto produto = novoProduto(2, new BigDecimal("500.00"));
        UUID orcamentoId = criarOrcamento(produto, new BigDecimal("500.00"), true, new BigDecimal("250.00"));
        avancarAteEmProducao(orcamentoId, true);

        OrcamentoDetalheResponse resultado = cancelarComMulta(orcamentoId, "50"); // multa bruta = 250,00

        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getValorMulta()));
        assertNull(resultado.getValorDevolvidoMulta());
    }

    @Test
    void sinalMenorQueMultaComportamentoInalterado() {
        seedUsuarioECliente();
        Produto produto = novoProduto(3, new BigDecimal("1000.00"));
        UUID orcamentoId = criarOrcamento(produto, new BigDecimal("1000.00"), true, new BigDecimal("150.00"));
        avancarAteEmProducao(orcamentoId, true);

        OrcamentoDetalheResponse resultado = cancelarComMulta(orcamentoId, "40"); // multa bruta = 400,00

        assertEquals(0, new BigDecimal("250.00").compareTo(resultado.getValorMulta()));
        assertNull(resultado.getValorDevolvidoMulta());
    }

    @Test
    void semSinalPagoComportamentoInalterado() {
        seedUsuarioECliente();
        Produto produto = novoProduto(4, new BigDecimal("300.00"));
        UUID orcamentoId = criarOrcamento(produto, new BigDecimal("300.00"), false, null);
        avancarAteEmProducao(orcamentoId, false); // APROVADO -> EM_PRODUCAO direto, nunca passa por SINAL_PAGO

        OrcamentoDetalheResponse resultado = cancelarComMulta(orcamentoId, "100"); // multa bruta = 300,00

        assertEquals(0, new BigDecimal("300.00").compareTo(resultado.getValorMulta()));
        assertNull(resultado.getValorDevolvidoMulta());
    }
}
