package com.penseprecifique.api.producao;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AgruparProducoesResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
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

/**
 * #136/RN-052 — agrupar() com estadoDestino=EM_ANDAMENTO consolida os produtos das produções de
 * origem e baixa insumo da nova produção. Componente com estoque negativo permitido e ainda não
 * confirmado precisa interromper o agrupamento inteiro antes de qualquer gravação (nenhum estorno de
 * consumo real, nenhuma produção nova, nenhuma original virando NÃO_REALIZADA).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoAgruparEstoqueNegativoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Insumo insumoAviso;
    private List<UUID> producaoIds;

    private void seedCenarioAgrupamento() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-agrupar-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        insumoAviso = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Corante Gel").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1")).permitirEstoqueNegativo(true).fracionavel(true).build());

        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Colorido").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumoAviso).quantidade(new BigDecimal("1")).build());

        UUID producao1 = criarProducao(produto.getId(), new BigDecimal("25"));
        UUID producao2 = criarProducao(produto.getId(), new BigDecimal("25"));
        producaoIds = List.of(producao1, producao2);
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

    private AgruparProducoesRequest requestAgrupar() {
        AgruparProducoesRequest request = new AgruparProducoesRequest();
        request.setProducaoIds(producaoIds);
        request.setEstadoDestino(EstadoProducao.EM_ANDAMENTO);
        request.setJustificativa("Agrupamento de teste automatizado para RN-052 do #136");
        return request;
    }

    @Test
    void agruparSemConfirmacaoRetornaAvisoENaoGravaNada() {
        seedCenarioAgrupamento();

        Object resultado = producaoService.agrupar(requestAgrupar());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(insumoAviso.getId(), aviso.getAvisos().get(0).getComponenteId());

        for (UUID producaoId : producaoIds) {
            Producao original = producaoRepository.findById(producaoId).orElseThrow();
            assertEquals(EstadoProducao.AGUARDANDO_INICIO, original.getEstado(), "originais não devem ter transicionado");
        }
        Insumo inalterado = insumoRepository.findById(insumoAviso.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1").compareTo(inalterado.getEstoqueAtual()), "não deve ter baixado sem confirmação");
    }

    @Test
    void agruparComConfirmacaoConsolidaEBaixaNegativo() {
        seedCenarioAgrupamento();

        AgruparProducoesRequest request = requestAgrupar();
        request.setConfirmarEstoqueNegativoInsumoIds(List.of(insumoAviso.getId()));
        Object resultado = producaoService.agrupar(request);

        AgruparProducoesResponse response = assertInstanceOf(AgruparProducoesResponse.class, resultado);
        assertEquals(EstadoProducao.EM_ANDAMENTO, response.getProducaoNova().getEstado());
        for (var original : response.getProducoesOriginais()) {
            assertEquals(EstadoProducao.NAO_REALIZADA, original.getEstado());
        }

        Insumo atualizado = insumoRepository.findById(insumoAviso.getId()).orElseThrow();
        // 25+25=50 consolidado, rendimento=10 -> ratio=5, necessaria=1*5=5, estoque 1-5=-4
        assertEquals(0, new BigDecimal("-4").compareTo(atualizado.getEstoqueAtual()));
    }
}
