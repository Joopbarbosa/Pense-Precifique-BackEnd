package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RN-NOVA-8 (V0.18.0, #444) — a entrega estimada que o detalhe do orçamento devolve conta do sinal pago
 * (com sinal) ou da aprovação (sem sinal), em dias corridos (CEN-NOVO-13, 14, 17).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoEntregaEstimadaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente cliente;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-entrega-estimada-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Entrega Estimada").ativa(true).build());
    }

    private UUID criarOrcamento(boolean sinalAtivo, Integer prazoDias) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Entrega").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("50.00")).build());
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(prazoDias != null);
        req.setPrazoProducaoDias(prazoDias);
        req.setItens(List.of(item));
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("30"));
        }
        return orcamentoService.criar(req).getId();
    }

    private void avancar(UUID id, AvancaStatusRequest req) {
        orcamentoService.avancarStatus(id, req);
    }

    /** CEN-NOVO-17 e CEN-NOVO-13 — com sinal ativo, sem entrega estimada até o sinal ser pago; depois conta dele. */
    @Test
    void comSinalAtivoSoTemEntregaDepoisDoSinalPago() {
        seed();
        UUID id = criarOrcamento(true, 10);
        AvancaStatusRequest sinal = new AvancaStatusRequest();
        sinal.setMetodoSinalRecebido(MetodoPagamento.PIX);
        avancar(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        avancar(id, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        avancar(id, sinal); // APROVADO -> AGUARDANDO_SINAL
        assertNull(orcamentoService.buscarPorId(id).getDataEntregaEstimada());

        avancar(id, sinal); // AGUARDANDO_SINAL -> SINAL_PAGO
        assertEquals(LocalDate.now(clock).plusDays(10), orcamentoService.buscarPorId(id).getDataEntregaEstimada());
    }

    /** CEN-NOVO-14 — sem sinal, conta da aprovação. */
    @Test
    void semSinalContaDaAprovacao() {
        seed();
        UUID id = criarOrcamento(false, 10);
        avancar(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        assertNull(orcamentoService.buscarPorId(id).getDataEntregaEstimada());
        avancar(id, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        assertEquals(LocalDate.now(clock).plusDays(10), orcamentoService.buscarPorId(id).getDataEntregaEstimada());
    }

    /** CEN-NOVO-18 — sem prazo de produção, nunca há entrega estimada. */
    @Test
    void semPrazoNaoTemEntregaEstimada() {
        seed();
        UUID id = criarOrcamento(false, null);
        avancar(id, new AvancaStatusRequest());
        avancar(id, new AvancaStatusRequest());
        assertNull(orcamentoService.buscarPorId(id).getDataEntregaEstimada());
    }
}
