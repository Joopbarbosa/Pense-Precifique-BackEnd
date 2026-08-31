package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RECONCILIA-005 (V0.8.1) — dois achados no cancelamento com estorno de sinal:
 * (1) frontend enviava só a data ("2026-08-20"), Jackson rejeita LocalDateTime sem componente de
 * hora, bloqueando 100% do fluxo com 400 antes de chegar no Service;
 * (2) mesmo com o formato certo, OrcamentoService.cancelar() ignorava
 * request.getDataEstornoSinal() e sempre gravava LocalDateTime.now(). Decisão confirmada com o
 * usuário: usar a data escolhida (ver docs-pense-precifique/modulos/ORCAMENTO/decisoes-orcamento.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrcamentoCancelarEstornoDataIT {

    @Autowired MockMvc mockMvc;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private Usuario usuario;
    private Cliente cliente;

    private String seedUsuarioClienteEToken() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-cancel-estorno-data-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Estorno Data").ativa(true).build());
        return jwtTokenProvider.generateToken(usuario);
    }

    private Produto novoProduto(int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("300.00")).build());
    }

    /** RASCUNHO -> ... -> SINAL_PAGO, via chamadas diretas ao Service (sem passar pelo HTTP). */
    private UUID criarOrcamentoAteSinalPago(Produto produto) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("0"));
        item.setPrecoUnitario(new BigDecimal("300.00"));
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(true);
        req.setValorSinal(new BigDecimal("150.00"));

        UUID orcamentoId = orcamentoService.criar(req).getId();

        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        AvancaStatusRequest sinalReq = new AvancaStatusRequest();
        sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // APROVADO -> AGUARDANDO_SINAL
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO

        return orcamentoId;
    }

    @Test
    void payloadComDataCompletaNaoQuebraMais() throws Exception {
        String token = seedUsuarioClienteEToken();
        UUID orcamentoId = criarOrcamentoAteSinalPago(novoProduto(1));

        String body = """
                {
                  "motivoCancelamento": "Cliente desistiu da encomenda",
                  "estornarSinal": true,
                  "dataEstornoSinal": "2026-08-19T12:00:00"
                }
                """;

        mockMvc.perform(post("/orcamentos/" + orcamentoId + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void dataEstornoApenasComDataSemHoraContinuaRejeitadaPeloJackson() throws Exception {
        String token = seedUsuarioClienteEToken();
        UUID orcamentoId = criarOrcamentoAteSinalPago(novoProduto(2));

        String body = """
                {
                  "motivoCancelamento": "Cliente desistiu da encomenda",
                  "estornarSinal": true,
                  "dataEstornoSinal": "2026-08-19"
                }
                """;

        mockMvc.perform(post("/orcamentos/" + orcamentoId + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorPersistidoReflicteDataEscolhidaPeloUsuarioNaoAHoraDoClique() {
        seedUsuarioClienteEToken();
        UUID orcamentoId = criarOrcamentoAteSinalPago(novoProduto(3));

        AvancaStatusRequest cancelReq = new AvancaStatusRequest();
        cancelReq.setMotivoCancelamento("Estorno retroativo combinado com a cliente");
        cancelReq.setEstornarSinal(true);
        cancelReq.setDataEstornoSinal(java.time.LocalDateTime.of(2026, 8, 19, 12, 0, 0));

        var resultado = orcamentoService.cancelar(orcamentoId, cancelReq);

        org.junit.jupiter.api.Assertions.assertEquals(
                java.time.LocalDateTime.of(2026, 8, 19, 12, 0, 0),
                resultado.getDataEstornoSinal());
    }

    @Test
    void estornarSemDataInformadaEhRejeitado() {
        seedUsuarioClienteEToken();
        UUID orcamentoId = criarOrcamentoAteSinalPago(novoProduto(4));

        AvancaStatusRequest cancelReq = new AvancaStatusRequest();
        cancelReq.setMotivoCancelamento("Estorno sem data informada");
        cancelReq.setEstornarSinal(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.penseprecifique.api.shared.exception.BusinessException.class,
                () -> orcamentoService.cancelar(orcamentoId, cancelReq));
    }
}
