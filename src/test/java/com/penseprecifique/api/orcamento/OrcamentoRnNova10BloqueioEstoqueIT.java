package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.AvisoEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
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
 * #218/RN-NOVA-10 — POST /orcamentos passa a bloquear (400) quando algum item tem
 * permitirEstoqueNegativo=false e a quantidade solicitada excede o estoque atual do Produto —
 * defesa em profundidade do bloqueio de tela (RN-NOVA-8), mesmo critério do endpoint de simulação
 * (ver {@link OrcamentoSimularAlertasIT}). Muda o contrato documentado em contrato-orcamento.md
 * (V0.6.1.1): antes deste RN, o endpoint nunca bloqueava por estoque, só retornava
 * avisosEstoque informativo — esse comportamento continua existindo para os casos que não
 * bloqueiam (permitirEstoqueNegativo=true).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoRnNova10BloqueioEstoqueIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-rnnova10-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente RN-NOVA-10").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private OrcamentoRequest requestComItem(UUID produtoId, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produtoId);
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        return req;
    }

    @Test
    void permitirEstoqueNegativoFalseEInsuficienteBloqueiaSemPersistir() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), false);
        long antes = orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId());

        OrcamentoRequest req = requestComItem(produto.getId(), 10);

        BusinessException ex = assertThrows(BusinessException.class, () -> orcamentoService.criar(req));
        assertTrue(ex.getMessage().contains("Kit Convite"), "mensagem deve identificar o produto bloqueado");

        long depois = orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId());
        assertEquals(antes, depois, "nada deve ter sido persistido quando o bloqueio ocorre");

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "estoque não deve ter sido alterado");
    }

    @Test
    void permitirEstoqueNegativoTrueEInsuficienteApenasAvisaEPersiste() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);

        OrcamentoRequest req = requestComItem(produto.getId(), 10);

        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()),
                "orçamento deve ter sido persistido, já que permitirEstoqueNegativo=true não bloqueia");

        List<AvisoEstoqueResponse> avisos = resultado.getAvisosEstoque();
        assertEquals(1, avisos.size());
        assertEquals(produto.getId(), avisos.get(0).getProdutoId());
        assertEquals(0, new BigDecimal("10").compareTo(avisos.get(0).getQuantidadeNecessaria()));
    }

    @Test
    void estoqueSuficienteNaoBloqueiaNemAvisa() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("100"), false);

        OrcamentoRequest req = requestComItem(produto.getId(), 10);

        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));
        assertTrue(resultado.getAvisosEstoque() == null || resultado.getAvisosEstoque().isEmpty());
    }

    @Test
    void doisItensMesmoProdutoAcumulaNecessidadeAntesDeBloquear() {
        seedUsuarioECliente();
        // #218 — mesmo critério de acumulação de calcularAvisosEstoque/validarEstoqueParaFinalizar:
        // cada item isolado (4) não excede o estoque (7), mas a soma (8) excede.
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("7"), false);

        OrcamentoItemRequest item1 = new OrcamentoItemRequest();
        item1.setProdutoId(produto.getId());
        item1.setMargemAplicada(new BigDecimal("50"));
        item1.setPrecoUnitario(new BigDecimal("10.00"));
        item1.setQuantidade(4);

        OrcamentoItemRequest item2 = new OrcamentoItemRequest();
        item2.setProdutoId(produto.getId());
        item2.setMargemAplicada(new BigDecimal("50"));
        item2.setPrecoUnitario(new BigDecimal("10.00"));
        item2.setQuantidade(4);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item1, item2));

        long antes = orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId());
        assertThrows(BusinessException.class, () -> orcamentoService.criar(req));
        assertEquals(antes, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));
    }
}
