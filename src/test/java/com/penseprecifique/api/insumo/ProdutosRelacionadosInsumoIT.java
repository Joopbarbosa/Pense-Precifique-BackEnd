package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.insumo.ProdutoRelacionadoResponse;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V0.17.0 #971 (DT-NOVA-18, CEN-NOVO-69 e 70): produtos que usam o insumo, com quantidade, unidade e o filtro de ativos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutosRelacionadosInsumoIT {

    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;

    private Usuario usuario;
    private int numero = 1;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produtos-relacionados-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto produto(String nome, boolean ativo) {
        return produtoRepository.save(Produto.builder().usuario(usuario).numero(numero++).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(ativo).precoCusto(BigDecimal.ZERO).precoVenda(new BigDecimal("10.00")).build());
    }

    @Test
    void somenteAtivosTiraOsInativosEOsItensTrazemQuantidadeEUnidade() {
        seedUsuario();
        UnidadeMedida metro = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("metro" + UUID.randomUUID()).sigla("m").build());
        Insumo fita = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Fita de cetim " + UUID.randomUUID())
                .unidadeMedida(metro).custoUnitario(new BigDecimal("3.00")).estoqueAtual(BigDecimal.TEN)
                .permitirEstoqueNegativo(true).fracionavel(true).build());
        Produto laco = produto("Laço pequeno", true);
        Produto antigo = produto("Laço antigo", false);
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(laco).insumo(fita).quantidade(new BigDecimal("2")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(antigo).insumo(fita).quantidade(new BigDecimal("3")).build());

        List<ProdutoRelacionadoResponse> todos = insumoService.listarProdutosRelacionados(fita.getId(), false);
        assertEquals(2, todos.size());
        ProdutoRelacionadoResponse doLaco = todos.stream().filter(r -> r.getId().equals(laco.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(doLaco.getQuantidade()));
        assertEquals("m", doLaco.getUnidade());

        List<ProdutoRelacionadoResponse> ativos = insumoService.listarProdutosRelacionados(fita.getId(), true);
        assertEquals(1, ativos.size());
        assertEquals(laco.getId(), ativos.get(0).getId());
    }
}
