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
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-6 (V0.8.2) — vínculo obrigatório entre Orçamento e Produção antes de EM_PRODUCAO. Cobre
 * os 4 casos do prompt P-B005: bloqueio sem vínculo (Caso 1), avanço liberado com vínculo (Caso 2),
 * N:N com múltiplas produções (Caso 3), mesmo bloqueio no segundo caminho SINAL_PAGO (Caso 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoVincularProducaoIT {

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
                .email("orc-vincular-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Vincular Produção").ativa(true).build());
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

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private UUID criarOrcamento(boolean sinalAtivo) {
        Produto produto = novoProduto();
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("50"));
        }
        return orcamentoService.criar(req).getId();
    }

    /** RASCUNHO -> ENVIADO -> APROVADO (sem sinal, deixa em APROVADO pronto pro caminho direto). */
    private void avancarAteAprovadoSemSinal(UUID orcamentoId) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
    }

    /** RASCUNHO -> ... -> SINAL_PAGO (com sinal). */
    private void avancarAteSinalPago(UUID orcamentoId) {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        AvancaStatusRequest sinalReq = new AvancaStatusRequest();
        sinalReq.setMetodoSinalRecebido(MetodoPagamento.PIX);
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // APROVADO -> AGUARDANDO_SINAL
        orcamentoService.avancarStatus(orcamentoId, sinalReq); // AGUARDANDO_SINAL -> SINAL_PAGO
    }

    /** RN-ORC-VINC-01 (P-B017) — vínculo deixou de ser obrigatório: sem nenhuma produção vinculada, o avanço acontece normalmente. */
    @Test
    void avancarSemVinculoNaoBloqueiaMais() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);
        avancarAteAprovadoSemSinal(orcamentoId);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        assertTrue(resultado instanceof OrcamentoDetalheResponse);
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).isEmpty());
    }

    @Test
    void avancarComVinculoPermiteTransicao() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);
        avancarAteAprovadoSemSinal(orcamentoId);

        Producao producao = novaProducao();
        VincularProducaoRequest vincularReq = new VincularProducaoRequest();
        vincularReq.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, vincularReq);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        assertTrue(resultado instanceof OrcamentoDetalheResponse);
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
    }

    @Test
    void multiplasProducoesVinculadasPersistemSemErroDeUnicidade() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);

        Producao producaoA = novaProducao();
        Producao producaoB = novaProducao();

        VincularProducaoRequest reqA = new VincularProducaoRequest();
        reqA.setProducaoId(producaoA.getId());
        orcamentoService.vincularProducao(orcamentoId, reqA);

        VincularProducaoRequest reqB = new VincularProducaoRequest();
        reqB.setProducaoId(producaoB.getId());
        List<OrcamentoProducaoResponse> vinculos = orcamentoService.vincularProducao(orcamentoId, reqB);

        assertEquals(2, vinculos.size());
        assertEquals(2, orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).size());

        avancarAteAprovadoSemSinal(orcamentoId);
        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
    }

    /** RN-ORC-VINC-01 (P-B017) — mesma ausência de bloqueio no caminho SINAL_PAGO → EM_PRODUCAO. */
    @Test
    void caminhoSinalPagoNaoBloqueiaMaisSemVinculo() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(true);
        avancarAteSinalPago(orcamentoId);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        assertTrue(resultado instanceof OrcamentoDetalheResponse);
        assertEquals(StatusOrcamento.EM_PRODUCAO, ((OrcamentoDetalheResponse) resultado).getStatus());
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).isEmpty());
    }

    /** P-B013/#320 — GET /orcamentos/{id} (buscarPorId) passa a expor os vínculos já existentes. */
    @Test
    void buscarPorIdExpoeProducoesVinculadas() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);

        Producao producaoA = novaProducao();
        Producao producaoB = novaProducao();
        VincularProducaoRequest reqA = new VincularProducaoRequest();
        reqA.setProducaoId(producaoA.getId());
        orcamentoService.vincularProducao(orcamentoId, reqA);
        VincularProducaoRequest reqB = new VincularProducaoRequest();
        reqB.setProducaoId(producaoB.getId());
        orcamentoService.vincularProducao(orcamentoId, reqB);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.buscarPorId(orcamentoId).getProducoesVinculadas();

        assertEquals(2, vinculos.size());
        assertTrue(vinculos.stream().anyMatch(v -> v.getProducaoId().equals(producaoA.getId())));
        assertTrue(vinculos.stream().anyMatch(v -> v.getProducaoId().equals(producaoB.getId())));
        assertTrue(vinculos.stream().allMatch(v -> v.getIdentificadorProducao() != null && v.getId() != null));
    }

    /** P-B013/#320 — sem nenhum vínculo, a lista vem vazia, nunca null. */
    @Test
    void buscarPorIdSemVinculoDevolveListaVazia() {
        seedUsuarioECliente();
        UUID orcamentoId = criarOrcamento(false);

        List<OrcamentoProducaoResponse> vinculos = orcamentoService.buscarPorId(orcamentoId).getProducoesVinculadas();

        assertEquals(0, vinculos.size());
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

    /** RN-PROD-VINC-01 — produto ainda não presente na produção: cria ProducaoProduto novo. */
    @Test
    void vincularProducaoAdicionaProdutoNovoNaProducao() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 3);
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        assertEquals(1, produtos.size());
        assertEquals(produto.getId(), produtos.get(0).getProduto().getId());
        assertEquals(0, new BigDecimal("3").compareTo(produtos.get(0).getQuantidade()));
    }

    /** RN-PROD-VINC-01 — produto já presente na produção: soma quantidade, nunca duplica linha. */
    @Test
    void vincularProducaoSomaQuantidadeQuandoProdutoJaExisteNaProducao() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        Producao producao = novaProducao();
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(new BigDecimal("2")).build());

        UUID orcamentoId = criarOrcamentoComProduto(produto, 5);
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        assertEquals(1, produtos.size(), "não pode duplicar a linha do produto já existente");
        assertEquals(0, new BigDecimal("7").compareTo(produtos.get(0).getQuantidade()));
    }

    /** RN-PROD-VINC-02 — produção fora de AGUARDANDO_INICIO bloqueia o vínculo, sem gravar nada. */
    @Test
    void vincularProducaoEmProducaoForaDeAguardandoInicioBloqueia() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 1);
        Producao producao = producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.EM_ANDAMENTO).build());

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.vincularProducao(orcamentoId, req));
        assertTrue(ex.getMessage().toLowerCase().contains("já começou"));

        assertTrue(orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamentoId, producao.getId()).isEmpty(),
                "vínculo não pode ser gravado quando a produção está bloqueada");
        assertEquals(0, producaoProdutoRepository.findByProducaoId(producao.getId()).size());
    }

    /** RN-PROD-HIST-01 — vincular grava histórico ITEM_ADICIONADO com produto/quantidade/origem corretos. */
    @Test
    void vincularProducaoGravaHistoricoItemAdicionado() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4);
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);

        List<HistoricoStatusProducao> historico =
                historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producao.getId());
        HistoricoStatusProducao linha = historico.stream()
                .filter(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .findFirst().orElseThrow(() -> new AssertionError("linha ITEM_ADICIONADO não encontrada"));

        assertNotNull(linha.getProduto());
        assertEquals(produto.getId(), linha.getProduto().getId());
        assertEquals(0, new BigDecimal("4").compareTo(linha.getQuantidade()));
        assertNotNull(linha.getReferenciaOrcamento());
        assertEquals(orcamentoId, linha.getReferenciaOrcamento().getId());
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

    /**
     * P-B017 (#320, achado de P-B015) — vincular de novo à mesma produção depois de adicionar um item
     * novo ao orçamento (ainda em RASCUNHO, único status que permite editar() — ORC-004) sincroniza só
     * o item novo, sem re-somar o que já tinha entrado no 1º vínculo.
     */
    @Test
    void vincularDeNovoAdicionaSoItemNovoSemReSomarOQueJaFoiSincronizado() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoId = orcamentoService.criar(requestComItens(List.of(itemDe(produtoA, 3)))).getId();
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);

        orcamentoService.editar(orcamentoId, requestComItens(List.of(itemDe(produtoA, 3), itemDe(produtoB, 2))));
        orcamentoService.vincularProducao(orcamentoId, req);

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        assertEquals(2, produtos.size());
        ProducaoProduto pa = produtos.stream().filter(p -> p.getProduto().getId().equals(produtoA.getId()))
                .findFirst().orElseThrow();
        ProducaoProduto pb = produtos.stream().filter(p -> p.getProduto().getId().equals(produtoB.getId()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(pa.getQuantidade()),
                "produtoA não pode ter sido re-somado no 2º vínculo");
        assertEquals(0, new BigDecimal("2").compareTo(pb.getQuantidade()));
    }

    /** P-B017 (#320) — quantidade de um item já sincronizado aumenta: só o delta (aumento) é somado de novo. */
    @Test
    void vincularDeNovoSomaSoDeltaQuandoQuantidadeDoItemAumenta() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        UUID orcamentoId = orcamentoService.criar(requestComItens(List.of(itemDe(produtoA, 3)))).getId();
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);

        orcamentoService.editar(orcamentoId, requestComItens(List.of(itemDe(produtoA, 5))));
        orcamentoService.vincularProducao(orcamentoId, req);

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        assertEquals(1, produtos.size());
        assertEquals(0, new BigDecimal("5").compareTo(produtos.get(0).getQuantidade()));

        List<HistoricoStatusProducao> itensAdicionados = historicoStatusProducaoRepository
                .findByProducaoIdOrderByDataTransicaoAsc(producao.getId()).stream()
                .filter(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .toList();
        assertEquals(2, itensAdicionados.size(),
                "2 linhas de histórico: 3 na 1ª sincronização, 2 (delta) na 2ª");
    }

    /** P-B017 (#320) — vincular de novo sem nenhuma mudança no orçamento não gera histórico nem soma de novo. */
    @Test
    void vincularDeNovoSemMudancaNoOrcamentoNaoGeraHistoricoNovo() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        UUID orcamentoId = orcamentoService.criar(requestComItens(List.of(itemDe(produtoA, 3)))).getId();
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
        orcamentoService.vincularProducao(orcamentoId, req);

        List<HistoricoStatusProducao> itensAdicionados = historicoStatusProducaoRepository
                .findByProducaoIdOrderByDataTransicaoAsc(producao.getId()).stream()
                .filter(h -> h.getTipoEvento() == TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .toList();
        assertEquals(1, itensAdicionados.size());

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        assertEquals(0, new BigDecimal("3").compareTo(produtos.get(0).getQuantidade()));
    }
}
