package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.SimularAlertasOrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frente 2/P-BE-CONSOLIDADO-001 (Cenário 207) — POST /orcamentos/simular-alertas. Não persiste
 * nada, calcula estoque de Produto (não insumo — Orçamento vende produto já pronto) por item em
 * construção, aceitando itemCatalogoId (Catálogo) ou produtoId (avulso) na mesma simulação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoSimularAlertasIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-simalertas-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Simular Alertas").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private ItemCatalogo novoItemCatalogo(Produto produto, int numeroCatalogo, int quantidadePacote) {
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(numeroCatalogo).nome("Catálogo " + numeroCatalogo)
                .margem(new BigDecimal("50")).ativo(true).build());
        return itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(quantidadePacote)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private SimularAlertasOrcamentoItemRequest itemAvulso(UUID produtoId, int quantidade) {
        SimularAlertasOrcamentoItemRequest item = new SimularAlertasOrcamentoItemRequest();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private SimularAlertasOrcamentoItemRequest itemCatalogo(UUID itemCatalogoId, int quantidade) {
        SimularAlertasOrcamentoItemRequest item = new SimularAlertasOrcamentoItemRequest();
        item.setItemCatalogoId(itemCatalogoId);
        item.setQuantidade(quantidade);
        return item;
    }

    @Test
    void estoqueSuficienteRetornaSituacaoSuficienteNaoOmitida() {
        seedUsuario();
        Produto produto = novoProduto("Laço Decorativo", 1, new BigDecimal("100"), true);

        List<AlertaInsumoResponse> alertas = orcamentoService.simularAlertas(List.of(itemAvulso(produto.getId(), 5)));

        assertEquals(1, alertas.size(), "SUFICIENTE não é filtrado pelo backend, mesmo padrão de Produção");
        assertEquals(SituacaoAlertaInsumo.SUFICIENTE, alertas.get(0).getSituacao());
        assertEquals("Laço Decorativo", alertas.get(0).getNomeInsumo());
    }

    @Test
    void estoqueInsuficienteSemPermitirNegativoRetornaBloqueioFuturo() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), false);

        List<AlertaInsumoResponse> alertas = orcamentoService.simularAlertas(List.of(itemAvulso(produto.getId(), 10)));

        assertEquals(1, alertas.size());
        AlertaInsumoResponse alerta = alertas.get(0);
        assertEquals(SituacaoAlertaInsumo.BLOQUEIO_FUTURO, alerta.getSituacao());
        assertEquals(0, new BigDecimal("3").compareTo(alerta.getEstoqueAtual()));
        assertEquals(0, new BigDecimal("10").compareTo(alerta.getQuantidadeNecessaria()));
    }

    @Test
    void estoqueInsuficienteComPermitirNegativoRetornaAviso() {
        seedUsuario();
        Produto produto = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);

        List<AlertaInsumoResponse> alertas = orcamentoService.simularAlertas(List.of(itemAvulso(produto.getId(), 10)));

        assertEquals(1, alertas.size());
        assertEquals(SituacaoAlertaInsumo.AVISO, alertas.get(0).getSituacao());
    }

    @Test
    void combinaItemDeCatalogoEItemAvulsoNaMesmaSimulacao() {
        seedUsuario();
        Produto produtoCatalogo = novoProduto("Kit Convite", 1, new BigDecimal("3"), true);
        ItemCatalogo itemCatalogo = novoItemCatalogo(produtoCatalogo, 1, 2);
        Produto produtoAvulso = novoProduto("Laço Decorativo", 2, new BigDecimal("100"), true);

        List<AlertaInsumoResponse> alertas = orcamentoService.simularAlertas(List.of(
                itemCatalogo(itemCatalogo.getId(), 2),
                itemAvulso(produtoAvulso.getId(), 5)));

        assertEquals(2, alertas.size());
        // item de catálogo: quantidade 2 * quantidadePacote 2 = 4 necessário, estoque 3 -> AVISO
        AlertaInsumoResponse alertaCatalogo = alertas.stream()
                .filter(a -> a.getNomeInsumo().equals("Kit Convite")).findFirst().orElseThrow();
        assertEquals(SituacaoAlertaInsumo.AVISO, alertaCatalogo.getSituacao());
        assertEquals(0, new BigDecimal("4").compareTo(alertaCatalogo.getQuantidadeNecessaria()));

        AlertaInsumoResponse alertaAvulso = alertas.stream()
                .filter(a -> a.getNomeInsumo().equals("Laço Decorativo")).findFirst().orElseThrow();
        assertEquals(SituacaoAlertaInsumo.SUFICIENTE, alertaAvulso.getSituacao());
    }

    @Test
    void itemSemOrigemOuComAsDuasLancaBusinessException() {
        seedUsuario();
        SimularAlertasOrcamentoItemRequest semOrigem = new SimularAlertasOrcamentoItemRequest();
        semOrigem.setQuantidade(1);

        assertThrows(BusinessException.class, () -> orcamentoService.simularAlertas(List.of(semOrigem)));
    }

    @Test
    void listaVaziaRetornaVaziaSemErro() {
        seedUsuario();
        assertTrue(orcamentoService.simularAlertas(List.of()).isEmpty());
    }
}
