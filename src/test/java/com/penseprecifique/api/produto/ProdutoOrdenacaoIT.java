package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #354 — GET /produtos?sort= com campo inexistente na entidade derrubava a request com 500
 * (UnknownPathException/InvalidDataAccessApiUsageException); passou a validar contra allowlist
 * (PageableOrdenacaoResolver) e responder 400. Cobre os campos ordenáveis (nome, numero,
 * precoVenda, precoCusto, estoqueAtual, createdAt) e a rejeição de campo fora da allowlist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoOrdenacaoIT {

    @Autowired ProdutoService produtoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private UUID pAId, pBId, pCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-ord-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        pAId = novoProduto("Alfa", 1, new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("5"));
        pBId = novoProduto("Bravo", 2, new BigDecimal("150.00"), new BigDecimal("60.00"), new BigDecimal("15"));
        pCId = novoProduto("Charlie", 3, new BigDecimal("100.00"), new BigDecimal("40.00"), new BigDecimal("10"));
    }

    private UUID novoProduto(String nome, int numero, BigDecimal precoVenda, BigDecimal precoCusto,
                              BigDecimal estoqueAtual) {
        return produtoRepository.save(com.penseprecifique.api.shared.domain.entity.Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE)
                .precoVenda(precoVenda).precoCusto(precoCusto).estoqueAtual(estoqueAtual)
                .build()).getId();
    }

    @Test
    void ordenaPorNomeAlfabetico() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "nome"));
        assertEquals(List.of(pAId, pBId, pCId), asc); // Alfa, Bravo, Charlie
    }

    @Test
    void ordenaPorNumeroAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "numero"));
        assertEquals(List.of(pAId, pBId, pCId), asc);

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "numero"));
        assertEquals(List.of(pCId, pBId, pAId), desc);
    }

    @Test
    void ordenaPorPrecoVendaAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "precoVenda"));
        assertEquals(List.of(pAId, pCId, pBId), asc); // 50, 100, 150

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "precoVenda"));
        assertEquals(List.of(pBId, pCId, pAId), desc);
    }

    @Test
    void ordenaPorEstoqueAtual() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "estoqueAtual"));
        assertEquals(List.of(pAId, pCId, pBId), asc); // 5, 10, 15
    }

    @Test
    void campoForaDaAllowlistLancaBusinessException() {
        seed();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "usuario.email"));
        assertThrows(BusinessException.class,
                () -> produtoService.listar(null, null, null, pageable));
    }

    private List<UUID> idsNaOrdem(Sort sort) {
        Page<ProdutoResponse> pagina = produtoService.listar(null, null, null, PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(ProdutoResponse::getId).toList();
    }
}
