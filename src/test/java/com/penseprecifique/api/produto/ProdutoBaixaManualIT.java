package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.BaixaManualProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
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

/**
 * RN-NOVA-5/DT-NOVA-4 (V0.14.0, #534 — réplica de #514 aplicada a Produto) — "Baixa manual"
 * generalizada para "Edição manual": mesmo endpoint, campo {@code tipo} (ENTRADA/SAIDA) decide a
 * direção. Motivo/observação continuam obrigatórios e idênticos para as duas direções.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoBaixaManualIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private static final String OBSERVACAO_VALIDA =
            "Ajuste manual de teste automatizado com mais de trinta caracteres.";

    private void seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-baixa-manual-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private UUID criarProdutoComEstoque(BigDecimal estoqueInicial, boolean permitirEstoqueNegativo) {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Produto " + UUID.randomUUID());
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(30);
        request.setPermitirEstoqueNegativo(permitirEstoqueNegativo);
        ProdutoDetalheResponse criado = produtoService.cadastrar(request);

        var produto = produtoRepository.findById(criado.getId()).orElseThrow();
        produto.setEstoqueAtual(estoqueInicial);
        produtoRepository.save(produto);
        return criado.getId();
    }

    private BaixaManualProdutoRequest montarRequest(TipoMovimentacaoProduto tipo, BigDecimal quantidade, MotivoMovimentacaoProduto motivo) {
        BaixaManualProdutoRequest request = new BaixaManualProdutoRequest();
        request.setTipo(tipo);
        request.setQuantidade(quantidade);
        request.setMotivo(motivo);
        request.setObservacao(OBSERVACAO_VALIDA);
        return request;
    }

    @Test
    void entradaManualAcrescentaEstoqueERegistraMovimentacaoTipoEntrada() {
        seedUsuario();
        UUID produtoId = criarProdutoComEstoque(new BigDecimal("10"), false);

        MovimentacaoProdutoResponse mov = produtoService.baixaManual(produtoId,
                montarRequest(TipoMovimentacaoProduto.ENTRADA, new BigDecimal("5"), MotivoMovimentacaoProduto.CORRECAO));

        assertEquals(TipoMovimentacaoProduto.ENTRADA, mov.getTipo());
        assertEquals(0, new BigDecimal("15").compareTo(produtoRepository.findById(produtoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void saidaManualContinuaSubtraindoEstoqueERegistraMovimentacaoTipoSaida() {
        seedUsuario();
        UUID produtoId = criarProdutoComEstoque(new BigDecimal("10"), false);

        MovimentacaoProdutoResponse mov = produtoService.baixaManual(produtoId,
                montarRequest(TipoMovimentacaoProduto.SAIDA, new BigDecimal("4"), MotivoMovimentacaoProduto.PERDA));

        assertEquals(TipoMovimentacaoProduto.SAIDA, mov.getTipo());
        assertEquals(0, new BigDecimal("6").compareTo(produtoRepository.findById(produtoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void entradaManualNuncaBloqueiaMesmoQuandoPermitirEstoqueNegativoEhFalso() {
        seedUsuario();
        UUID produtoId = criarProdutoComEstoque(new BigDecimal("0"), false);

        // ENTRADA só soma — não há como violar a trava de estoque negativo indo pra cima.
        produtoService.baixaManual(produtoId,
                montarRequest(TipoMovimentacaoProduto.ENTRADA, new BigDecimal("3"), MotivoMovimentacaoProduto.OUTRO));

        assertEquals(0, new BigDecimal("3").compareTo(produtoRepository.findById(produtoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void saidaManualContinuaBloqueadaSePermitirEstoqueNegativoForFalso() {
        seedUsuario();
        UUID produtoId = criarProdutoComEstoque(new BigDecimal("2"), false);

        BaixaManualProdutoRequest request =
                montarRequest(TipoMovimentacaoProduto.SAIDA, new BigDecimal("5"), MotivoMovimentacaoProduto.PERDA);

        assertThrows(BusinessException.class, () -> produtoService.baixaManual(produtoId, request));
        assertEquals(0, new BigDecimal("2").compareTo(produtoRepository.findById(produtoId).orElseThrow().getEstoqueAtual()));
    }
}
