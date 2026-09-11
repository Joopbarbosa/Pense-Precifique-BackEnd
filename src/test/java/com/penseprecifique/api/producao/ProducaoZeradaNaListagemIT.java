package com.penseprecifique.api.producao;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-B003 (V0.8.3, achado de P-B002) — {@code ProducaoRepository.buscarIdsOrdenados()} tinha um
 * JOIN implícito (não LEFT JOIN) na navegação {@code pp.produto.nome} (usada só no filtro opcional
 * de busca por nome): produção sem nenhum {@link com.penseprecifique.api.shared.domain.entity.ProducaoProduto}
 * desaparecia inteiramente de {@code GET /producoes} (Listagem e Kanban, mesmo método) — não um
 * filtro incorreto, ausência total da linha.
 *
 * <p>Caminho real que motivou a gravidade Alta (não hipotético): RN-NOVA-17 (P-B001,
 * {@code removerProdutoDeProducaoAtiva}) remove produtos um a um de uma produção já
 * {@code EM_ANDAMENTO}/{@code TRAVADA} — removendo o último, a produção fica com 0 produtos sem
 * ser cancelada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoZeradaNaListagemIT {

    @Autowired ProducaoService producaoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-zerada-listagem-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Produção Zerada").ativa(true).build());
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

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** Monta o caminho real de RN-NOVA-17 até uma produção EM_ANDAMENTO ficar com 0 produtos. */
    private Producao producaoZeradaViaRemocaoReal() {
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);
        producaoService.iniciar(producao.getId(), new IniciarProducaoRequest());

        orcamentoService.removerProdutoDeProducaoAtiva(orcamentoId, producao.getId(), produto.getId());

        assertTrue(producaoProdutoRepository.findByProducaoId(producao.getId()).isEmpty(),
                "pré-condição do teste: produção precisa estar realmente com 0 produtos");
        Producao recarregada = producaoRepository.findById(producao.getId()).orElseThrow();
        assertEquals(EstadoProducao.EM_ANDAMENTO, recarregada.getEstado(),
                "produção continua existindo/ativa, não foi cancelada");
        return recarregada;
    }

    /** Bug corrigido: produção zerada continua na Listagem (GET /producoes, tamanho de página normal). */
    @Test
    void producaoZeradaApareceNaListagem() {
        seedUsuarioECliente();
        Producao producaoZerada = producaoZeradaViaRemocaoReal();

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));

        assertTrue(pagina.getContent().stream().anyMatch(p -> p.getId().equals(producaoZerada.getId())),
                "produção sem nenhum produto não pode desaparecer da Listagem");
        ProducaoResponse resposta = pagina.getContent().stream()
                .filter(p -> p.getId().equals(producaoZerada.getId())).findFirst().orElseThrow();
        assertTrue(resposta.getProdutos().isEmpty(), "lista de produtos vazia, sem quebrar a resposta");
    }

    /** Bug corrigido: mesmo método serve o Kanban (size=100) — produção zerada também aparece lá. */
    @Test
    void producaoZeradaApareceNoKanban() {
        seedUsuarioECliente();
        Producao producaoZerada = producaoZeradaViaRemocaoReal();

        Page<ProducaoResponse> kanban = producaoService.listar(null, null, null, null, PageRequest.of(0, 100));

        assertTrue(kanban.getContent().stream().anyMatch(p -> p.getId().equals(producaoZerada.getId())),
                "Kanban usa o mesmo GET /producoes — produção zerada precisa aparecer aqui também");
    }

    /** Sem regressão: Detalhe por ID direto já funcionava antes da correção e continua funcionando. */
    @Test
    void producaoZeradaContinuaAcessivelPorDetalheDireto() {
        seedUsuarioECliente();
        Producao producaoZerada = producaoZeradaViaRemocaoReal();

        ProducaoDetalheResponse detalhe = producaoService.buscarPorId(producaoZerada.getId());

        assertEquals(producaoZerada.getId(), detalhe.getId());
        assertTrue(detalhe.getProdutos().isEmpty());
    }

    /** Sem regressão: ordenação (numero DESC, default) continua correta com uma produção zerada
     * misturada entre produções com produtos reais. */
    @Test
    void ordenacaoPadraoNaoQuebraComProducaoZeradaMisturada() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Producao producaoComProduto = novaProducao();
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producaoComProduto).produto(produtoA).quantidade(new BigDecimal("1")).build());
        Producao producaoZerada = producaoZeradaViaRemocaoReal(); // numero mais alto, criada depois

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));

        List<UUID> ids = pagina.getContent().stream().map(ProducaoResponse::getId).toList();
        assertEquals(2, ids.size());
        assertEquals(producaoZerada.getId(), ids.get(0), "numero mais alto (produção zerada) vem primeiro, DESC");
        assertEquals(producaoComProduto.getId(), ids.get(1));
    }

    /** Sem regressão: busca por nome de produto continua funcionando (o filtro que motivava o JOIN). */
    @Test
    void buscaPorNomeDeProdutoContinuaFuncionandoENaoIncluiProducaoZerada() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Producao producaoComProduto = novaProducao();
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producaoComProduto).produto(produtoA).quantidade(new BigDecimal("1")).build());
        producaoZeradaViaRemocaoReal();

        Page<ProducaoResponse> pagina = producaoService.listar(produtoA.getNome(), null, null, null, PageRequest.of(0, 20));

        assertEquals(1, pagina.getContent().size(),
                "busca por nome de produto não deve trazer a produção zerada (ela não tem nenhum produto pra casar)");
        assertEquals(producaoComProduto.getId(), pagina.getContent().get(0).getId());
    }
}
