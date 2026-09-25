package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-10 (V0.10.0, #466, altera ORC-005) — troca FINALIZADO→ENTREGUE→PAGO por
 * FINALIZADO→PAGO→ENTREGUE. DT-NOVA-4 — dataPagamento persistido uma única vez na transição pra
 * PAGO, nunca sobrescrito, ReciboPagamento continua sendo gerado no mesmo gatilho (só o momento no
 * ciclo de vida mudou).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoStatusPagoEntregueIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired ReciboPagamentoRepository reciboPagamentoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-status-pago-entregue-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Status Pago/Entregue").ativa(true).build());
    }

    /** Estoque bem acima do necessário — isola o teste de qualquer aviso/bloqueio de estoque. */
    private Produto novoProduto() {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Status").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Insumo Status").marca("X").unidadeMedida(unidadeMedida("g"))
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    /** RASCUNHO -> ENVIADO -> APROVADO -> EM_PRODUCAO -> FINALIZADO, sem avisos (estoque folgado). */
    private UUID criarOrcamentoAteFinalizado(Produto produto) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(5);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        // temPrazoProducao=true desabilita o atalho de aprovação direta (RN-NOVA-2) — precisa
        // passar literalmente por EM_PRODUCAO -> FINALIZADO -> PAGO -> ENTREGUE.
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));

        UUID orcamentoId = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // APROVADO -> EM_PRODUCAO
        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // EM_PRODUCAO -> FINALIZADO
        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado,
                "estoque folgado não deveria gerar aviso/bloqueio");
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
        return orcamentoId;
    }

    @Test
    void finalizadoParaPagoGeraReciboESetaDataPagamento() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoAteFinalizado(produto);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.PAGO, detalhe.getStatus(), "RN-NOVA-10 — PAGO vem logo após FINALIZADO");

        Orcamento persistido = orcamentoRepository.findById(orcamentoId).orElseThrow();
        assertNotNull(persistido.getDataPagamento(), "DT-NOVA-4 — setado na transição pra PAGO");
        assertNotNull(detalhe.getDataPagamento(),
                "achado do teste manual — dataPagamento persistido mas nunca chegava ao DTO de resposta");
        assertTrue(reciboPagamentoRepository.findByOrcamentoId(orcamentoId).isPresent(),
                "gatilho de geração do Recibo de Pagamento continua o mesmo, só antecipado");
    }

    @Test
    void pagoParaEntregueMantemDataPagamentoESemSegundoRecibo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoAteFinalizado(produto);
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // FINALIZADO -> PAGO

        Orcamento apoisPago = orcamentoRepository.findById(orcamentoId).orElseThrow();
        LocalDateTime dataPagamentoOriginal = apoisPago.getDataPagamento();
        assertNotNull(dataPagamentoOriginal);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // PAGO -> ENTREGUE
        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.ENTREGUE, detalhe.getStatus(), "RN-NOVA-10 — ENTREGUE vem depois de PAGO");

        Orcamento apoisEntregue = orcamentoRepository.findById(orcamentoId).orElseThrow();
        assertEquals(dataPagamentoOriginal, apoisEntregue.getDataPagamento(),
                "DT-NOVA-4 — nunca sobrescrito depois de setado uma vez");
        assertEquals(dataPagamentoOriginal, detalhe.getDataPagamento(),
                "achado do teste manual — DTO precisa continuar expondo dataPagamento em ENTREGUE também");
        assertTrue(reciboPagamentoRepository.findByOrcamentoId(orcamentoId).isPresent(),
                "recibo continua existindo, gerado só uma vez na transição pra PAGO");
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
