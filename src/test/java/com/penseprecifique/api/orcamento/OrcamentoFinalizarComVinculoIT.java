package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
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
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-19/20 (V0.8.3, #375+308, P-B004) — bloqueio de `EM_PRODUCAO → FINALIZADO` por vínculo
 * ativo (não-terminal) e aviso de vínculo órfão (terminal-cancelado). Cobre os 2 casos numéricos do
 * prompt de execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoFinalizarComVinculoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-finalizar-vinculo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Finalizar Vínculo").ativa(true).build());
    }

    /** Estoque sempre suficiente por padrão — isola o bloqueio/aviso de vínculo do de estoque. */
    private Produto novoProduto(int numero, BigDecimal estoqueAtual) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    /** RASCUNHO -> ENVIADO -> APROVADO -> EM_PRODUCAO, sem vincular (RN-NOVA-6 removida em P-B017). */
    private UUID criarOrcamentoAteEmProducao(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        // temPrazoProducao=true + prazoProducaoDias preenchido — desabilita deliberadamente o
        // atalho de aprovação direta (RN-NOVA-2, elegivelParaAtalhoAprovacaoDireta() exige
        // prazoProducaoDias == null); sem isso, "ENVIADO -> APROVADO" abaixo pularia direto pra
        // FINALIZADO, nunca passando por EM_PRODUCAO — RN-NOVA-19 é escopada só à transição
        // literal EM_PRODUCAO -> FINALIZADO, não ao atalho.
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));

        UUID orcamentoId = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // ENVIADO -> APROVADO
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // APROVADO -> EM_PRODUCAO
        return orcamentoId;
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** vincularProducao() só aceita produção AGUARDANDO_INICIO (adicionarProdutosDeOrcamento) —
     * a produção precisa nascer nesse estado, ser vinculada, e só DEPOIS transicionar pro estado
     * que o teste quer simular (a ordem inversa faz o próprio vincular falhar). */
    private Producao criarProducaoVinculada(UUID orcamentoId, EstadoProducao estadoFinal) {
        Producao producao = producaoRepository.save(
                Producao.builder().usuario(usuario).numero(proximoNumeroProducao++).build());
        vincular(orcamentoId, producao);
        producao.setEstado(estadoFinal);
        return producaoRepository.save(producao);
    }

    /** RN-NOVA-19 — 1 vínculo não-terminal (EM_ANDAMENTO) bloqueia, mensagem cita PRD-N + estado. */
    @Test
    void bloqueiaComUmVinculoNaoTerminal() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        Producao producao = criarProducaoVinculada(orcamentoId, EstadoProducao.EM_ANDAMENTO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        String identificador = IdentificadorFormatter.formatar("PRD", producao.getNumero());
        assertTrue(ex.getMessage().contains(identificador), "mensagem deve citar o identificador PRD-N");
        assertTrue(ex.getMessage().contains("EM_ANDAMENTO"), "mensagem deve citar o estado atual");
        assertTrue(ex.getMessage().contains("ainda não foi finalizada"));

        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        assertEquals(StatusOrcamento.EM_PRODUCAO, detalhe.getStatus(), "não deve ter avançado");
    }

    /** RN-NOVA-19 — 2+ vínculos não-terminais simultâneos: todos precisam estar FINALIZADA, a
     * mensagem lista as 2 pendentes (plural), não só uma. */
    @Test
    void bloqueiaComMultiplosVinculosNaoTerminaisSimultaneos() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        Producao emAndamento = criarProducaoVinculada(orcamentoId, EstadoProducao.EM_ANDAMENTO);
        Producao travada = criarProducaoVinculada(orcamentoId, EstadoProducao.TRAVADA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().contains(IdentificadorFormatter.formatar("PRD", emAndamento.getNumero())));
        assertTrue(ex.getMessage().contains(IdentificadorFormatter.formatar("PRD", travada.getNumero())));
        assertTrue(ex.getMessage().contains("EM_ANDAMENTO"));
        assertTrue(ex.getMessage().contains("TRAVADA"));
        assertTrue(ex.getMessage().contains("Produções"), "plural quando há mais de uma pendente");
    }

    /** RN-NOVA-19 — mesmo com 1 dos 2 vínculos já FINALIZADA (caso feliz parcial), o outro ainda
     * não-terminal continua bloqueando — não basta 1 dos 2 estar pronto. */
    @Test
    void bloqueiaQuandoApenasUmDosDoisVinculosEstaFinalizado() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        criarProducaoVinculada(orcamentoId, EstadoProducao.FINALIZADA);
        Producao travada = criarProducaoVinculada(orcamentoId, EstadoProducao.TRAVADA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().contains(IdentificadorFormatter.formatar("PRD", travada.getNumero())));
        assertTrue(ex.getMessage().contains("TRAVADA"));
    }

    /** RN-NOVA-19 — vínculo único e FINALIZADA (caso feliz) não bloqueia, finaliza normalmente. */
    @Test
    void naoBloqueiaQuandoVinculoEstaFinalizado() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        criarProducaoVinculada(orcamentoId, EstadoProducao.FINALIZADA);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }

    /** RN-NOVA-20 — vínculo órfão (CANCELADA) não bloqueia, mas gera aviso; sem confirmação, nada
     * é persistido. */
    @Test
    void vinculoOrfaoGeraAvisoSemBloquearENaoPersisteSemConfirmacao() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        Producao cancelada = criarProducaoVinculada(orcamentoId, EstadoProducao.CANCELADA);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertTrue(aviso.getAvisos() == null || aviso.getAvisos().isEmpty(), "sem aviso de estoque neste cenário");
        assertEquals(1, aviso.getVinculosOrfaos().size());
        assertEquals(IdentificadorFormatter.formatar("PRD", cancelada.getNumero()),
                aviso.getVinculosOrfaos().get(0).getIdentificador());
        assertEquals(EstadoProducao.CANCELADA, aviso.getVinculosOrfaos().get(0).getEstado());

        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        assertEquals(StatusOrcamento.EM_PRODUCAO, detalhe.getStatus(), "nada persiste na 1ª chamada");
    }

    /** RN-NOVA-20 — confirmarVinculosOrfaos=true na 2ª chamada finaliza de verdade. */
    @Test
    void confirmarVinculosOrfaosNaSegundaChamadaFinaliza() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        criarProducaoVinculada(orcamentoId, EstadoProducao.NAO_REALIZADA);

        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()); // 1ª chamada, só aviso

        AvancaStatusRequest confirmar = new AvancaStatusRequest();
        confirmar.setConfirmarVinculosOrfaos(true);
        Object resultado = orcamentoService.avancarStatus(orcamentoId, confirmar);

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }

    /** RN-NOVA-20 — aviso de vínculo órfão coexiste com aviso de estoque negativo na mesma resposta,
     * sem um suprimir o outro. */
    @Test
    void avisoDeVinculoOrfaoCoexisteComAvisoDeEstoqueNegativo() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("3")); // estoque insuficiente pra qtd 10
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 10);
        criarProducaoVinculada(orcamentoId, EstadoProducao.CANCELADA);

        Object resultado = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size(), "aviso de estoque presente");
        assertEquals(1, aviso.getVinculosOrfaos().size(), "aviso de vínculo órfão presente ao mesmo tempo");

        // confirma só o estoque, sem confirmar o vínculo órfão — ainda deve devolver aviso (não finaliza)
        AvancaStatusRequest confirmaSoEstoque = new AvancaStatusRequest();
        confirmaSoEstoque.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));
        Object resultado2 = orcamentoService.avancarStatus(orcamentoId, confirmaSoEstoque);
        ConfirmacaoEstoqueNegativoResponse aviso2 = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado2);
        assertTrue(aviso2.getAvisos() == null || aviso2.getAvisos().isEmpty(), "estoque já confirmado, sem novo aviso");
        assertEquals(1, aviso2.getVinculosOrfaos().size(), "vínculo órfão ainda pendente, não suprimido pela confirmação de estoque");

        // confirma os dois — finaliza de verdade
        AvancaStatusRequest confirmaTudo = new AvancaStatusRequest();
        confirmaTudo.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));
        confirmaTudo.setConfirmarVinculosOrfaos(true);
        Object resultado3 = orcamentoService.avancarStatus(orcamentoId, confirmaTudo);
        assertInstanceOf(OrcamentoDetalheResponse.class, resultado3);
    }

    /** Exemplo numérico do prompt (caso 1) — vínculos A=FINALIZADA + B=CANCELADA: RN-NOVA-19 não
     * bloqueia (nenhum não-terminal), RN-NOVA-20 avisa só sobre B, confirma e finaliza. */
    @Test
    void exemploNumerico1_FinalizadaEcancelada_AvisaSoSobreOrfaEFinaliza() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        criarProducaoVinculada(orcamentoId, EstadoProducao.FINALIZADA);
        Producao producaoB = criarProducaoVinculada(orcamentoId, EstadoProducao.CANCELADA);

        Object primeira = orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, primeira);
        assertEquals(1, aviso.getVinculosOrfaos().size());
        assertEquals(producaoB.getId(), aviso.getVinculosOrfaos().get(0).getId());

        AvancaStatusRequest confirmar = new AvancaStatusRequest();
        confirmar.setConfirmarVinculosOrfaos(true);
        Object segunda = orcamentoService.avancarStatus(orcamentoId, confirmar);
        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, segunda);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }

    /** Exemplo numérico do prompt (caso 2) — mesmo cenário, mas B=EM_ANDAMENTO: RN-NOVA-19 bloqueia,
     * RN-NOVA-20 nem chega a ser avaliada. */
    @Test
    void exemploNumerico2_FinalizadaEEmAndamento_Bloqueia() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, new BigDecimal("100"));
        UUID orcamentoId = criarOrcamentoAteEmProducao(produto, 5);
        criarProducaoVinculada(orcamentoId, EstadoProducao.FINALIZADA);
        Producao producaoB = criarProducaoVinculada(orcamentoId, EstadoProducao.EM_ANDAMENTO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().contains(IdentificadorFormatter.formatar("PRD", producaoB.getNumero())));
        assertTrue(ex.getMessage().contains("EM_ANDAMENTO"));
    }
}
