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
 * #459 (V0.10.0, parent #336) — mesma classe de bug do #336 original, achada em
 * {@code ListaProdutosPage.tsx} durante a implementação do frontend de #336: a categoria
 * "Inativos" não tinha nenhum filtro real aplicado (nem client-side, nem server-side) — a aba
 * mostrava exatamente a mesma lista que "Todos". Fix: parâmetro {@code ativo} novo em
 * {@code GET /produtos}, mesmo padrão já usado para {@code tipo}/{@code semCatalogo}
 * ({@code ProdutoRepository.buscar}/{@code buscarComBusca}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoFiltroAtivoIT {

    @Autowired ProdutoService produtoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private final AtomicInteger contador = new AtomicInteger(1);

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-filtro-ativo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto criar(String nome, boolean ativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(contador.getAndIncrement()).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00"))
                .ativo(ativo).build());
    }

    @Test
    void filtroAtivoFalseEncontraInativoForaDaPrimeiraPagina() {
        seedUsuario();
        // Ordenação padrão é por nome — nomeando o inativo para cair depois de 5 ativos numa
        // página de tamanho 5, ele nunca apareceria na página 0 sem filtro server-side.
        for (int i = 0; i < 5; i++) {
            criar("A Ativo " + i, true);
        }
        Produto inativo = criar("Z Inativo", false);

        Page<ProdutoResponse> semFiltro = produtoService.listar(null, null, null, null, PageRequest.of(0, 5));
        assertTrue(semFiltro.getContent().stream().noneMatch(p -> p.getId().equals(inativo.getId())));

        Page<ProdutoResponse> filtrado = produtoService.listar(null, null, null, false, PageRequest.of(0, 5));
        assertEquals(1, filtrado.getTotalElements());
        assertEquals(inativo.getId(), filtrado.getContent().get(0).getId());
    }

    @Test
    void filtroAtivoTrueExcluiInativos() {
        seedUsuario();
        criar("Ativo 1", true);
        criar("Ativo 2", true);
        criar("Inativo 1", false);

        Page<ProdutoResponse> filtrado = produtoService.listar(null, null, null, true, PageRequest.of(0, 20));

        assertEquals(2, filtrado.getTotalElements());
        assertTrue(filtrado.getContent().stream().allMatch(ProdutoResponse::isAtivo));
    }

    @Test
    void filtroAtivoCombinaComBusca() {
        seedUsuario();
        criar("Caneca Grande", true);
        criar("Caneca Pequena", false);
        criar("Prato", true);

        Page<ProdutoResponse> filtrado = produtoService.listar(null, "Caneca", null, false, PageRequest.of(0, 20));

        assertEquals(1, filtrado.getTotalElements());
        assertEquals("Caneca Pequena", filtrado.getContent().get(0).getNome());
    }
}
