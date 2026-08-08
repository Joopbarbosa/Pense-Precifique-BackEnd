package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.produto.ComponenteVinculadoResponse;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
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
 * #237 (correção V0.7) — GET /produtos/{id}/componentes-vinculados. Não existia nenhum
 * endpoint de leitura que expusesse o {@code vinculoId} (id de FichaTecnicaItem) exigido por
 * POST /produtos/{id}/resolver-vinculos (ação SUBSTITUIR, tipo COMPONENTE_FICHA_TECNICA) —
 * o id só era alcançável hoje via consulta direta ao repository (ver ProdutoResolverVinculosIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoComponentesVinculadosIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private int proximoNumero = 1;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-componentes-vinculados-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(String nome, TipoProduto tipo, BigDecimal precoCusto) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumero++).nome(nome).tipo(tipo).tempoProducao(30)
                .ativo(true).precoCusto(precoCusto).precoVenda(new BigDecimal("10.00"))
                .build());
    }

    private FichaTecnicaItem novoComponente(Produto produtoPai, Produto produtoBase, BigDecimal quantidade) {
        return fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoPai).produtoBase(produtoBase).quantidade(quantidade).build());
    }

    @Test
    void componentesVinculadosRetornaVinculoIdEDadosDoProdutoPai() {
        seedUsuario();
        Produto componente = novoProduto("Recheio", TipoProduto.PRODUTO, new BigDecimal("2.0000"));
        Produto produtoPai = novoProduto("Bolo composto", TipoProduto.PRODUTO, BigDecimal.ZERO);
        FichaTecnicaItem vinculo = novoComponente(produtoPai, componente, new BigDecimal("2"));

        List<ComponenteVinculadoResponse> resultado = produtoService.componentesVinculados(componente.getId());

        assertEquals(1, resultado.size());
        ComponenteVinculadoResponse response = resultado.get(0);
        assertEquals(vinculo.getId(), response.getVinculoId());
        assertEquals(produtoPai.getId(), response.getProdutoId());
        assertEquals("Bolo composto", response.getProdutoNome());
        assertTrue(response.getProdutoIdentificador().startsWith("PRO-"));
    }

    @Test
    void componentesVinculadosSemUsoRetornaListaVazia() {
        seedUsuario();
        Produto produto = novoProduto("Sem vínculo", TipoProduto.PRODUTO, new BigDecimal("2.0000"));

        assertTrue(produtoService.componentesVinculados(produto.getId()).isEmpty());
    }

    @Test
    void componentesVinculadosProdutoInexistenteLancaResourceNotFound() {
        seedUsuario();
        assertThrows(ResourceNotFoundException.class, () -> produtoService.componentesVinculados(UUID.randomUUID()));
    }
}
