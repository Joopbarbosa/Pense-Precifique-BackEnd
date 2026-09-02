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
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-ORC-VINC-05 (V0.8.2, #320, P-B020) — criar produção nova já vinculada ao orçamento, numa única
 * operação, sem duplicar a quantidade dos produtos (achado do Passo 0 de P-F004: criar a produção
 * com os itens do orçamento e depois chamar vincularProducao() por cima somaria em dobro).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoCriarProducaoVinculadaIT {

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

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-criar-producao-vinculada-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Criar Produção Vinculada").ativa(true).build());
    }

    /** RN-PROD-VINC-01 exige ficha técnica + rendimento válidos — mesma regra de criarProducao(). */
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

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private CriarProducaoVinculadaRequest requestCriacao(LocalDate dataTerminoPrevista) {
        CriarProducaoVinculadaRequest req = new CriarProducaoVinculadaRequest();
        req.setDataTerminoPrevista(dataTerminoPrevista);
        req.setObservacoes("Criada a partir do orçamento — teste");
        return req;
    }

    /** Caso principal — quantidade correta na produção nova, sem duplicação. */
    @Test
    void criaProducaoComQuantidadeCorretaSemDuplicar() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 7);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(10)));

        assertEquals(1, vinculos.size());
        UUID producaoId = vinculos.get(0).getProducaoId();

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producaoId);
        assertEquals(1, produtos.size(), "não pode ter duplicado a linha do produto");
        assertEquals(0, new BigDecimal("7").compareTo(produtos.get(0).getQuantidade()),
                "quantidade deve ser exatamente a do orçamento, sem soma/duplicação");

        Producao producao = producaoRepository.findById(producaoId).orElseThrow();
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, producao.getEstado());
    }

    /** Vínculo formal gravado — aparece em orcamento_producoes, mesmo contrato de vincularProducao(). */
    @Test
    void gravaVinculoFormalCorretamente() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));

        assertEquals(1, orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).size());
        assertTrue(orcamentoService.buscarPorId(orcamentoId).getProducoesVinculadas().stream()
                .anyMatch(v -> v.getProducaoId().equals(vinculos.get(0).getProducaoId())));
    }

    /** RN-PROD-HIST-01 — histórico ITEM_ADICIONADO gravado com produto/quantidade/origem corretos,
     * mesmo padrão de vincularProducao() (reaproveita adicionarProdutosDeOrcamento()). */
    @Test
    void gravaHistoricoItemAdicionado() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));
        UUID producaoId = vinculos.get(0).getProducaoId();

        List<HistoricoStatusProducao> historico =
                historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producaoId);

        assertTrue(historico.stream().anyMatch(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.STATUS
                        && h.getStatusNovo() == EstadoProducao.AGUARDANDO_INICIO),
                "produção nasce com histórico de status AGUARDANDO_INICIO, mesmo padrão de criarProducao()");

        HistoricoStatusProducao linha = historico.stream()
                .filter(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .findFirst().orElseThrow(() -> new AssertionError("linha ITEM_ADICIONADO não encontrada"));
        assertNotNull(linha.getProduto());
        assertEquals(produto.getId(), linha.getProduto().getId());
        assertEquals(0, new BigDecimal("4").compareTo(linha.getQuantidade()));
        assertNotNull(linha.getReferenciaOrcamento());
        assertEquals(orcamentoId, linha.getReferenciaOrcamento().getId());
    }

    /** RN-ORC-PRAZO-01/RN-ORC-VINC-04 (P-B019) — aviso de estouro de prazo funciona para a produção
     * nova, mesmo cálculo já usado por vincularProducao() (deriva de dataAprovacao/prazoProducaoDias
     * do orçamento e dataTerminoPrevista da produção — nada específico deste endpoint). */
    @Test
    void avisoDeEstouroDePrazoFuncionaParaProducaoNova() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2); // prazoProducaoDias = 5
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO (seta dataAprovacao)

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(20))); // além do prazo prometido (5 dias)

        assertTrue(vinculos.get(0).isEstouroPrazo(), "término da produção nova ultrapassa o prazo prometido ao cliente");
    }

    /** RN-ORC-PRAZO-01 — sem estouro quando a produção nova termina dentro do prazo prometido. */
    @Test
    void semAvisoDeEstouroQuandoDentroDoPrazo() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2); // prazoProducaoDias = 5
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(2)));

        assertFalse(vinculos.get(0).isEstouroPrazo());
    }

    /** Não exige status específico do orçamento — mesmo critério de vincularProducao() (RN-ORC-VINC-01). */
    @Test
    void funcionaComOrcamentoAindaEmRascunho() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 1); // RASCUNHO, nunca avançado

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));

        assertEquals(1, vinculos.size());
    }

    /** Orçamento sem itens (backend não força itens não-vazios em OrcamentoRequest) não pode gerar
     * produção sem produto — CriarProducaoRequest.produtos exige @NotEmpty do lado de criarProducao(),
     * este método precisa da mesma garantia mesmo vindo por um caminho diferente. */
    @Test
    void orcamentoSemItensNaoPermiteCriarProducao() {
        seedUsuarioECliente();
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of());
        req.setSinalAtivo(false);
        UUID orcamentoId = orcamentoService.criar(req).getId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.criarProducaoVinculada(orcamentoId, requestCriacao(LocalDate.now().plusDays(5))));
        assertTrue(ex.getMessage().toLowerCase().contains("itens"));
    }

    /** Chamar 2 vezes cria 2 produções distintas (não é idempotente — "criar" é sempre um novo recurso,
     * diferente de vincularProducao() que reaproveita a mesma produção existente). */
    @Test
    void chamarDuasVezesCriaDuasProducoesDistintas() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);

        List<OrcamentoProducaoResponse> primeira = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));
        List<OrcamentoProducaoResponse> segunda = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));

        assertEquals(1, primeira.size());
        assertEquals(2, segunda.size());
        UUID producaoId1 = primeira.get(0).getProducaoId();
        UUID producaoId2 = segunda.stream().map(OrcamentoProducaoResponse::getProducaoId)
                .filter(id -> !id.equals(producaoId1)).findFirst().orElseThrow();
        assertNotEquals(producaoId1, producaoId2);
        assertEquals(2, orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).size());
    }

    private UUID criarOrcamentoComDoisProdutos(Produto produtoA, int quantidadeA, Produto produtoB, int quantidadeB) {
        OrcamentoItemRequest itemA = new OrcamentoItemRequest();
        itemA.setProdutoId(produtoA.getId());
        itemA.setMargemAplicada(BigDecimal.ZERO);
        itemA.setPrecoUnitario(new BigDecimal("50.00"));
        itemA.setQuantidade(quantidadeA);

        OrcamentoItemRequest itemB = new OrcamentoItemRequest();
        itemB.setProdutoId(produtoB.getId());
        itemB.setMargemAplicada(BigDecimal.ZERO);
        itemB.setPrecoUnitario(new BigDecimal("50.00"));
        itemB.setQuantidade(quantidadeB);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(itemA, itemB));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    /** RN-NOVA-13 (V0.8.3, #375+308) — produtoIds restringe a produção nova a só os itens marcados. */
    @Test
    void produtoIdsRestringeAosItensSelecionados() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoId = criarOrcamentoComDoisProdutos(produtoA, 3, produtoB, 5);

        CriarProducaoVinculadaRequest request = requestCriacao(LocalDate.now().plusDays(5));
        request.setProdutoIds(List.of(produtoA.getId()));

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(orcamentoId, request);
        UUID producaoId = vinculos.get(0).getProducaoId();

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producaoId);
        assertEquals(1, produtos.size(), "só o produto selecionado deve entrar na produção");
        assertEquals(produtoA.getId(), produtos.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("3").compareTo(produtos.get(0).getQuantidade()));
    }

    /** RN-NOVA-13 — produtoIds nulo/ausente preserva o comportamento padrão (todos os itens),
     * mesmo consumidor usado hoje por ModalVincularProducao/modoCriarNova. */
    @Test
    void produtoIdsAusenteMantemComportamentoPadraoComMultiplosProdutos() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoId = criarOrcamentoComDoisProdutos(produtoA, 2, produtoB, 6);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.criarProducaoVinculada(
                orcamentoId, requestCriacao(LocalDate.now().plusDays(5)));
        UUID producaoId = vinculos.get(0).getProducaoId();

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producaoId);
        assertEquals(2, produtos.size(), "sem produtoIds, todos os itens do orçamento entram — comportamento inalterado");
    }

    /** RN-NOVA-13 — lista vazia explícita é erro de negócio, nunca silenciosamente "todos os itens". */
    @Test
    void produtoIdsVazioLancaBusinessException() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 2);

        CriarProducaoVinculadaRequest request = requestCriacao(LocalDate.now().plusDays(5));
        request.setProdutoIds(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.criarProducaoVinculada(orcamentoId, request));
        assertTrue(ex.getMessage().toLowerCase().contains("selecione"));
    }
}
