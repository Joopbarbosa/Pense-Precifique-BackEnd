package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoAvancoStatusResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-B012/RN-NOVA-2 (revisada, V0.8.2) — {@code simularAvancarStatus} reaproveita
 * {@link OrcamentoService#elegivelParaAtalhoAprovacaoDireta} e a validação de estoque de
 * {@link OrcamentoAtalhoAprovacaoDiretaIT} sem chamar {@code baixarEstoque}/{@code save} — todo
 * caso confirma que nada muda no banco após a simulação (status e estoque inalterados). Também
 * cobre {@code ignorarAtalhoAprovacaoDireta} no {@code avancarStatus} real (recusar o atalho).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoSimularAvancoStatusIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-simular-atalho-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Simular Atalho").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private OrcamentoRequest montarRequest(Produto produto, int quantidade, boolean sinalAtivo,
                                            boolean temPrazoProducao, Integer prazoProducaoDias) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(temPrazoProducao);
        req.setPrazoProducaoDias(prazoProducaoDias);
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("30"));
        }
        req.setItens(List.of(item));
        return req;
    }

    private UUID criarEEnviar(OrcamentoRequest req) {
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        return id;
    }

    @Test
    void simulacaoAtalhoLimpoNaoPersisteNada() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Simulacao Limpa", 1, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        SimulacaoAvancoStatusResponse simulacao = orcamentoService.simularAvancarStatus(id, new AvancaStatusRequest());

        assertEquals(StatusOrcamento.ENVIADO, simulacao.getStatusAtual());
        assertTrue(simulacao.isAtalhoAplicavel());
        assertEquals(StatusOrcamento.FINALIZADO, simulacao.getStatusResultante());
        assertTrue(simulacao.getAvisosEstoque().isEmpty());

        OrcamentoDetalheResponse aindaEnviado = orcamentoService.buscarPorId(id);
        assertEquals(StatusOrcamento.ENVIADO, aindaEnviado.getStatus(), "simulação não deve mudar o status persistido");

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(inalterado.getEstoqueAtual()), "simulação não deve baixar estoque");
    }

    @Test
    void simulacaoComEstoqueInsuficientePermitidoDevolveAvisosSemPersistir() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Simulacao Aviso", 2, new BigDecimal("3"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        SimulacaoAvancoStatusResponse simulacao = orcamentoService.simularAvancarStatus(id, new AvancaStatusRequest());

        assertTrue(simulacao.isAtalhoAplicavel());
        assertEquals(StatusOrcamento.FINALIZADO, simulacao.getStatusResultante());
        assertEquals(1, simulacao.getAvisosEstoque().size());
        assertEquals(produto.getId(), simulacao.getAvisosEstoque().get(0).getComponenteId());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "simulação não deve baixar estoque mesmo com aviso");
    }

    @Test
    void simulacaoComSinalAtivoNaoAplicaAtalho() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Simulacao Sinal", 3, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, true, false, null));

        SimulacaoAvancoStatusResponse simulacao = orcamentoService.simularAvancarStatus(id, new AvancaStatusRequest());

        assertFalse(simulacao.isAtalhoAplicavel());
        assertEquals(StatusOrcamento.APROVADO, simulacao.getStatusResultante());
        assertTrue(simulacao.getAvisosEstoque().isEmpty());
    }

    @Test
    void simulacaoComIgnorarAtalhoForcaFluxoNormalMesmoElegivel() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Simulacao Ignorar", 4, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        AvancaStatusRequest requestIgnorar = new AvancaStatusRequest();
        requestIgnorar.setIgnorarAtalhoAprovacaoDireta(true);
        SimulacaoAvancoStatusResponse simulacao = orcamentoService.simularAvancarStatus(id, requestIgnorar);

        assertFalse(simulacao.isAtalhoAplicavel());
        assertEquals(StatusOrcamento.APROVADO, simulacao.getStatusResultante());
    }

    @Test
    void simulacaoEmStatusDiferenteDeEnviadoRejeitaCom400() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Simulacao Status Errado", 5, new BigDecimal("100"), true);
        UUID id = orcamentoService.criar(montarRequest(produto, 10, false, false, null)).getId(); // RASCUNHO

        assertThrows(BusinessException.class,
                () -> orcamentoService.simularAvancarStatus(id, new AvancaStatusRequest()));
    }

    @Test
    void avancarStatusRealComIgnorarAtalhoVaiParaAprovadoMesmoElegivel() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Recusa Atalho", 6, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        AvancaStatusRequest requestIgnorar = new AvancaStatusRequest();
        requestIgnorar.setIgnorarAtalhoAprovacaoDireta(true);
        Object resultado = orcamentoService.avancarStatus(id, requestIgnorar);

        OrcamentoDetalheResponse detalhe = (OrcamentoDetalheResponse) resultado;
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus(), "usuário recusou o atalho — deve seguir fluxo normal");

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(inalterado.getEstoqueAtual()), "recusar o atalho não deve baixar estoque");
    }
}
