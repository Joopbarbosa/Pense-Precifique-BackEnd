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
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoOrcamentoResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-15 (V0.8.3, #375+308) — seção "Orçamentos vinculados" no Detalhe de Produção:
 * {@code ProducaoDetalheResponse.orcamentosVinculados}, populado via
 * {@code OrcamentoProducaoRepository.findByProducaoId}.
 *
 * RN-NOVA-16 (V0.8.3, #375+308, P-B002) — mesmo campo/DTO também em {@code ProducaoResponse}
 * (Listagem/Kanban), populado via {@code findByProducaoIdIn} batched (1 query por página).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProducaoOrcamentosVinculadosIT {

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
    @Autowired EntityManagerFactory entityManagerFactory;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-orcamentos-vinculados-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Vínculos Produção").ativa(true).build());
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

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade, BigDecimal precoUnitario) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(precoUnitario);
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

    /** Sem vínculo — lista vazia, Frontend oculta a seção. */
    @Test
    void producaoSemVinculoRetornaListaVazia() {
        seedUsuarioECliente();
        Producao producao = novaProducao();

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertTrue(response.getOrcamentosVinculados().isEmpty());
    }

    /** 1 vínculo — campos corretos (orcamentoId, identificador ORC-N, status, cliente, valor). */
    @Test
    void producaoComUmVinculoExpoeCamposCorretos() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4, new BigDecimal("25.00"));
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertEquals(1, response.getOrcamentosVinculados().size());
        ProducaoOrcamentoResponse vinculo = response.getOrcamentosVinculados().get(0);
        assertEquals(orcamentoId, vinculo.getOrcamentoId());
        assertTrue(vinculo.getIdentificadorOrcamento().startsWith("ORC-"));
        assertEquals(StatusOrcamento.RASCUNHO, vinculo.getStatusOrcamento());
        assertEquals("Cliente Vínculos Produção", vinculo.getNomeCliente());
        assertEquals(0, new BigDecimal("100.00").compareTo(vinculo.getValorTotal()),
                "valorTotal deve refletir Orcamento.total (4 x 25.00)");
    }

    /** N vínculos — lista completa, sem paginação/limite, mesmo padrão de "Produções relacionadas". */
    @Test
    void producaoComMultiplosVinculosListaTodosSemLimite() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoA = criarOrcamentoComProduto(produtoA, 2, new BigDecimal("10.00"));
        UUID orcamentoB = criarOrcamentoComProduto(produtoB, 3, new BigDecimal("15.00"));
        Producao producao = novaProducao();
        vincular(orcamentoA, producao);
        vincular(orcamentoB, producao);

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertEquals(2, response.getOrcamentosVinculados().size());
        assertTrue(response.getOrcamentosVinculados().stream()
                .anyMatch(v -> v.getOrcamentoId().equals(orcamentoA)));
        assertTrue(response.getOrcamentosVinculados().stream()
                .anyMatch(v -> v.getOrcamentoId().equals(orcamentoB)));
    }

    /** RN-NOVA-16 — Listagem/Kanban expõe orcamentosVinculados por linha, mesmo DTO do Detalhe.
     * `producaoSemVinculo` recebe um `ProducaoProduto` avulso (sem vínculo de orçamento) — necessário
     * porque `ProducaoRepository.buscarIdsOrdenados()` tem um achado pré-existente (fora do escopo
     * desta tarefa, registrado em DECISOES_V0.8.3.md) que exclui da Listagem qualquer produção sem
     * NENHUM produto; sem isso o teste ficaria confundindo esse bug com o comportamento de
     * RN-NOVA-16. */
    @Test
    void listagemPopulaOrcamentosVinculadosPorProducao() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoA = criarOrcamentoComProduto(produtoA, 2, new BigDecimal("10.00"));
        UUID orcamentoB = criarOrcamentoComProduto(produtoB, 1, new BigDecimal("30.00"));
        Producao producaoComVinculo = novaProducao();
        Producao producaoSemVinculo = novaProducao();
        vincular(orcamentoA, producaoComVinculo);
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producaoSemVinculo).produto(produtoB).quantidade(new BigDecimal("1")).build());

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));

        ProducaoResponse comVinculo = pagina.getContent().stream()
                .filter(p -> p.getId().equals(producaoComVinculo.getId())).findFirst().orElseThrow();
        ProducaoResponse semVinculo = pagina.getContent().stream()
                .filter(p -> p.getId().equals(producaoSemVinculo.getId())).findFirst().orElseThrow();

        assertEquals(1, comVinculo.getOrcamentosVinculados().size());
        assertEquals(orcamentoA, comVinculo.getOrcamentosVinculados().get(0).getOrcamentoId());
        assertTrue(semVinculo.getOrcamentosVinculados().isEmpty());
        // orcamentoB nunca foi vinculado a nenhuma produção — confirma que o batch não "vaza"
        // vínculo de outro orçamento pra dentro da produção errada.
        assertTrue(pagina.getContent().stream()
                .noneMatch(p -> p.getOrcamentosVinculados().stream()
                        .anyMatch(v -> v.getOrcamentoId().equals(orcamentoB))));
    }

    /** RN-NOVA-16, critério 1 — indicador continua aceso mesmo com o orçamento vinculado CANCELADO. */
    @Test
    void listagemMostraVinculoIndependenteDoStatusDoOrcamentoInclusiveCancelado() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 1, new BigDecimal("20.00"));
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        orcamentoService.cancelar(orcamentoId, new AvancaStatusRequest());

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));
        ProducaoResponse resposta = pagina.getContent().stream()
                .filter(p -> p.getId().equals(producao.getId())).findFirst().orElseThrow();

        assertEquals(1, resposta.getOrcamentosVinculados().size(),
                "vínculo continua visível mesmo com o orçamento já CANCELADO — RN-NOVA-16 não filtra por status");
        assertEquals(StatusOrcamento.CANCELADO, resposta.getOrcamentosVinculados().get(0).getStatusOrcamento());
    }

    /** RN-NOVA-16 — findByProducaoIdIn é 1 única query batched, independente do nº de produções/vínculos
     * na página (nunca N+1 do tipo 1 findByProducaoId por linha). */
    @Test
    void findByProducaoIdInUsaUmaUnicaQueryBatchedParaMultiplasProducoes() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        Produto produtoC = novoProduto();
        UUID orcamentoA = criarOrcamentoComProduto(produtoA, 1, new BigDecimal("10.00"));
        UUID orcamentoB = criarOrcamentoComProduto(produtoB, 1, new BigDecimal("10.00"));
        UUID orcamentoC = criarOrcamentoComProduto(produtoC, 1, new BigDecimal("10.00"));
        Producao producaoA = novaProducao();
        Producao producaoB = novaProducao();
        Producao producaoC = novaProducao();
        vincular(orcamentoA, producaoA);
        vincular(orcamentoB, producaoB);
        vincular(orcamentoC, producaoC);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        List<OrcamentoProducao> resultado = orcamentoProducaoRepository.findByProducaoIdIn(
                List.of(producaoA.getId(), producaoB.getId(), producaoC.getId()));

        assertEquals(3, resultado.size());
        assertEquals(1L, stats.getPrepareStatementCount(),
                "3 produções com vínculo devem custar exatamente 1 query batched, não 3 (N+1)");
    }
}
