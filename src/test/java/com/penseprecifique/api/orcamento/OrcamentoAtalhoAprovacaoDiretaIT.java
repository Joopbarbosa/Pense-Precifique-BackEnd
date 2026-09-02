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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-B006/RN-NOVA-2/RN-NOVA-3 (V0.8.2) — RN-NOVA-3 substitui a obrigatoriedade incondicional de
 * ORC-018 por prazo condicional a {@code temPrazoProducao}; RN-NOVA-2 é o atalho ENVIADO→FINALIZADO
 * (sinal inativo + sem prazo de produção + estoque suficiente pula
 * AGUARDANDO_SINAL/SINAL_PAGO/EM_PRODUCAO), exceção deliberada ao invariante de fluxo linear de
 * ORC-005. Casos 1/2 cobrem RN-NOVA-3; Casos 3-7 cobrem RN-NOVA-2 (elegibilidade + reaproveitamento
 * da confirmação de estoque negativo já usada em EM_PRODUCAO→FINALIZADO, ver
 * OrcamentoRn052EstoqueNegativoIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoAtalhoAprovacaoDiretaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-atalho-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Atalho").ativa(true).build());
    }

    private Produto novoProduto(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private OrcamentoRequest montarRequest(Produto produto, int quantidade, boolean sinalAtivo,
                                            boolean temPrazoProducao, Integer prazoProducaoDias) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(temPrazoProducao);
        req.setPrazoProducaoDias(prazoProducaoDias);
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) {
            req.setPercentualSinal(new BigDecimal("30"));
        }
        req.setItens(List.of(item));
        return req;
    }

    private UUID criarEEnviar(OrcamentoRequest req) {
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO
        return id;
    }

    @Test
    void criarSemPrazoAceitaComPrazoNulo() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sem Prazo", 1, new BigDecimal("100"), true);

        OrcamentoDetalheResponse resposta = orcamentoService.criar(montarRequest(produto, 1, false, false, null));

        assertNull(resposta.getPrazoProducaoDias());
    }

    @Test
    void criarComPrazoSimSemDiasRejeitaCom400() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Com Prazo", 2, new BigDecimal("100"), true);

        assertThrows(BusinessException.class,
                () -> orcamentoService.criar(montarRequest(produto, 1, false, true, null)));
    }

    @Test
    void atalhoCompletoPulaDiretoParaFinalizado() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Atalho", 3, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
        assertNotNull(detalhe.getDataAprovacao(), "ORC-019 — data de aprovação registrada mesmo pulando o status APROVADO persistido");

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90").compareTo(atualizado.getEstoqueAtual()));
    }

    @Test
    void atalhoNaoHabilitadoPorSinalAtivoSegueFluxoNormal() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sinal", 4, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, true, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(inalterado.getEstoqueAtual()), "atalho não deve baixar estoque");
    }

    @Test
    void atalhoNaoHabilitadoPorTerPrazoSegueFluxoNormal() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Prazo", 5, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, true, 5));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());
    }

    @Test
    void atalhoNaoHabilitadoPorEstoqueInsuficienteSegueFluxoNormalSemErro() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sem Estoque", 6, new BigDecimal("2"), false);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(inalterado.getEstoqueAtual()));
    }

    @Test
    void atalhoComEstoqueNegativoReaproveitaConfirmacaoExistente() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Negativo Permitido", 7, new BigDecimal("3"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        ConfirmacaoEstoqueNegativoResponse aviso = assertInstanceOf(ConfirmacaoEstoqueNegativoResponse.class, resultado);
        assertEquals(1, aviso.getAvisos().size());
        assertEquals(produto.getId(), aviso.getAvisos().get(0).getComponenteId());

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3").compareTo(inalterado.getEstoqueAtual()), "não deve ter baixado sem confirmação");

        AvancaStatusRequest confirmar = new AvancaStatusRequest();
        confirmar.setConfirmarEstoqueNegativoProdutoIds(List.of(produto.getId()));
        Object resultadoConfirmado = orcamentoService.avancarStatus(id, confirmar);

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultadoConfirmado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("-7").compareTo(atualizado.getEstoqueAtual()));
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** vincularProducao() exige ficha técnica + rendimento válidos (RN-PROD-VINC-01) — o
     * novoProduto() padrão deste arquivo não tem isso, porque nenhum teste pré-existente aqui
     * precisava vincular produção. Helper próprio só para os testes de vínculo desta tarefa. */
    private Produto produtoComFichaTecnica(String nome, int numero, BigDecimal estoqueAtual, boolean permitirEstoqueNegativo) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(estoqueAtual).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    /** vincularProducao() só aceita produção AGUARDANDO_INICIO (adicionarProdutosDeOrcamento) — a
     * produção precisa nascer nesse estado, ser vinculada, e só DEPOIS transicionar pro estado que
     * o teste quer simular (a ordem inversa faz o próprio vincular falhar). */
    private Producao criarProducaoVinculada(UUID orcamentoId, EstadoProducao estadoFinal) {
        Producao producao = producaoRepository.save(
                Producao.builder().usuario(usuario).numero(proximoNumeroProducao++).build());
        vincular(orcamentoId, producao);
        if (estadoFinal != EstadoProducao.AGUARDANDO_INICIO) {
            producao.setEstado(estadoFinal);
            producao = producaoRepository.save(producao);
        }
        return producao;
    }

    /**
     * RN-NOVA-19 (achado de P-A005, corrigido em P-B005, V0.8.3, #379) — vínculo com produção
     * não-terminal desqualifica o atalho, mesmo com sinal inativo e sem prazo (as 2 condições
     * antigas continuariam batendo). Exemplo numérico do prompt de execução: checkpoint vincula
     * Produção X (RN-NOVA-13) enquanto ainda RASCUNHO, antes de avançar pra ENVIADO.
     */
    @Test
    void vinculoComProducaoAguardandoInicioDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado", 8, new BigDecimal("100"), true);
        UUID id = orcamentoService.criar(montarRequest(produto, 10, false, false, null)).getId();
        criarProducaoVinculada(id, EstadoProducao.AGUARDANDO_INICIO);
        orcamentoService.avancarStatus(id, new AvancaStatusRequest()); // RASCUNHO -> ENVIADO

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus(),
                "vínculo não-terminal desqualifica o atalho — segue fluxo normal, não pula pra FINALIZADO");

        Produto inalterado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(inalterado.getEstoqueAtual()), "atalho não rodou, nada foi baixado");
    }

    /** Mesmo critério para EM_ANDAMENTO (não só AGUARDANDO_INICIO). */
    @Test
    void vinculoComProducaoEmAndamentoDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado EM_ANDAMENTO", 9, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        criarProducaoVinculada(id, EstadoProducao.EM_ANDAMENTO);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());
    }

    /** Mesmo critério para TRAVADA. */
    @Test
    void vinculoComProducaoTravadaDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado TRAVADA", 10, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        criarProducaoVinculada(id, EstadoProducao.TRAVADA);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.APROVADO, detalhe.getStatus());
    }

    /** RN-NOVA-19, caso feliz: vínculo com produção já FINALIZADA NÃO desqualifica — atalho
     * continua funcionando normalmente (por simetria com RN-NOVA-19, que também não bloqueia aqui). */
    @Test
    void vinculoComProducaoFinalizadaNaoDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado FINALIZADA", 11, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        criarProducaoVinculada(id, EstadoProducao.FINALIZADA);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus(), "vínculo terminal-feliz não desqualifica o atalho");
    }

    /** RN-NOVA-19/20, vínculo órfão: produção CANCELADA também NÃO desqualifica o atalho —
     * decisão deliberada (RN-NOVA-20 é só aviso informativo, o atalho pula esse mecanismo por
     * natureza, mesmo espírito de "produção não é necessária"). */
    @Test
    void vinculoComProducaoCanceladaOrfaNaoDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado CANCELADA", 12, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        criarProducaoVinculada(id, EstadoProducao.CANCELADA);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus(), "vínculo órfão não desqualifica o atalho, deliberadamente");
    }

    /** Mesmo critério para NAO_REALIZADA (outro estado terminal-órfão). */
    @Test
    void vinculoComProducaoNaoRealizadaNaoDesqualificaAtalho() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Vinculado NAO_REALIZADA", 13, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        criarProducaoVinculada(id, EstadoProducao.NAO_REALIZADA);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }

    /** Sem vínculo nenhum — regressão: atalho continua elegível normalmente (já coberto por
     * atalhoCompletoPulaDiretoParaFinalizado, mas reconfirmado aqui explicitamente com
     * orcamentoProducaoRepository ausente de propósito, ver Passo 0 desta tarefa). */
    @Test
    void semVinculoAtalhoContinuaElegivel() {
        seedUsuarioECliente();
        Produto produto = novoProduto("Produto Sem Vinculo Nenhum", 14, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }

    /** Depois de desqualificado o atalho por vínculo ativo, o fluxo normal continua protegido por
     * RN-NOVA-19 até a produção vinculada finalizar — não requer código novo, só confirma a
     * integração entre as duas correções (P-B004 + P-B005). */
    @Test
    void aposDesqualificarAtalhoFluxoNormalRespeitaRnNova19AteProducaoFinalizar() {
        seedUsuarioECliente();
        Produto produto = produtoComFichaTecnica("Produto Fluxo Normal Protegido", 15, new BigDecimal("100"), true);
        UUID id = criarEEnviar(montarRequest(produto, 10, false, false, null));
        Producao producaoX = criarProducaoVinculada(id, EstadoProducao.EM_ANDAMENTO);

        // ENVIADO -> APROVADO (atalho desqualificado)
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        // APROVADO -> EM_PRODUCAO (sinal inativo)
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());

        // EM_PRODUCAO -> FINALIZADO deve ser bloqueado por RN-NOVA-19 (produção ainda EM_ANDAMENTO)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.avancarStatus(id, new AvancaStatusRequest()));
        assertTrue(ex.getMessage().contains("ainda não foi finalizada"));

        producaoX.setEstado(EstadoProducao.FINALIZADA);
        producaoRepository.save(producaoX);

        Object resultado = orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        OrcamentoDetalheResponse detalhe = assertInstanceOf(OrcamentoDetalheResponse.class, resultado);
        assertEquals(StatusOrcamento.FINALIZADO, detalhe.getStatus());
    }
}
