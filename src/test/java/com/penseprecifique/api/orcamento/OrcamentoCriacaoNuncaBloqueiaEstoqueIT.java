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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenProject #246/#245 (RN-NOVA-11, revisada) — POST /orcamentos nunca bloqueia por estoque
 * insuficiente, independente de permitirEstoqueNegativo. Substitui
 * OrcamentoRnNova10BloqueioEstoqueIT (removido): RN-NOVA-10 (bloqueio pré-save quando
 * permitirEstoqueNegativo=false) foi revertida — a validação original do usuário concluiu que a
 * única trava real de negócio é no avanço para FINALIZADO (RN-059, ver
 * {@link OrcamentoRn052EstoqueNegativoIT}), nunca na criação. calcularAvisosEstoque continua
 * informativo pós-criação (avisosEstoque na resposta), agora para qualquer item insuficiente,
 * também independente de permitirEstoqueNegativo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoCriacaoNuncaBloqueiaEstoqueIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-nunca-bloqueia-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente RN-NOVA-11").ativa(true).build());
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
    void permitirEstoqueNegativoFalseEInsuficienteNaoBloqueiaEPersiste() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), false);

        OrcamentoRequest req = requestComItem(produto.getId(), 10);
        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()),
                "orçamento deve ter sido persistido — criação nunca bloqueia por estoque, mesmo com permitirEstoqueNegativo=false");

        List<AvisoEstoqueResponse> avisos = resultado.getAvisosEstoque();
        assertEquals(1, avisos.size());
        assertEquals(produto.getId(), avisos.get(0).getProdutoId());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()),
                "estoque não é baixado na criação — baixa só acontece no avanço EM_PRODUCAO -> FINALIZADO");
    }

    @Test
    void permitirEstoqueNegativoTrueEInsuficienteApenasAvisaEPersiste() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);

        OrcamentoRequest req = requestComItem(produto.getId(), 10);
        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));

        List<AvisoEstoqueResponse> avisos = resultado.getAvisosEstoque();
        assertEquals(1, avisos.size());
        assertEquals(produto.getId(), avisos.get(0).getProdutoId());
        assertEquals(0, new BigDecimal("10").compareTo(avisos.get(0).getQuantidadeNecessaria()));
    }

    @Test
    void estoqueSuficienteNaoAvisa() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("100"), false);

        OrcamentoRequest req = requestComItem(produto.getId(), 10);
        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));
        assertTrue(resultado.getAvisosEstoque() == null || resultado.getAvisosEstoque().isEmpty());
    }

    @Test
    void doisItensMesmoProdutoAcumulaNecessidadeSemBloquear() {
        seedUsuarioECliente();
        // Mesmo critério de acumulação de calcularAvisosEstoque/validarEstoqueParaFinalizar: cada
        // item isolado (4) não excede o estoque (7), mas a soma (8) excede — ainda assim não bloqueia.
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

        OrcamentoDetalheResponse resultado = orcamentoService.criar(req);

        assertEquals(1, orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));
        List<AvisoEstoqueResponse> avisos = resultado.getAvisosEstoque();
        assertEquals(1, avisos.size());
        assertEquals(0, new BigDecimal("8").compareTo(avisos.get(0).getQuantidadeNecessaria()));
    }
}
