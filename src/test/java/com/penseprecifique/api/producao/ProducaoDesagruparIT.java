package com.penseprecifique.api.producao;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.DesagruparProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AgruparProducoesResponse;
import com.penseprecifique.api.shared.dto.response.producao.DesagruparProducaoResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-5 (V0.10.0, #450) — desagrupar produção agrupada. Inverso de agrupar(): 1 produção nova
 * por produto, nunca revive as originais (append-only, RN-PROD-VINC-04). Só permitido em
 * AGUARDANDO_INICIO.
 *
 * <p>RN-NOVA-11 (V0.10.0, #469) — critério aditivo de contagem (mais de 2 produtos) — revogada por
 * RN-NOVA-14 (mesma versão, achado do teste manual, 2ª rodada): a artesã confirmou que 2 produtos
 * também deve ser elegível (não existe grupo com menos de 2). {@link #agruparDoisProdutos()}
 * permanece — agora cobre o caso de sucesso com exatamente 2 itens, não mais o de rejeição.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoDesagruparIT {

    @Autowired ProducaoService producaoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Produto produtoA;
    private Produto produtoB;
    private Produto produtoC;

    private void seedCenario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-desagrupar-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida(unidadeMedida("g"))
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true).build());

        produtoA = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo A").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoA).insumo(insumo).quantidade(new BigDecimal("1")).build());

        produtoB = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Bolo B").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("15.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoB).insumo(insumo).quantidade(new BigDecimal("1")).build());

        produtoC = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(3).nome("Bolo C").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("20.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoC).insumo(insumo).quantidade(new BigDecimal("1")).build());
    }

    private UUID criarProducao(UUID produtoId, BigDecimal quantidade) {
        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        criar.setProdutos(List.of(item));
        return producaoService.criarProducao(criar).getId();
    }

    /** 3 produtos — elegível pra desagrupar. */
    private UUID agruparEmAguardandoInicio() {
        UUID p1 = criarProducao(produtoA.getId(), BigDecimal.ONE);
        UUID p2 = criarProducao(produtoB.getId(), BigDecimal.ONE);
        UUID p3 = criarProducao(produtoC.getId(), BigDecimal.ONE);

        AgruparProducoesRequest agrupar = new AgruparProducoesRequest();
        agrupar.setProducaoIds(List.of(p1, p2, p3));
        agrupar.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        agrupar.setJustificativa("Agrupamento de teste automatizado para RN-NOVA-5 do #450.");

        AgruparProducoesResponse resultado = (AgruparProducoesResponse) producaoService.agrupar(agrupar);
        return resultado.getProducaoNova().getId();
    }

    /** 2 produtos — elegível pra desagrupar (RN-NOVA-14 revoga a restrição de RN-NOVA-11). */
    private UUID agruparDoisProdutos() {
        UUID p1 = criarProducao(produtoA.getId(), BigDecimal.ONE);
        UUID p2 = criarProducao(produtoB.getId(), BigDecimal.ONE);

        AgruparProducoesRequest agrupar = new AgruparProducoesRequest();
        agrupar.setProducaoIds(List.of(p1, p2));
        agrupar.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        agrupar.setJustificativa("Agrupamento de teste automatizado (2 itens) para RN-NOVA-11 do #469.");

        AgruparProducoesResponse resultado = (AgruparProducoesResponse) producaoService.agrupar(agrupar);
        return resultado.getProducaoNova().getId();
    }

    @Test
    void desagruparCriaUmaProducaoNovaPorProdutoEOriginalViraNaoRealizada() {
        seedCenario();
        UUID agrupadaId = agruparEmAguardandoInicio();

        DesagruparProducaoRequest.ItemDesagrupar itemA = new DesagruparProducaoRequest.ItemDesagrupar();
        itemA.setProdutoId(produtoA.getId());
        itemA.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest.ItemDesagrupar itemB = new DesagruparProducaoRequest.ItemDesagrupar();
        itemB.setProdutoId(produtoB.getId());
        itemB.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest.ItemDesagrupar itemC = new DesagruparProducaoRequest.ItemDesagrupar();
        itemC.setProdutoId(produtoC.getId());
        itemC.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA, itemB, itemC));

        DesagruparProducaoResponse resultado = producaoService.desagrupar(agrupadaId, request);

        assertEquals(3, resultado.getProducoesNovas().size());
        for (ProducaoDetalheResponse filha : resultado.getProducoesNovas()) {
            assertEquals(EstadoProducao.AGUARDANDO_INICIO, filha.getEstado());
            assertEquals(1, filha.getProdutos().size());
        }
        assertEquals(EstadoProducao.NAO_REALIZADA, resultado.getProducaoOriginal().getEstado());

        Producao original = producaoRepository.findById(agrupadaId).orElseThrow();
        assertTrue(original.getJustificativaNaoRealizada().contains("Desagrupada"));
    }

    @Test
    void desagruparComApenas2ItensFunciona() {
        // RN-NOVA-14 (V0.10.0) — revoga RN-NOVA-11/#469: 2 produtos/customizações agrupados também
        // é elegível pra desagrupar, não só "mais de 2".
        seedCenario();
        UUID agrupadaId = agruparDoisProdutos();

        DesagruparProducaoRequest.ItemDesagrupar itemA = new DesagruparProducaoRequest.ItemDesagrupar();
        itemA.setProdutoId(produtoA.getId());
        itemA.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest.ItemDesagrupar itemB = new DesagruparProducaoRequest.ItemDesagrupar();
        itemB.setProdutoId(produtoB.getId());
        itemB.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA, itemB));

        DesagruparProducaoResponse resultado = producaoService.desagrupar(agrupadaId, request);

        assertEquals(2, resultado.getProducoesNovas().size());
        assertEquals(EstadoProducao.NAO_REALIZADA, resultado.getProducaoOriginal().getEstado());
    }

    @Test
    void desagruparProducaoQueNaoVeioDeAgrupamentoFalha() {
        seedCenario();
        UUID producaoAvulsa = criarProducao(produtoA.getId(), BigDecimal.ONE);

        DesagruparProducaoRequest.ItemDesagrupar item = new DesagruparProducaoRequest.ItemDesagrupar();
        item.setProdutoId(produtoA.getId());
        item.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(item));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> producaoService.desagrupar(producaoAvulsa, request));
        assertTrue(ex.getMessage().contains("agrupamento"));
    }

    @Test
    void desagruparProducaoJaIniciadaFalha() {
        seedCenario();
        UUID agrupadaId = agruparEmAguardandoInicio();
        producaoService.iniciar(agrupadaId, new com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest());

        DesagruparProducaoRequest.ItemDesagrupar itemA = new DesagruparProducaoRequest.ItemDesagrupar();
        itemA.setProdutoId(produtoA.getId());
        itemA.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest.ItemDesagrupar itemB = new DesagruparProducaoRequest.ItemDesagrupar();
        itemB.setProdutoId(produtoB.getId());
        itemB.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA, itemB));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> producaoService.desagrupar(agrupadaId, request));
        assertTrue(ex.getMessage().contains("aguardando início"));
    }

    @Test
    void desagruparComListaDeItensDivergenteDosProdutosReaisFalha() {
        seedCenario();
        UUID agrupadaId = agruparEmAguardandoInicio();

        DesagruparProducaoRequest.ItemDesagrupar itemA = new DesagruparProducaoRequest.ItemDesagrupar();
        itemA.setProdutoId(produtoA.getId());
        itemA.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        DesagruparProducaoRequest.ItemDesagrupar itemB = new DesagruparProducaoRequest.ItemDesagrupar();
        itemB.setProdutoId(produtoB.getId());
        itemB.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        // Falta o item de produtoC — lista incompleta.
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA, itemB));

        assertThrows(BusinessException.class, () -> producaoService.desagrupar(agrupadaId, request));
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
