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
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-6 (V0.8.2) — vínculo obrigatório entre Orçamento e Produção antes de EM_PRODUCAO. Cobre
 * os 4 casos do prompt P-B005: bloqueio sem vínculo (Caso 1), avanço liberado com vínculo (Caso 2),
 * N:N com múltiplas produções (Caso 3), mesmo bloqueio no segundo caminho SINAL_PAGO (Caso 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoVincularProducaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-vincular-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Vincular Produção").ativa(true).build());
    }

    private Produto novoProduto() {
        int numero = proximoNumeroProduto++;
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("50.00")).build());
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).build());
    }

    private UUID criarOrcamento(boolean sinalAtivo) {
        Produto produto = novoProduto();
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("50"));
        }
        return orcamentoService.criar(req).getId();
    }

    /** RASCUNHO -> ENVIADO -> APROVADO (sem sinal, deixa em APROVADO pronto pro caminho direto). */
    private void avancarAteAprovadoSemSinal(UUID orcamentoId) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
    }

    /** RASCUNHO -> ... -> SINAL_PAGO (com sinal). */
    private void avancarAteSinalPago(UUID orcamentoId) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        AvancaStatusRequest sinalReq = new AvancaStatusRequest();
        sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // APROVADO -> AGUARDANDO_SINAL
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO
    }

    @Test
    void avancarSemVinculoBloqueiaComBusinessException() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);
        avancarAteAprovadoSemSinal(orcamentoId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().toLowerCase().contains("produção"));

        OrcamentoDetalheResponse orcamento = orcamentoService.buscarPorId(orcamentoId);
        assertEquals(StatusOrcamento.APROVADO, orcamento.getStatus());
    }

    @Test
    void avancarComVinculoPermiteTransicao() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);
        avancarAteAprovadoSemSinal(orcamentoId);

        Producao producao = novaProducao();
        VincularProducaoRequest vincularReq = new VincularProducaoRequest();
        vincularReq.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, vincularReq);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        assertTrue(resultado instanceof OrcamentoDetalheResponse);
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
    }

    @Test
    void multiplasProducoesVinculadasPersistemSemErroDeUnicidade() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);

        Producao producaoA = novaProducao();
        Producao producaoB = novaProducao();

        VincularProducaoRequest reqA = new VincularProducaoRequest();
        reqA.setProducaoId(producaoA.getId());
        orcamentoService.vincularProducao(orcamentoId, reqA);

        VincularProducaoRequest reqB = new VincularProducaoRequest();
        reqB.setProducaoId(producaoB.getId());
        List<OrcamentoProducaoResponse> vinculos = orcamentoService.vincularProducao(orcamentoId, reqB);

        assertEquals(2, vinculos.size());
        assertEquals(2, orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).size());

        avancarAteAprovadoSemSinal(orcamentoId);
        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
    }

    @Test
    void caminhoSinalPagoAplicaMesmoBloqueioSemVinculo() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(true);
        avancarAteSinalPago(orcamentoId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().toLowerCase().contains("produção"));

        OrcamentoDetalheResponse orcamento = orcamentoService.buscarPorId(orcamentoId);
        assertEquals(StatusOrcamento.SINAL_PAGO, orcamento.getStatus());
    }
}
