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
import com.penseprecifique.api.shared.dto.request.RetomarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoDetalheResponse;
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
 * #136/RN-052 — iniciar()/retomar() religam confirmarEstoqueNegativoInsumoIds (antes um stub sem
 * efeito): componente com permitirEstoqueNegativo=true cujo resultado ficaria negativo e ainda não
 * confirmado gera ConfirmacaoEstoqueNegativoResponse em vez de baixar; RN-059 (permitirEstoqueNegativo
 * =false) continua bloqueando incondicionalmente, sem gate de confirmação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoRn052EstoqueNegativoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;

    private Insumo seedUsuarioProdutoEInsumo(boolean permitirEstoqueNegativo, String estoqueInicial) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-rn052-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Corante Gel").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal(estoqueInicial)).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .fracionavel(true).build());

        Produto bolo = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Decorado").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(bolo).insumo(insumo).quantidade(new BigDecimal("1")).build());

        this.produtoAtual = bolo;
        return insumo;
    }

    private Produto produtoAtual;

    private UUID criarProducao(int numero, BigDecimal quantidade) {
        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produtoAtual.getId());
        item.setQuantidade(quantidade);
        criar.setProdutos(List.of(item));
        return producaoService.criarProducao(criar).getId();
    }

    @Test
    void iniciarSemConfirmacaoRetornaAvisoENaoBaixa() {
        // rendimento=10, quantidade=50 -> ratioLote=5, necessaria=1*5=5; estoque=3 -> resultante=-2
        Insumo corante = seedUsuarioProdutoEInsumo(true, "3");
        UUID producaoId = criarProducao(1, new BigDecimal("50"));

        Object resultado = producaoService.iniciar(producaoId, new IniciarProducaoRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(corante.getId(), aviso.getAvisos().get(0).getComponenteId());

        Insumo inalterado = insumoRepository.findById(corante.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "não deve ter baixado sem confirmação");

        Producao producao = producaoRepository.findById(producaoId).orElseThrow();
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producao.getEstado(), "não deve ter transicionado sem confirmação");
    }

    @Test
    void iniciarComConfirmacaoBaixaENegativa() {
        Insumo corante = seedUsuarioProdutoEInsumo(true, "3");
        UUID producaoId = criarProducao(2, new BigDecimal("50"));

        IniciarProducaoRequest request = new IniciarProducaoRequest();
        request.setConfirmarEstoqueNegativoInsumoIds(List.of(corante.getId()));
        Object resultado = producaoService.iniciar(producaoId, request);

        ProducaoDetalheResponse detalhe = assertInstanceOf(ProducaoDetalheResponse.class, resultado);
        assertEquals("EM_ANDAMENTO", detalhe.getEstado().name());

        Insumo atualizado = insumoRepository.findById(corante.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-2").compareTo(atualizado.getEstoqueAtual()), "confirmado, deve baixar e ficar negativo");
    }

    @Test
    void permitirEstoqueNegativoFalseContinuaBloqueandoSemGateDeConfirmacao() {
        Insumo corante = seedUsuarioProdutoEInsumo(false, "3");
        UUID producaoId = criarProducao(3, new BigDecimal("50"));

        IniciarProducaoRequest request = new IniciarProducaoRequest();
        request.setConfirmarEstoqueNegativoInsumoIds(List.of(corante.getId()));
        Object resultado = producaoService.iniciar(producaoId, request);

        ProducaoDetalheResponse detalhe = assertInstanceOf(ProducaoDetalheResponse.class, resultado);
        assertEquals("TRAVADA", detalhe.getEstado().name(), "RN-059 bloqueia incondicionalmente, não é contornável por confirmação");

        Insumo inalterado = insumoRepository.findById(corante.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()));
    }

    @Test
    void estoqueSuficienteBaixaDiretoSemAviso() {
        Insumo corante = seedUsuarioProdutoEInsumo(true, "100");
        UUID producaoId = criarProducao(4, new BigDecimal("50"));

        Object resultado = producaoService.iniciar(producaoId, new IniciarProducaoRequest());

        ProducaoDetalheResponse detalhe = assertInstanceOf(ProducaoDetalheResponse.class, resultado);
        assertEquals("EM_ANDAMENTO", detalhe.getEstado().name());

        Insumo atualizado = insumoRepository.findById(corante.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("95").compareTo(atualizado.getEstoqueAtual()));
    }

    @Test
    void retomarSemConfirmacaoRetornaAvisoENaoBaixa() {
        // Insumo começa bloqueante (permitir=false) -> iniciar() trava. Depois vira permitir=true (edição de
        // cadastro), ainda negativo -> retomar() deve reverificar, achar aviso RN-052, e não baixar sem confirmar.
        Insumo corante = seedUsuarioProdutoEInsumo(false, "3");
        UUID producaoId = criarProducao(5, new BigDecimal("50"));
        producaoService.iniciar(producaoId, new IniciarProducaoRequest());
        assertEquals(EstadoProducao.TRAVADA, producaoRepository.findById(producaoId).orElseThrow().getEstado());

        corante.setPermitirEstoqueNegativo(true);
        insumoRepository.save(corante);

        Object resultado = producaoService.retomar(producaoId, new RetomarProducaoRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(EstadoProducao.TRAVADA, producaoRepository.findById(producaoId).orElseThrow().getEstado(),
                "permanece TRAVADA sem confirmação");
    }

    @Test
    void retomarComConfirmacaoBaixaEAvanca() {
        Insumo corante = seedUsuarioProdutoEInsumo(false, "3");
        UUID producaoId = criarProducao(6, new BigDecimal("50"));
        producaoService.iniciar(producaoId, new IniciarProducaoRequest());

        corante.setPermitirEstoqueNegativo(true);
        insumoRepository.save(corante);

        RetomarProducaoRequest request = new RetomarProducaoRequest();
        request.setConfirmarEstoqueNegativoInsumoIds(List.of(corante.getId()));
        Object resultado = producaoService.retomar(producaoId, request);

        ProducaoDetalheResponse detalhe = assertInstanceOf(ProducaoDetalheResponse.class, resultado);
        assertEquals("EM_ANDAMENTO", detalhe.getEstado().name());

        Insumo atualizado = insumoRepository.findById(corante.getId()).orElseThrow();
        assertTrue(atualizado.getEstoqueAtual().compareTo(BigDecimal.ZERO) < 0);
    }
}
