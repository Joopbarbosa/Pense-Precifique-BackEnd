package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-5 (V0.8.2) — duplicar orçamento. Cobre os 4 casos numéricos do prompt P-B004: item de
 * Catálogo com preço recalculado (Caso 1), item avulso com preço recalculado (Caso 2), campos de
 * ciclo de vida resetados (Caso 3), datas limpas (Caso 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoDuplicarIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroCatalogo = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-duplicar-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Duplicar").ativa(true).build());
    }

    /** RN-PROD-VINC-01 exige ficha técnica + rendimento válidos — mesma regra de criarProducao(). */
    private Produto novoProduto(BigDecimal precoVenda) {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(precoVenda).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private ItemCatalogo novoItemCatalogo(BigDecimal precoVenda) {
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(proximoNumeroCatalogo++).nome("Catálogo Teste " + UUID.randomUUID()).ativo(true).build());
        Produto produto = novoProduto(precoVenda);
        return itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(1)
                .precoVenda(precoVenda).build());
    }

    private OrcamentoItemRequest itemDeCatalogo(ItemCatalogo itemCatalogo, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setItemCatalogoId(itemCatalogo.getId());
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoItemRequest itemAvulso(Produto produto, BigDecimal precoUnitario, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(precoUnitario);
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoRequest requestBase(List<OrcamentoItemRequest> itens) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(itens);
        return req;
    }

    @Test
    void itemDeCatalogoTemPrecoRecalculadoNaDuplicacao() {
        seedUsuarioECliente();
        ItemCatalogo itemCatalogo = novoItemCatalogo(new BigDecimal("40.00"));

        OrcamentoDetalheResponse original = orcamentoService.criar(requestBase(List.of(itemDeCatalogo(itemCatalogo, 2))));
        assertEquals(0, new BigDecimal("80.00").compareTo(original.getTotal()));

        // Preço vivo do item de catálogo sobe depois da criação do original.
        itemCatalogo.setPrecoVenda(new BigDecimal("55.00"));
        itemCatalogoRepository.save(itemCatalogo);

        OrcamentoDetalheResponse duplicado = orcamentoService.duplicar(original.getId());

        assertEquals(1, duplicado.getItens().size());
        OrcamentoItemResponse item = duplicado.getItens().get(0);
        assertEquals(0, new BigDecimal("55.00").compareTo(item.getPrecoUnitario()));
        assertEquals(0, new BigDecimal("110.00").compareTo(item.getSubtotal()));
        assertEquals(0, new BigDecimal("110.00").compareTo(duplicado.getTotal()));
    }

    @Test
    void itemAvulsoTemPrecoRecalculadoNaDuplicacao() {
        seedUsuarioECliente();
        Produto produtoY = novoProduto(new BigDecimal("30.00"));

        OrcamentoDetalheResponse original = orcamentoService.criar(
                requestBase(List.of(itemAvulso(produtoY, new BigDecimal("30.00"), 1))));
        assertEquals(0, new BigDecimal("30.00").compareTo(original.getTotal()));

        // Preço de cadastro do produto muda depois da criação do original.
        produtoY.setPrecoVenda(new BigDecimal("48.00"));
        produtoRepository.save(produtoY);

        OrcamentoDetalheResponse duplicado = orcamentoService.duplicar(original.getId());

        assertEquals(1, duplicado.getItens().size());
        OrcamentoItemResponse item = duplicado.getItens().get(0);
        assertEquals(0, new BigDecimal("48.00").compareTo(item.getPrecoUnitario()));
        assertEquals(0, new BigDecimal("48.00").compareTo(duplicado.getTotal()));
    }

    @Test
    void camposDeCicloDeVidaSaoResetadosNaDuplicacao() {
        seedUsuarioECliente();
        Produto produto = novoProduto(new BigDecimal("100.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemAvulso(produto, new BigDecimal("100.00"), 1)));
        criacao.setSinalAtivo(true);
        criacao.setPercentualSinal(new BigDecimal("50"));
        OrcamentoDetalheResponse original = orcamentoService.criar(criacao);
        Integer numeroOriginal = original.getNumero();

        orcamentoService.avancarStatus(original.getId(), new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(original.getId(), new AvancaStatusRequest()); // ENVIADO -> APROVADO
        AvancaStatusRequest sinalReq = new AvancaStatusRequest();
        sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamentoService.avancarStatus(original.getId(), sinalReq); // APROVADO -> AGUARDANDO_SINAL
        orcamentoService.avancarStatus(original.getId(), sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO
        // RN-NOVA-6 — pré-requisito da transição para EM_PRODUCAO
        Producao producao = producaoRepository.save(Producao.builder().usuario(usuario).numero(1).build());
        VincularProducaoRequest vincularReq = new VincularProducaoRequest();
        vincularReq.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(original.getId(), vincularReq);
        orcamentoService.avancarStatus(original.getId(), new AvancaStatusRequest()); // SINAL_PAGO -> EM_PRODUCAO
        orcamentoService.avancarStatus(original.getId(), new AvancaStatusRequest()); // EM_PRODUCAO -> FINALIZADO

        OrcamentoDetalheResponse duplicado = orcamentoService.duplicar(original.getId());

        assertEquals(com.penseprecifique.api.shared.domain.enums.StatusOrcamento.RASCUNHO, duplicado.getStatus());
        assertNull(duplicado.getDataAprovacao());
        assertNull(duplicado.getDataSinalPago());
        assertNotEquals(numeroOriginal, duplicado.getNumero());
    }

    @Test
    void datasSaoLimpasNaDuplicacao() {
        seedUsuarioECliente();
        Produto produto = novoProduto(new BigDecimal("50.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemAvulso(produto, new BigDecimal("50.00"), 1)));
        criacao.setInicioAssimQueAprovado(false);
        criacao.setDataInicioEstimada(java.time.LocalDate.of(2026, 6, 1));
        criacao.setDataValidade(java.time.LocalDateTime.of(2026, 6, 15, 0, 0));
        OrcamentoDetalheResponse original = orcamentoService.criar(criacao);
        assertEquals(java.time.LocalDate.of(2026, 6, 1), original.getDataInicioEstimada());

        OrcamentoDetalheResponse duplicado = orcamentoService.duplicar(original.getId());

        assertNull(duplicado.getDataInicioEstimada());
        assertNull(duplicado.getDataValidade());
        assertTrue(duplicado.isInicioAssimQueAprovado());
    }
}
