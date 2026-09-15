package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemCustomizacaoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Achado do teste manual (V0.10.0) — Customização é produzível (tem ficha técnica/rendimento igual
 * a Produto), mas todo o tratamento de estoque/produção do Orçamento (avisos, bloqueio, baixa/
 * reversão, itens-sem-estoque, criação/sincronização de Produção) só considerava o produto
 * principal de cada item, nunca as customizações anexadas (`OrcamentoItemCustomizacao`).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoEstoqueCustomizacaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumero = 1;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-estoque-custom-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Estoque Customização").ativa(true).build());
    }

    private Produto novoProduto(TipoProduto tipo, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumero++).nome("Produto " + tipo + " " + proximoNumero)
                .tipo(tipo).tempoProducao(30).estoqueAtual(estoqueAtual)
                .permitirEstoqueNegativo(permitirEstoqueNegativo)
                .rendimento(BigDecimal.TEN).precoVenda(new BigDecimal("10.00")).build());
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(proximoNumero++).nome("Insumo " + proximoNumero).marca("X")
                .unidadeMedida("g").estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true)
                .fracionavel(true).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(BigDecimal.ONE).build());
        return produto;
    }

    private OrcamentoRequest requestComCustomizacao(Produto produtoPrincipal, Produto customizacao,
                                                      int qtdCustomizacao) {
        OrcamentoItemCustomizacaoRequest custReq = new OrcamentoItemCustomizacaoRequest();
        custReq.setProdutoId(customizacao.getId());
        custReq.setQuantidade(qtdCustomizacao);

        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produtoPrincipal.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(1);
        item.setCustomizacoes(List.of(custReq));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        return req;
    }

    @Test
    void avisoEstoqueConsideraCustomizacao() {
        seed();
        Produto produtoPrincipal = novoProduto(TipoProduto.PRODUTO, new BigDecimal("100"), true);
        Produto customizacao = novoProduto(TipoProduto.CUSTOMIZACAO, BigDecimal.ZERO, true);

        OrcamentoDetalheResponse detalhe = orcamentoService.criar(requestComCustomizacao(produtoPrincipal, customizacao, 2));

        assertTrue(detalhe.getAvisosEstoque().stream().anyMatch(a -> a.getProdutoId().equals(customizacao.getId())),
                "customização com estoque 0 e 2 solicitadas deveria gerar aviso");
    }

    @Test
    void itensSemEstoqueIncluiCustomizacao() {
        seed();
        Produto produtoPrincipal = novoProduto(TipoProduto.PRODUTO, new BigDecimal("100"), true);
        Produto customizacao = novoProduto(TipoProduto.CUSTOMIZACAO, BigDecimal.ZERO, true);
        UUID orcamentoId = orcamentoService.criar(requestComCustomizacao(produtoPrincipal, customizacao, 3)).getId();

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamentoId);

        ItemSemEstoqueResponse itemCustom = semEstoque.stream()
                .filter(i -> i.getProdutoId().equals(customizacao.getId())).findFirst().orElseThrow(
                        () -> new AssertionError("Customização deveria aparecer em itensSemEstoque"));
        assertEquals(new BigDecimal("3"), itemCustom.getQuantidadeSolicitada());
        assertEquals(null, itemCustom.getProducaoVinculadaId());
    }

    @Test
    void criarProducaoVinculadaCobreCustomizacaoEItensSemEstoqueReflete() {
        seed();
        Produto produtoPrincipal = novoProduto(TipoProduto.PRODUTO, new BigDecimal("100"), true);
        Produto customizacao = novoProduto(TipoProduto.CUSTOMIZACAO, BigDecimal.ZERO, true);
        UUID orcamentoId = orcamentoService.criar(requestComCustomizacao(produtoPrincipal, customizacao, 1)).getId();

        CriarProducaoVinculadaRequest req = new CriarProducaoVinculadaRequest();
        req.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        req.setProdutoIds(List.of(customizacao.getId()));
        orcamentoService.criarProducaoVinculada(orcamentoId, req);

        List<ItemSemEstoqueResponse> semEstoque = orcamentoService.itensSemEstoque(orcamentoId);
        ItemSemEstoqueResponse itemCustom = semEstoque.stream()
                .filter(i -> i.getProdutoId().equals(customizacao.getId())).findFirst().orElseThrow();
        assertTrue(itemCustom.getProducaoVinculadaId() != null,
                "produção criada especificamente pra Customização deveria cobrir esse item");
    }

    @Test
    void finalizarBaixaEstoqueDaCustomizacaoECancelarRevertem() {
        seed();
        Produto produtoPrincipal = novoProduto(TipoProduto.PRODUTO, new BigDecimal("100"), true);
        Produto customizacao = novoProduto(TipoProduto.CUSTOMIZACAO, new BigDecimal("50"), true);
        UUID orcamentoId = orcamentoService.criar(requestComCustomizacao(produtoPrincipal, customizacao, 4)).getId();

        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // APROVADO -> EM_PRODUCAO
        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // -> FINALIZADO
        assertInstanceOf(OrcamentoDetalheResponse.class, resultado, "estoque folgado não deveria gerar aviso/bloqueio");

        Produto customizacaoAposFinalizar = produtoRepository.findById(customizacao.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("46").compareTo(customizacaoAposFinalizar.getEstoqueAtual()),
                "baixa de estoque da customização (50 - 4) não estava acontecendo antes desta correção");

        AvancaStatusRequest cancelReq = new AvancaStatusRequest();
        cancelReq.setPercentualMulta(BigDecimal.ZERO);
        cancelReq.setMotivoCancelamento("Cancelamento de teste automatizado — reversão de estoque de customização.");
        orcamentoService.cancelar(orcamentoId, cancelReq);

        Produto customizacaoAposCancelar = produtoRepository.findById(customizacao.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50").compareTo(customizacaoAposCancelar.getEstoqueAtual()),
                "reversão de estoque da customização não estava acontecendo antes desta correção");
    }
}
