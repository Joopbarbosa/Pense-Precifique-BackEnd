package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #487 (V0.12.0) — achado de integração do Frontend do Caixa (UC-NOVO-1): `GET /produtos?busca=`
 * passa a casar também por `codigoBarras` (igualdade exata), além do `nome` (LIKE parcial já
 * existente) — a venda rápida de balcão busca produto "por nome ou código de barras".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoBuscaCodigoBarrasIT {

    @Autowired ProdutoService produtoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private final AtomicInteger contador = new AtomicInteger(1);
    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-busca-ean-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto criarProduto(String nome, String codigoBarras) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(contador.getAndIncrement()).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(1).precoVenda(new BigDecimal("10.00")).codigoBarras(codigoBarras)
                .ativo(true).build());
    }

    @Test
    void buscaPorCodigoBarrasExatoEncontraOProduto() {
        seedUsuario();
        Produto alvo = criarProduto("Caneta Especial", "7891234567895");
        criarProduto("Outro Produto Qualquer", "1111111111111");

        Page<ProdutoResponse> resultado = produtoService.listar(null, "7891234567895", null, null, PageRequest.of(0, 10));

        assertEquals(1, resultado.getTotalElements());
        assertEquals(alvo.getId(), resultado.getContent().get(0).getId());
    }

    @Test
    void buscaPorNomeContinuaFuncionandoComoAntes() {
        seedUsuario();
        criarProduto("Caderno Universitário", "1234567890123");

        Page<ProdutoResponse> resultado = produtoService.listar(null, "caderno", null, null, PageRequest.of(0, 10));

        assertEquals(1, resultado.getTotalElements());
        assertTrue(resultado.getContent().get(0).getNome().contains("Caderno"));
    }

    @Test
    void buscaSemCorrespondenciaRetornaVazio() {
        seedUsuario();
        criarProduto("Produto Sem Relação", "9999999999999");

        Page<ProdutoResponse> resultado = produtoService.listar(null, "0000000000000", null, null, PageRequest.of(0, 10));

        assertEquals(0, resultado.getTotalElements());
    }
}
