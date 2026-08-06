package com.penseprecifique.api.producao;

import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.FinalizarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.PerdaProducaoRequest;
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

/**
 * #188/RN-NOVA-4 (Opção A) — perda declarada ao finalizar desconta da quantidade que entra em
 * estoque (planejada - perda), não é registro paralelo. Exemplo numérico do prompt: 20 planejado,
 * perda 5 → 15 no estoque final.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoFinalizarComPerdaIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;

    private Usuario usuario;

    private Produto seedProdutoEProducaoEmAndamento(BigDecimal quantidadePlanejada, int numeroProducao) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-perda-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Kit Convite Casamento").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).estoqueAtual(BigDecimal.ZERO).build());

        Producao producao = Producao.builder()
                .usuario(usuario).numero(numeroProducao).estado(EstadoProducao.EM_ANDAMENTO)
                .dataInicio(java.time.LocalDate.now()).dataTerminoPrevista(java.time.LocalDate.now().plusDays(7))
                .build();
        producao = producaoRepository.save(producao);
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(quantidadePlanejada).build());

        this.producaoIdAtual = producao.getId();
        return produto;
    }

    private UUID producaoIdAtual;

    @Test
    void perdaDeclaradaDescontaDoIncrementoDeEstoque() {
        Produto produto = seedProdutoEProducaoEmAndamento(new BigDecimal("20"), 1);

        PerdaProducaoRequest perda = new PerdaProducaoRequest();
        perda.setProdutoId(produto.getId());
        perda.setQuantidadePerdida(new BigDecimal("5"));
        FinalizarProducaoRequest request = new FinalizarProducaoRequest();
        request.setPerdas(List.of(perda));

        producaoService.finalizar(producaoIdAtual, request);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("15").compareTo(atualizado.getEstoqueAtual()),
                "20 planejado - 5 perda = 15 no estoque, não 20");

        ProducaoProduto pp = producaoProdutoRepository.findByProducaoId(producaoIdAtual).get(0);
        assertEquals(0, new BigDecimal("5").compareTo(pp.getQuantidadePerdida()), "perda declarada deve ficar persistida");
    }

    @Test
    void semPerdaDeclaradaIncrementaTotalPlanejado() {
        Produto produto = seedProdutoEProducaoEmAndamento(new BigDecimal("20"), 2);

        producaoService.finalizar(producaoIdAtual, null);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(atualizado.getEstoqueAtual()),
                "sem perda declarada, comportamento anterior preservado: incrementa o total planejado");
    }

    @Test
    void perdaMaiorQuePlanejadoBloqueia() {
        Produto produto = seedProdutoEProducaoEmAndamento(new BigDecimal("20"), 3);

        PerdaProducaoRequest perda = new PerdaProducaoRequest();
        perda.setProdutoId(produto.getId());
        perda.setQuantidadePerdida(new BigDecimal("25"));
        FinalizarProducaoRequest request = new FinalizarProducaoRequest();
        request.setPerdas(List.of(perda));

        assertThrows(BusinessException.class, () -> producaoService.finalizar(producaoIdAtual, request));

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(inalterado.getEstoqueAtual()),
                "bloqueio não deve ter alterado o estoque");
    }
}
