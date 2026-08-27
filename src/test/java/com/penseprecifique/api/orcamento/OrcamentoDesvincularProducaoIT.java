package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
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
 * RN-ORC-VINC-03 (V0.8.2, #320, P-B017 Parte 3) — desvincular reverte de verdade o que o vínculo
 * adicionou à produção, respeitando múltiplas origens no mesmo produto e a restrição de estado
 * simétrica ao vincular (só AGUARDANDO_INICIO — decisão fechada em chat, ver DECISOES_V0.8.2.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoDesvincularProducaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-desvincular-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Desvincular Produção").ativa(true).build());
    }

    private Produto novoProduto() {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("50.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private OrcamentoItemRequest itemDe(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoRequest requestComItens(List<OrcamentoItemRequest> itens) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(itens);
        req.setSinalAtivo(false);
        return req;
    }

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade) {
        return orcamentoService.criar(requestComItens(List.of(itemDe(produto, quantidade)))).getId();
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** Caso 1 — desvincular simples: 1 orçamento, reverte tudo (ProducaoProduto some, vínculo some). */
    @Test
    void desvincularSimplesReverteTudoCorretamente() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId());

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "ProducaoProduto deve ser removido quando a quantidade zera");
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isEmpty(),
                "vínculo deve deixar de existir após desvincular");

        List<HistoricoStatusProducao> removidos = historicoStatusProducaoRepository
                .findByProducaoIdAndReferenciaOrcamentoIdAndTipoEvento(
                        producao.getId(), orcamentoId, TipoEventoHistoricoProducao.ITEM_REMOVIDO);
        assertEquals(1, removidos.size());
        assertEquals(produto.getId(), removidos.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("4").compareTo(removidos.get(0).getQuantidade()));
    }

    /** Caso 2 — múltiplas origens no mesmo produto: desvincular um orçamento não toca a contribuição do outro. */
    @Test
    void desvincularComMultiplasOrigensNoMesmoProdutoReverteSoAFracaoDaqueleOrcamento() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        Producao producao = novaProducao();

        UUID orcamentoA = criarOrcamentoComProduto(produto, 3);
        UUID orcamentoB = criarOrcamentoComProduto(produto, 5);
        vincular(orcamentoA, producao);
        vincular(orcamentoB, producao);

        ProducaoProduto antes = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("8").compareTo(antes.getQuantidade()));

        orcamentoService.desvincularProducao(orcamentoA, producao.getId());

        ProducaoProduto depois = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(depois.getQuantidade()),
                "só a fração do orçamento A (3) deve ter sido revertida, preservando a de B (5)");

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoA, producao.getId()).isEmpty());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoB, producao.getId()).isPresent(),
                "vínculo do orçamento B não pode ser afetado por desvincular A");
    }

    /** Caso 3 — desvincular bloqueado quando a produção não está mais AGUARDANDO_INICIO. */
    @Test
    void desvincularBloqueadoForaDeAguardandoInicio() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        producao.setEstado(EstadoProducao.EM_ANDAMENTO);
        producaoRepository.save(producao);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.desvincularProducao(orcamentoId, producao.getId()));
        assertTrue(ex.getMessage().toLowerCase().contains("já começou"));

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isPresent(),
                "vínculo não pode ser removido quando a reversão é bloqueada");
        ProducaoProduto inalterado = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(inalterado.getQuantidade()));
    }

    /** Caso 4 — piso em zero: produção editada manualmente para uma quantidade menor que o histórico não fica negativa. */
    @Test
    void desvincularComPisoEmZeroQuandoProducaoFoiEditadaManualmente() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 6);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        ProducaoProduto producaoProduto = producaoProdutoRepository
                .findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).orElseThrow();
        producaoProduto.setQuantidade(new BigDecimal("2"));
        producaoProdutoRepository.save(producaoProduto);

        orcamentoService.desvincularProducao(orcamentoId, producao.getId());

        assertTrue(producaoProdutoRepository.findByProducaoIdAndProdutoId(producao.getId(), produto.getId()).isEmpty(),
                "quantidade não pode ficar negativa — deve zerar e remover a linha, nunca estourar para baixo");
    }

    /** Vínculo inexistente lança ResourceNotFoundException. */
    @Test
    void desvincularSemVinculoLancaResourceNotFound() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamentoComProduto(novoProduto(), 1);
        Producao producao = novaProducao();

        assertThrows(ResourceNotFoundException.class,
                () -> orcamentoService.desvincularProducao(orcamentoId, producao.getId()));
    }
}
