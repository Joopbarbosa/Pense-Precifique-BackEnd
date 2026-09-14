package com.penseprecifique.api.producao;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoDesagruparIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Produto produtoA;
    private Produto produtoB;

    private void seedCenario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-desagrupar-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
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

    private UUID agruparEmAguardandoInicio() {
        UUID p1 = criarProducao(produtoA.getId(), BigDecimal.ONE);
        UUID p2 = criarProducao(produtoB.getId(), BigDecimal.ONE);

        AgruparProducoesRequest agrupar = new AgruparProducoesRequest();
        agrupar.setProducaoIds(List.of(p1, p2));
        agrupar.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        agrupar.setJustificativa("Agrupamento de teste automatizado para RN-NOVA-5 do #450.");

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
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA, itemB));

        DesagruparProducaoResponse resultado = producaoService.desagrupar(agrupadaId, request);

        assertEquals(2, resultado.getProducoesNovas().size());
        for (ProducaoDetalheResponse filha : resultado.getProducoesNovas()) {
            assertEquals(EstadoProducao.AGUARDANDO_INICIO, filha.getEstado());
            assertEquals(1, filha.getProdutos().size());
        }
        assertEquals(EstadoProducao.NAO_REALIZADA, resultado.getProducaoOriginal().getEstado());

        Producao original = producaoRepository.findById(agrupadaId).orElseThrow();
        assertTrue(original.getJustificativaNaoRealizada().contains("Desagrupada"));
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
        // Falta o item de produtoB — lista incompleta.
        DesagruparProducaoRequest request = new DesagruparProducaoRequest();
        request.setItens(List.of(itemA));

        assertThrows(BusinessException.class, () -> producaoService.desagrupar(agrupadaId, request));
    }
}
