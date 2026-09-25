package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.produto.FichaTecnicaItemResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.insumo.InsumoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #462 (achado do teste manual, V0.10.0) — sem {@code tipoProdutoBase} no contrato, o Frontend não
 * tinha como distinguir Produto de Customização ao recarregar uma ficha técnica já salva (edição),
 * e rotulava todo componente com {@code produtoBaseId} como "produto" genérico — inclusive quando
 * era, na verdade, uma Customização (regressão visual: badge/categoria erradas na Edição, embora o
 * dado salvo em si estivesse correto).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoFichaTecnicaTipoProdutoBaseIT {

    @Autowired ProdutoService produtoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-ficha-tipo-base-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void fichaTecnicaExpoeTipoProdutoBaseDistinguindoProdutoDeCustomizacao() {
        seedUsuario();
        Produto customizacao = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Fitilho").tipo(TipoProduto.CUSTOMIZACAO)
                .tempoProducao(5).ativo(true).precoCusto(new BigDecimal("1.50"))
                .precoVenda(new BigDecimal("3.00")).build());
        Produto componenteProduto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Recheio").tipo(TipoProduto.PRODUTO)
                .tempoProducao(10).ativo(true).precoCusto(new BigDecimal("2.00"))
                .precoVenda(new BigDecimal("5.00")).build());
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").unidadeMedida(unidadeMedida("kg")).fracionavel(true)
                .estoqueMinimo(BigDecimal.ONE).custoUnitario(new BigDecimal("4.00"))
                .estoqueAtual(BigDecimal.TEN).ativo(true).build());

        Produto produtoPai = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(3).nome("Bolo decorado").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).ativo(true).precoCusto(BigDecimal.ZERO)
                .precoVenda(new BigDecimal("20.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoPai).produtoBase(customizacao).quantidade(new BigDecimal("1")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoPai).produtoBase(componenteProduto).quantidade(new BigDecimal("1")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoPai).insumo(insumo).quantidade(new BigDecimal("2")).build());

        ProdutoDetalheResponse resposta = produtoService.buscarPorId(produtoPai.getId());

        FichaTecnicaItemResponse itemCustomizacao = resposta.getFichaTecnica().stream()
                .filter(i -> customizacao.getId().equals(i.getProdutoBaseId())).findFirst().orElseThrow();
        assertEquals(TipoProduto.CUSTOMIZACAO, itemCustomizacao.getTipoProdutoBase());

        FichaTecnicaItemResponse itemProduto = resposta.getFichaTecnica().stream()
                .filter(i -> componenteProduto.getId().equals(i.getProdutoBaseId())).findFirst().orElseThrow();
        assertEquals(TipoProduto.PRODUTO, itemProduto.getTipoProdutoBase());

        FichaTecnicaItemResponse itemInsumo = resposta.getFichaTecnica().stream()
                .filter(i -> insumo.getId().equals(i.getInsumoId())).findFirst().orElseThrow();
        assertNull(itemInsumo.getTipoProdutoBase());
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
