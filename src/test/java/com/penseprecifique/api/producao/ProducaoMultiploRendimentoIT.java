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
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
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
 * #214/PDC-027 — reversão de PDC-005: produto com insumo não-fracionável na ficha aceita qualquer
 * múltiplo inteiro do rendimento, não mais só 1x. Limite passa a ser o estoque disponível dos
 * insumos não-fracionáveis com permitirEstoqueNegativo=false, não mais "exatamente o rendimento".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoMultiploRendimentoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Produto produtoAtual;

    /** Produto "Caixa de bombom", rendimento 2, insumo Papelão (não-fracionável, 2 por receita). */
    private Insumo seedCaixaDeBombom(boolean permitirEstoqueNegativo, String estoqueInicial) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-multiplo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo papelao = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Papelão").marca("X").unidadeMedida("un")
                .estoqueAtual(new BigDecimal(estoqueInicial)).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .fracionavel(false).build());

        Produto caixa = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Caixa de bombom").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(new BigDecimal("2")).precoVenda(new BigDecimal("10.00")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(caixa).insumo(papelao).quantidade(new BigDecimal("2")).build());

        this.produtoAtual = caixa;
        return papelao;
    }

    private CriarProducaoRequest requestCom(BigDecimal quantidade) {
        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produtoAtual.getId());
        item.setQuantidade(quantidade);
        criar.setProdutos(List.of(item));
        return criar;
    }

    @Test
    void multiploValidoDentroDoEstoquePermiteCriacao() {
        // PDC-CEN-083 — estoque=50, quantidade=6 (3x rendimento) -> maxMultiplos=25, quantidadeMaxima=50, 6<=50
        seedCaixaDeBombom(false, "50");

        ProducaoDetalheResponse detalhe = producaoService.criarProducao(requestCom(new BigDecimal("6")));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, detalhe.getEstado());
    }

    @Test
    void quantidadeNaoMultiplaBloqueiaComMensagemDeMultiplo() {
        // PDC-CEN-084 — 5 não é múltiplo de 2
        seedCaixaDeBombom(false, "50");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> producaoService.criarProducao(requestCom(new BigDecimal("5"))));

        assertTrue(ex.getMessage().contains("exige quantidade em múltiplos de"),
                "mensagem deveria ser sobre múltiplo inválido: " + ex.getMessage());
    }

    @Test
    void multiploValidoMasExcedeEstoqueBloqueiaComMensagemDeLimite() {
        // PDC-CEN-085 — 60 é múltiplo de 2, mas quantidadeMaxima=50 (estoque 50 / 2 por receita = 25 * 2)
        seedCaixaDeBombom(false, "50");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> producaoService.criarProducao(requestCom(new BigDecimal("60"))));

        assertTrue(ex.getMessage().contains("quantidade máxima permitida"),
                "mensagem deveria ser sobre limite de estoque: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Papelão"));
    }

    @Test
    void estoqueInsuficienteParaUmaVezORendimentoNaoBloqueiaCriacao() {
        // Regressão do achado no Frontend de #214 (correção 2026-08-08) — maxMultiplos=0
        // (estoque=1 < ficha.quantidade=2 por receita) não deve bloquear a criação com o erro de
        // teto por estoque; quantidade = rendimento (mínimo possível) é aceita normalmente, e a
        // trava por estoque insuficiente segue acontecendo só ao tentar Iniciar (TRAVADA).
        seedCaixaDeBombom(false, "1");

        ProducaoDetalheResponse detalhe = producaoService.criarProducao(requestCom(new BigDecimal("2")));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, detalhe.getEstado());
    }

    @Test
    void permitirEstoqueNegativoTrueSemLimiteDeMultiplos() {
        // PDC-CEN-086 — insumo permite estoque negativo -> só a checagem de múltiplo se aplica, sem teto
        seedCaixaDeBombom(true, "1");

        ProducaoDetalheResponse detalhe = producaoService.criarProducao(requestCom(new BigDecimal("100")));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, detalhe.getEstado());

        // confirma calcularAlertas() sem alteração: ratioLote=100/2=50, necessaria=2*50=100, estoque=1 -> AVISO
        assertEquals(1, detalhe.getAlertasInsumos().size());
        assertEquals(0, new BigDecimal("100").compareTo(detalhe.getAlertasInsumos().get(0).getQuantidadeNecessaria()));
        assertEquals(com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo.AVISO,
                detalhe.getAlertasInsumos().get(0).getSituacao());
    }

    @Test
    void produtoSemInsumoNaoFracionavelContinuaSemLimiteDeMultiplo() {
        // Regressão — caso fracionável não passa pelo novo gate, quantidade livre
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-multiplo-frac-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo farinha = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(false)
                .fracionavel(true).build());

        Produto bolo = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(bolo).insumo(farinha).quantidade(new BigDecimal("100")).build());

        this.produtoAtual = bolo;

        ProducaoDetalheResponse detalhe = producaoService.criarProducao(requestCom(new BigDecimal("7")));

        assertEquals(EstadoProducao.AGUARDANDO_INICIO, detalhe.getEstado());
    }

    @Test
    void verificarComponentesContinuaBaixandoProporcionalmenteParaMultiploDoRendimento() {
        // Confirma PDC-007 (baixa proporcional via ratioLote) sem alteração para quantidade = 3x rendimento
        Insumo papelao = seedCaixaDeBombom(false, "50");
        UUID producaoId = producaoService.criarProducao(requestCom(new BigDecimal("6"))).getId();

        producaoService.iniciar(producaoId, new IniciarProducaoRequest());

        Insumo atualizado = insumoRepository.findById(papelao.getId()).orElseThrow();
        // ratioLote = 6/2 = 3; necessaria = 2 * 3 = 6; estoque 50 - 6 = 44
        assertEquals(0, new BigDecimal("44").compareTo(atualizado.getEstoqueAtual()));

        Producao producao = producaoRepository.findById(producaoId).orElseThrow();
        assertEquals(EstadoProducao.EM_ANDAMENTO, producao.getEstado());
    }
}
