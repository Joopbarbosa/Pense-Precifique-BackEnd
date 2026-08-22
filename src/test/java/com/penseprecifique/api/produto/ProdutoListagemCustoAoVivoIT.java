package com.penseprecifique.api.produto;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #135/RN-039 — GET /produtos (listagem) recalcula custoUnitario ao vivo, mesmo cálculo já usado em
 * GET /produtos/{id} (buscarPorId). Antes ficava travado no valor persistido em produto.precoCusto,
 * desatualizando se o preço de um insumo da ficha técnica mudasse depois sem resalvar o produto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoListagemCustoAoVivoIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;

    @Test
    void listagemRecalculaCustoUnitarioSemResalvarProduto() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-custo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
                .custoUnitario(new BigDecimal("2.00")).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).fracionavel(true).build());

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(insumo.getId());
        item.setQuantidade(new BigDecimal("5"));

        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Bolo de Farinha");
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(60);
        request.setRendimento(new BigDecimal("10"));
        request.setFichaTecnica(List.of(item));

        ProdutoDetalheResponse cadastrado = produtoService.cadastrar(request);

        // custoTotalLote = 5 * 2.00 = 10.00 (sem valor-hora configurado, mão de obra = 0); custoUnitario = 10.00/10 = 1.00
        Page<ProdutoResponse> paginaAntes = produtoService.listar(null, null, null, PageRequest.of(0, 10));
        ProdutoResponse respostaAntes = paginaAntes.getContent().stream()
                .filter(p -> p.getId().equals(cadastrado.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1.0000").compareTo(respostaAntes.getCustoUnitario()));

        // Preço do insumo muda depois — produto NÃO é resalvo.
        insumo.setCustoUnitario(new BigDecimal("4.00"));
        insumoRepository.save(insumo);

        Page<ProdutoResponse> paginaDepois = produtoService.listar(null, null, null, PageRequest.of(0, 10));
        ProdutoResponse respostaDepois = paginaDepois.getContent().stream()
                .filter(p -> p.getId().equals(cadastrado.getId())).findFirst().orElseThrow();
        // custoTotalLote = 5 * 4.00 = 20.00; custoUnitario = 20.00/10 = 2.00
        assertEquals(0, new BigDecimal("2.0000").compareTo(respostaDepois.getCustoUnitario()),
                "listagem deve refletir o novo custo do insumo sem precisar resalvar o produto");
    }
}
