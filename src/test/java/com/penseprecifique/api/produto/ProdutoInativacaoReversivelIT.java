package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frente 5/P-BE-CONSOLIDADO-001 (Opção A, decisão de 2026-07-29) — inativação reversível de Produto,
 * distinta do soft-delete (excluir/DELETE) que já existia. POST /produtos/{id}/inativar e
 * POST /produtos/{id}/reativar são o mecanismo novo (ativo=true/false); DELETE /produtos/{id}
 * continua sendo remoção lógica total (deletedAt), comportamento inalterado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoInativacaoReversivelIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-inativacao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(String nome, int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(true)
                // chk_preco_venda_tipo — preco_venda obrigatório pra todo tipo desde #210+231+234
                .precoVenda(new BigDecimal("10.00"))
                .build());
    }

    @Test
    void inativarSetaAtivoFalseSemExcluir() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1);

        produtoService.inativar(produto.getId());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertFalse(atualizado.getAtivo());
        assertTrue(atualizado.getDeletedAt() == null, "inativar não deve tocar deletedAt");
    }

    @Test
    void reativarVoltaAtivoParaTrue() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1);
        produtoService.inativar(produto.getId());

        produtoService.reativar(produto.getId());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertTrue(atualizado.getAtivo());
    }

    @Test
    void inativarEReativarSaoIdempotentes() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1);

        produtoService.inativar(produto.getId());
        produtoService.inativar(produto.getId());
        Produto inativo = produtoRepository.findById(produto.getId()).orElseThrow();
        assertFalse(inativo.getAtivo());

        produtoService.reativar(produto.getId());
        produtoService.reativar(produto.getId());
        Produto ativo = produtoRepository.findById(produto.getId()).orElseThrow();
        assertTrue(ativo.getAtivo());
    }

    @Test
    void excluirContinuaFazendoSoftDeleteTotalDistintoDeInativar() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1);

        produtoService.excluir(produto.getId());

        Produto excluido = produtoRepository.findById(produto.getId()).orElseThrow();
        assertTrue(excluido.getDeletedAt() != null, "excluir deve setar deletedAt (remoção lógica total)");
        assertTrue(excluido.getAtivo(), "excluir não deve mexer em ativo — são mecanismos distintos");

        assertThrows(ResourceNotFoundException.class, () -> produtoService.inativar(produto.getId()),
                "produto excluído não é encontrado pelo fluxo de inativação reversível");
    }
}
