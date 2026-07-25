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
import com.penseprecifique.api.shared.dto.request.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.DivisaoProducaoResponse;
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
 * #136/RN-052 — dividir() (RN-065) é chamado de dentro de iniciar() quando há bloqueante e
 * dividir=true. O produto sem bloqueio (produção A) pode ainda ter um componente diferente com
 * estoque negativo permitido — esse caso também precisa do gate de confirmação, e nada pode ser
 * gravado (nem a divisão, nem a produção original virar NÃO_REALIZADA) enquanto pendente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoDividirEstoqueNegativoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Insumo insumoBloqueante;
    private Insumo insumoAviso;

    private UUID seedCenarioDivisao() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-dividir-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        insumoBloqueante = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Chocolate Belga").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1")).permitirEstoqueNegativo(false).fracionavel(true).build());
        insumoAviso = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(2).nome("Corante Gel").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1")).permitirEstoqueNegativo(true).fracionavel(true).build());

        Produto produtoBloqueado = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Chocolate").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoBloqueado).insumo(insumoBloqueante).quantidade(new BigDecimal("1")).build());

        Produto produtoLiberado = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Bolo Colorido").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoLiberado).insumo(insumoAviso).quantidade(new BigDecimal("1")).build());

        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item1 = new ProducaoProdutoRequest();
        item1.setProdutoId(produtoBloqueado.getId());
        item1.setQuantidade(new BigDecimal("50"));
        ProducaoProdutoRequest item2 = new ProducaoProdutoRequest();
        item2.setProdutoId(produtoLiberado.getId());
        item2.setQuantidade(new BigDecimal("50"));
        criar.setProdutos(List.of(item1, item2));

        return producaoService.criarProducao(criar).getId();
    }

    @Test
    void dividirSemConfirmacaoRetornaAvisoENaoGravaNada() {
        UUID producaoId = seedCenarioDivisao();

        IniciarProducaoRequest request = new IniciarProducaoRequest();
        request.setDividir(true);
        Object resultado = producaoService.iniciar(producaoId, request);

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(insumoAviso.getId(), aviso.getAvisos().get(0).getComponenteId());

        Producao original = producaoRepository.findById(producaoId).orElseThrow();
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, original.getEstado(), "original não deve ter transicionado");
        assertTrue(producaoRepository.findByProducaoOrigemId(producaoId).isEmpty(), "nenhuma produção filha deve ter sido criada");

        assertEquals(0, new BigDecimal("1").compareTo(insumoRepository.findById(insumoAviso.getId()).orElseThrow().getEstoqueAtual()));
        assertEquals(0, new BigDecimal("1").compareTo(insumoRepository.findById(insumoBloqueante.getId()).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void dividirComConfirmacaoDivideEBaixaProducaoA() {
        UUID producaoId = seedCenarioDivisao();

        IniciarProducaoRequest request = new IniciarProducaoRequest();
        request.setDividir(true);
        request.setConfirmarEstoqueNegativoInsumoIds(List.of(insumoAviso.getId()));
        Object resultado = producaoService.iniciar(producaoId, request);

        DivisaoProducaoResponse divisao = assertInstanceOf(DivisaoProducaoResponse.class, resultado);
        assertEquals(EstadoProducao.NAO_REALIZADA, divisao.getProducaoOriginal().getEstado());
        assertEquals(EstadoProducao.EM_ANDAMENTO, divisao.getProducaoA().getEstado());
        assertEquals(EstadoProducao.TRAVADA, divisao.getProducaoB().getEstado());

        Insumo avisoAtualizado = insumoRepository.findById(insumoAviso.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-4").compareTo(avisoAtualizado.getEstoqueAtual()), "produção A confirmada deve baixar e ficar negativa");

        Insumo bloqueanteInalterado = insumoRepository.findById(insumoBloqueante.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1").compareTo(bloqueanteInalterado.getEstoqueAtual()), "produção B (bloqueada) não baixa nada");
    }
}
