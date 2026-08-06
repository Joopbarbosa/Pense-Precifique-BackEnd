package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoContagensResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Frente 4/P-BE-CONSOLIDADO-001 — GET /produtos/contagens. ListaProdutosPage.tsx só mostrava a
 * contagem real da categoria ativa no filtro; as demais mostravam sempre 0 porque o dado não
 * existia na API. Contagem ignora `busca` (badges de categoria são navegação global — decisão do
 * prompt de origem).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoContagensIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-contagens-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(String nome, int numero, TipoProduto tipo, boolean ativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(tipo)
                .tempoProducao(30).ativo(ativo)
                // chk_preco_venda_tipo — CUSTOMIZACAO exige preco_venda preenchido no banco
                .precoVenda(tipo == TipoProduto.CUSTOMIZACAO ? new BigDecimal("10.00") : null)
                .build());
    }

    @Test
    void contagemCorretaComProdutosDasTresCategoriasEInativos() {
        seedUsuario();
        novoProduto("Kit Convite", 1, TipoProduto.PRODUTO, true);
        novoProduto("Bolo Base", 2, TipoProduto.PRODUTO_BASE, true);
        novoProduto("Embalagem Presente", 3, TipoProduto.CUSTOMIZACAO, true);
        novoProduto("Kit Antigo", 4, TipoProduto.PRODUTO, false);

        ProdutoContagensResponse contagens = produtoService.contagens();

        assertEquals(4, contagens.getTotal());
        assertEquals(1, contagens.getInativos());
        assertEquals(2, contagens.getPorTipo().getProduto());
        assertEquals(1, contagens.getPorTipo().getProdutoBase());
        assertEquals(1, contagens.getPorTipo().getCustomizacao());
    }

    @Test
    void contagemZeradaQuandoNaoHaProdutos() {
        seedUsuario();

        ProdutoContagensResponse contagens = produtoService.contagens();

        assertEquals(0, contagens.getTotal());
        assertEquals(0, contagens.getInativos());
        assertEquals(0, contagens.getPorTipo().getProduto());
        assertEquals(0, contagens.getPorTipo().getProdutoBase());
        assertEquals(0, contagens.getPorTipo().getCustomizacao());
    }

    @Test
    void naoInterferePorOutroUsuario() {
        seedUsuario();
        novoProduto("Kit Convite", 1, TipoProduto.PRODUTO, true);

        Usuario outro = usuarioRepository.save(Usuario.builder()
                .email("produto-contagens-outro-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        produtoRepository.save(Produto.builder()
                .usuario(outro).numero(1).nome("Kit Outro Usuário").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(true).build());

        ProdutoContagensResponse contagens = produtoService.contagens();

        assertEquals(1, contagens.getTotal());
    }
}
