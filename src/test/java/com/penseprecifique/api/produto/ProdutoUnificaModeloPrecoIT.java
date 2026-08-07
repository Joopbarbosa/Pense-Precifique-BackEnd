package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #210+231+234, RN-NOVA-1/RN-038a — CEN-NOVO-1. Produto tipo PRODUTO passa a aceitar precoVenda/
 * margemLucro no cadastro/edição, com o mesmo padrão calculado+override que já existia só para
 * CUSTOMIZACAO: preço sugerido recalcula ao vivo quando o custo muda, mas o preço de venda
 * persistido com override não é sobrescrito sozinho por mudança de custo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoUnificaModeloPrecoIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;

    private Usuario usuario;
    private Insumo insumo;

    private void seedUsuarioEInsumo() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-preco-unificado-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
                .custoUnitario(new BigDecimal("4.00")).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private FichaTecnicaItemRequest itemFichaTecnica(BigDecimal quantidade) {
        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(insumo.getId());
        item.setQuantidade(quantidade);
        return item;
    }

    @Test
    void produtoAceitaMargemLucroNoCadastroECalculaPrecoSugerido() {
        seedUsuarioEInsumo();

        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Bolo Simples");
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(30);
        request.setRendimento(BigDecimal.ONE);
        request.setMargemLucro(new BigDecimal("50"));
        request.setFichaTecnica(List.of(itemFichaTecnica(BigDecimal.ONE)));

        ProdutoDetalheResponse response = produtoService.cadastrar(request);

        // custoUnitario = 1 * 4.00 = 4.00; precoSugerido = 4.00 * 1.5 = 6.00
        assertEquals(0, new BigDecimal("6.00").compareTo(response.getPrecoSugerido()));
        assertEquals(0, new BigDecimal("6.00").compareTo(response.getPrecoVenda()));
        assertFalse(response.isOverride());
    }

    @Test
    void produtoAceitaPrecoVendaManualComoOverrideNoCadastro() {
        seedUsuarioEInsumo();

        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Bolo Premium");
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(30);
        request.setRendimento(BigDecimal.ONE);
        request.setMargemLucro(new BigDecimal("50"));
        request.setPrecoVenda(new BigDecimal("9.99"));
        request.setFichaTecnica(List.of(itemFichaTecnica(BigDecimal.ONE)));

        ProdutoDetalheResponse response = produtoService.cadastrar(request);

        assertEquals(0, new BigDecimal("9.99").compareTo(response.getPrecoVenda()));
        assertTrue(response.isOverride());
    }

    @Test
    void precoVendaComOverrideNaoEhSobrescritoQuandoCustoMudaNaEdicao() {
        seedUsuarioEInsumo();

        ProdutoRequest cadastroRequest = new ProdutoRequest();
        cadastroRequest.setNome("Bolo Premium");
        cadastroRequest.setTipo(TipoProduto.PRODUTO);
        cadastroRequest.setTempoProducao(30);
        cadastroRequest.setRendimento(BigDecimal.ONE);
        cadastroRequest.setMargemLucro(new BigDecimal("50"));
        cadastroRequest.setPrecoVenda(new BigDecimal("9.99"));
        cadastroRequest.setFichaTecnica(List.of(itemFichaTecnica(BigDecimal.ONE)));
        ProdutoDetalheResponse cadastrado = produtoService.cadastrar(cadastroRequest);

        ProdutoRequest edicaoRequest = new ProdutoRequest();
        edicaoRequest.setNome("Bolo Premium");
        edicaoRequest.setTipo(TipoProduto.PRODUTO);
        edicaoRequest.setTempoProducao(30);
        edicaoRequest.setRendimento(BigDecimal.ONE);
        // margemLucro não enviada — preserva os 50% já persistidos; precoVenda também não enviado.
        edicaoRequest.setFichaTecnica(List.of(itemFichaTecnica(new BigDecimal("2")))); // dobra o custo

        ProdutoDetalheResponse editado = produtoService.editar(cadastrado.getId(), edicaoRequest);

        // custoUnitario dobrou pra 8.00, precoSugerido acompanha (8.00 * 1.5 = 12.00)...
        assertEquals(0, new BigDecimal("12.00").compareTo(editado.getPrecoSugerido()));
        // ...mas o preço de venda com override permanece o valor manual original.
        assertEquals(0, new BigDecimal("9.99").compareTo(editado.getPrecoVenda()));
        assertTrue(editado.isOverride());
    }

    @Test
    void precoVendaSemOverrideAcompanhaMudancaDeMargemNaEdicao() {
        seedUsuarioEInsumo();

        ProdutoRequest cadastroRequest = new ProdutoRequest();
        cadastroRequest.setNome("Bolo Simples");
        cadastroRequest.setTipo(TipoProduto.PRODUTO);
        cadastroRequest.setTempoProducao(30);
        cadastroRequest.setRendimento(BigDecimal.ONE);
        cadastroRequest.setMargemLucro(new BigDecimal("50"));
        cadastroRequest.setFichaTecnica(List.of(itemFichaTecnica(BigDecimal.ONE)));
        ProdutoDetalheResponse cadastrado = produtoService.cadastrar(cadastroRequest);
        assertFalse(cadastrado.isOverride());

        ProdutoRequest edicaoRequest = new ProdutoRequest();
        edicaoRequest.setNome("Bolo Simples");
        edicaoRequest.setTipo(TipoProduto.PRODUTO);
        edicaoRequest.setTempoProducao(30);
        edicaoRequest.setRendimento(BigDecimal.ONE);
        edicaoRequest.setMargemLucro(new BigDecimal("100"));
        edicaoRequest.setFichaTecnica(List.of(itemFichaTecnica(BigDecimal.ONE))); // custo inalterado

        ProdutoDetalheResponse editado = produtoService.editar(cadastrado.getId(), edicaoRequest);

        // custoUnitario continua 4.00; precoSugerido = 4.00 * 2.0 = 8.00 — acompanha a nova margem
        assertEquals(0, new BigDecimal("8.00").compareTo(editado.getPrecoVenda()));
        assertFalse(editado.isOverride());
    }
}
