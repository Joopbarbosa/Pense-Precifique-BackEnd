package com.penseprecifique.api.producao;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #153/RN-NOVA-7 — POST /producoes/simular-alertas: recalcula o consumo acumulado de cada insumo
 * (produtos já na lista + o novo) a cada adição, reaproveitando validarEResolverProdutos()+calcularAlertas().
 * Exemplo numérico do prompt: insumo "Papel" estoque 5, produto A consome 3, produto B consome mais 3
 * → acumulado 6 > 5 → BLOQUEIO (permitirEstoqueNegativo=false) ou AVISO (=true).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoSimularAlertasIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private UUID produtoAId;
    private UUID produtoBId;

    private void seed(boolean permitirEstoqueNegativo) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-sim-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo papel = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Papel").marca("X").unidadeMedida("un")
                .estoqueAtual(new BigDecimal("5")).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .fracionavel(true).build());

        Produto produtoA = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto A").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        Produto produtoB = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Produto B").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoA).insumo(papel).quantidade(new BigDecimal("3")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoB).insumo(papel).quantidade(new BigDecimal("3")).build());

        produtoAId = produtoA.getId();
        produtoBId = produtoB.getId();
    }

    private ProducaoProdutoRequest itemDe(UUID produtoId, String quantidade) {
        ProducaoProdutoRequest r = new ProducaoProdutoRequest();
        r.setProdutoId(produtoId);
        r.setQuantidade(new BigDecimal(quantidade));
        return r;
    }

    @Test
    void bloqueioQuandoConsumoAcumuladoUltrapassaEstoqueSemPermitirNegativo() {
        seed(false);
        List<AlertaInsumoResponse> alertas = producaoService.simularAlertas(
                List.of(itemDe(produtoAId, "1"), itemDe(produtoBId, "1")));

        AlertaInsumoResponse papel = alertas.stream()
                .filter(a -> a.getNomeInsumo().equals("Papel")).findFirst()
                .orElseThrow(() -> new AssertionError("Alerta de Papel não retornado"));
        assertEquals(SituacaoAlertaInsumo.BLOQUEIO_FUTURO, papel.getSituacao());
        assertEquals(0, new BigDecimal("6").compareTo(papel.getQuantidadeNecessaria()),
                "consumo acumulado dos dois produtos deve somar 6, não checar isolado");
    }

    @Test
    void avisoQuandoConsumoAcumuladoUltrapassaEstoqueComPermitirNegativo() {
        seed(true);
        List<AlertaInsumoResponse> alertas = producaoService.simularAlertas(
                List.of(itemDe(produtoAId, "1"), itemDe(produtoBId, "1")));

        AlertaInsumoResponse papel = alertas.stream()
                .filter(a -> a.getNomeInsumo().equals("Papel")).findFirst()
                .orElseThrow(() -> new AssertionError("Alerta de Papel não retornado"));
        assertEquals(SituacaoAlertaInsumo.AVISO, papel.getSituacao());
    }

    @Test
    void consumoAcumuladoDeDoisProdutosSomaNaoChecaIsolado() {
        seed(true);
        // isolado, cada produto sozinho consome só 3 de 5 em estoque — sem estouro
        List<AlertaInsumoResponse> soProdutoA = producaoService.simularAlertas(List.of(itemDe(produtoAId, "1")));
        AlertaInsumoResponse papelIsolado = soProdutoA.stream()
                .filter(a -> a.getNomeInsumo().equals("Papel")).findFirst().orElseThrow();
        assertEquals(SituacaoAlertaInsumo.SUFICIENTE, papelIsolado.getSituacao());

        // acumulado dos dois juntos estoura (6 > 5) — prova que soma em vez de checar cada um isolado
        List<AlertaInsumoResponse> ambos = producaoService.simularAlertas(
                List.of(itemDe(produtoAId, "1"), itemDe(produtoBId, "1")));
        AlertaInsumoResponse papelAcumulado = ambos.stream()
                .filter(a -> a.getNomeInsumo().equals("Papel")).findFirst().orElseThrow();
        assertTrue(papelAcumulado.getSituacao() != SituacaoAlertaInsumo.SUFICIENTE,
                "consumo acumulado de dois produtos deve estourar o estoque, mesmo cada um isolado sendo suficiente");
    }
}
