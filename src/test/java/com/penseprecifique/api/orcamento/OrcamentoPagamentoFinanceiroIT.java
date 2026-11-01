package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.financeiro.Lancamento;
import com.penseprecifique.api.financeiro.LancamentoFinanceiroService;
import com.penseprecifique.api.financeiro.LancamentoRepository;
import com.penseprecifique.api.financeiro.OrcamentoDaParcela;
import com.penseprecifique.api.financeiro.PapelParcela;
import com.penseprecifique.api.financeiro.ParcelaOrcamentoResponse;
import com.penseprecifique.api.financeiro.SituacaoLancamento;
import com.penseprecifique.api.financeiro.SituacaoParcela;
import com.penseprecifique.api.financeiro.SubtipoLancamento;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.testsupport.MetodosPagamentoTeste;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1174 / PP-522 (RN-NOVA-10/11/12/38/40/41/43/51; CEN-NOVO-17 a 21, 44, 46, 47, 48): o passo Pagamento no fluxo real do orçamento,
 * as parcelas geradas pelo LancamentoFinanceiroService, o "Recebi" nas duas telas e o cancelamento com estorno e multa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoPagamentoFinanceiroIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired LancamentoFinanceiroService financeiro;
    @Autowired LancamentoRepository lancamentos;
    @Autowired ReciboPagamentoRepository recibos;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private Produto produto;
    private MetodoPagamentoConfiguravel pix;
    private MetodoPagamentoConfiguravel dinheiro;

    @BeforeEach
    void preparar() {
        usuario = usuarioRepository.save(Usuario.builder().email("orc-pag-fin-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Cliente").ativa(true).build());
        pix = MetodosPagamentoTeste.pix(metodos, usuario);
        dinheiro = MetodosPagamentoTeste.criar(metodos, usuario, TipoMetodoPagamento.DINHEIRO, true);
        UnidadeMedida un = unidadeMedidaRepository.findAll().stream().findFirst().orElseGet(() ->
                unidadeMedidaRepository.save(UnidadeMedida.builder().sigla("g").nome("grama").build()));
        produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Produto").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("300.00")).build());
        Insumo insumo = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Insumo").marca("X").unidadeMedida(un)
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(produto).insumo(insumo).quantidade(BigDecimal.ONE).build());
    }

    // --------------------------------------------------------------------------------------------- apoio

    /** Orçamento ENVIADO de total R$ 300,00, com prazo de produção (não elegível ao atalho). */
    private UUID enviado(boolean comPrazo) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("300.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setTemPrazoProducao(comPrazo);
        if (comPrazo) req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        return id;
    }

    private PagamentoOrcamentoRequest pagamento(boolean sinal, String valorSinal) {
        PagamentoOrcamentoRequest p = new PagamentoOrcamentoRequest();
        p.setMetodoPagamentoId(pix.getId());
        p.setTemSinal(sinal);
        if (sinal) {
            p.setTipoSinal(PagamentoOrcamentoRequest.TipoSinal.VALOR);
            p.setValorSinal(new BigDecimal(valorSinal));
            p.setSinalAte(LocalDate.now().plusDays(5));
        }
        p.setVencimentoSaldo(LocalDate.now().plusDays(30));
        return p;
    }

    private void aprovar(UUID id, PagamentoOrcamentoRequest p) {
        AvancaStatusRequest r = new AvancaStatusRequest();
        r.setPagamento(p);
        orcamentoService.avancarStatus(id, r);
    }

    private void avancar(UUID id) {
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
    }

    private void receberSinal(UUID id, UUID metodo) {
        AvancaStatusRequest r = new AvancaStatusRequest();
        r.setMetodoSinalRecebidoId(metodo);
        orcamentoService.avancarStatus(id, r);
    }

    private void vincularProducao(UUID id) {
        Producao producao = producaoRepository.save(Producao.builder().usuario(usuario).numero(1).build());
        VincularProducaoRequest v = new VincularProducaoRequest();
        v.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(id, v);
    }

    private void finalizarProducoes() {
        producaoRepository.findAll().stream().filter(p -> p.getUsuario().getId().equals(usuario.getId())).forEach(p -> {
            p.setEstado(com.penseprecifique.api.shared.domain.enums.EstadoProducao.FINALIZADA);
            producaoRepository.save(p);
        });
    }

    private List<ParcelaOrcamentoResponse> parcelas(UUID id) {
        return orcamentoService.buscarPorId(id).getParcelas();
    }

    private ParcelaOrcamentoResponse parcela(UUID id, PapelParcela papel) {
        return parcelas(id).stream().filter(p -> p.papel() == papel).findFirst().orElseThrow();
    }

    private StatusOrcamento status(UUID id) {
        return orcamentoService.buscarPorId(id).getStatus();
    }

    private Lancamento lancamentoDoOrcamento(UUID id) {
        return lancamentos.findByOrcamentoIdAndUsuarioIdAndSubtipoIsNull(id, usuario.getId()).orElseThrow();
    }

    private void assertValor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " mas foi " + real);
    }

    private void aprovarComSinalAteEmProducao(UUID id, String sinal) {
        aprovar(id, pagamento(true, sinal));
        avancar(id);                 // APROVADO -> AGUARDANDO_SINAL
        receberSinal(id, pix.getId()); // AGUARDANDO_SINAL -> SINAL_PAGO
        vincularProducao(id);
        avancar(id);                 // SINAL_PAGO -> EM_PRODUCAO
    }

    // --------------------------------------------------------------------------------------------- aprovação (CEN-44, 47)

    @Test
    void aprovarSemPagamentoEBloqueioENadaMuda() {
        UUID id = enviado(true);
        BusinessException e = assertThrows(BusinessException.class, () -> orcamentoService.avancarStatus(id, new AvancaStatusRequest()));
        assertEquals("Informe como será o pagamento para aprovar.", e.getMessage());
        assertEquals(StatusOrcamento.ENVIADO, status(id));
        assertTrue(parcelas(id).isEmpty());
        assertTrue(lancamentos.findByOrcamentoIdAndUsuarioIdAndSubtipoIsNull(id, usuario.getId()).isEmpty());
    }

    @Test
    void aprovarComSinalGravaPagamentoEGeraSinalESaldo() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        OrcamentoDetalheResponse d = orcamentoService.buscarPorId(id);
        assertEquals(StatusOrcamento.APROVADO, d.getStatus());
        assertTrue(d.isSinalAtivo());
        assertValor("90.00", d.getValorSinal());
        assertEquals(pix.getId(), d.getMetodoPagamento().id());
        assertEquals(2, d.getParcelas().size());
        ParcelaOrcamentoResponse sinal = parcela(id, PapelParcela.SINAL);
        ParcelaOrcamentoResponse saldo = parcela(id, PapelParcela.SALDO);
        assertValor("90.00", sinal.valor());
        assertEquals(LocalDate.now().plusDays(5), sinal.vencimento());
        assertValor("210.00", saldo.valor());
        assertEquals(LocalDate.now().plusDays(30), saldo.vencimento());
        assertEquals(SituacaoParcela.EM_ABERTO, sinal.situacao());
        assertValor("300.00", lancamentoDoOrcamento(id).getValorTotal());
    }

    @Test
    void sinalDeCemPorCentoGeraSoAParcelaSinalEVaiAPagoAoReceber() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "300.00"));
        assertEquals(1, parcelas(id).size());
        assertEquals(PapelParcela.SINAL, parcelas(id).get(0).papel());
        avancar(id); // APROVADO -> AGUARDANDO_SINAL
        receberSinal(id, pix.getId());
        OrcamentoDetalheResponse d = orcamentoService.buscarPorId(id);
        assertEquals(StatusOrcamento.PAGO, d.getStatus());
        assertEquals(SituacaoParcela.BAIXADA, parcela(id, PapelParcela.SINAL).situacao());
        assertNotNull(orcamentos.findById(id).orElseThrow().getDataPagamento());
        assertTrue(recibos.findByOrcamentoId(id).isPresent());
    }

    @Test
    void semSinalComAtalhoGeraUmaParcelaTotalEmAberto() {
        UUID id = enviado(false);
        aprovar(id, pagamento(false, null));
        assertEquals(StatusOrcamento.FINALIZADO, status(id));
        assertEquals(1, parcelas(id).size());
        ParcelaOrcamentoResponse total = parcela(id, PapelParcela.TOTAL);
        assertValor("300.00", total.valor());
        assertEquals(SituacaoParcela.EM_ABERTO, total.situacao());
    }

    @Test
    void comSinalNaoUsaOAtalhoMesmoSemPrazo() {
        UUID id = enviado(false);
        aprovar(id, pagamento(true, "100.00"));
        assertEquals(StatusOrcamento.APROVADO, status(id));
    }

    @Test
    void pagamentoInvalidoNaoGravaNadaENaoMudaOStatus() {
        UUID id = enviado(true);
        PagamentoOrcamentoRequest p = pagamento(true, "400.00");
        assertThrows(BusinessException.class, () -> aprovar(id, p));
        assertEquals(StatusOrcamento.ENVIADO, status(id));
        assertFalse(orcamentos.findById(id).orElseThrow().getSinalAtivo());
        assertTrue(parcelas(id).isEmpty());
    }

    // --------------------------------------------------------------------------------------------- "Recebi" (CEN-46, 48)

    @Test
    void fluxoCompletoComDuasFormasBaixaAsParcelasEOReciboUsaAFormaRecebida() {
        UUID id = enviado(true);
        aprovarComSinalAteEmProducao(id, "90.00"); // sinal em Pix
        assertEquals(StatusOrcamento.EM_PRODUCAO, status(id));
        ParcelaOrcamentoResponse sinal = parcela(id, PapelParcela.SINAL);
        assertEquals(SituacaoParcela.BAIXADA, sinal.situacao());
        assertEquals("PIX", sinal.metodoPagamentoNome());
        finalizarProducoes();
        avancar(id); // EM_PRODUCAO -> FINALIZADO
        assertEquals(StatusOrcamento.FINALIZADO, status(id));
        AvancaStatusRequest recebi = new AvancaStatusRequest();
        recebi.setMetodoRecebidoId(dinheiro.getId()); // saldo recebido em dinheiro
        orcamentoService.avancarStatus(id, recebi);
        assertEquals(StatusOrcamento.PAGO, status(id));
        ParcelaOrcamentoResponse saldo = parcela(id, PapelParcela.SALDO);
        assertEquals(SituacaoParcela.BAIXADA, saldo.situacao());
        assertEquals("DINHEIRO", saldo.metodoPagamentoNome());
        var recibo = recibos.findByOrcamentoId(id).orElseThrow();
        assertEquals(dinheiro.getId(), recibo.getMetodoPagamento().getId());
        assertValor("90.00", recibo.getValorSinalPago());
        assertValor("210.00", recibo.getValorRestantePago());
        assertEquals(pix.getId(), orcamentos.findById(id).orElseThrow().getMetodoSinalRecebido().getId());
    }

    @Test
    void semFormaRecebidaOReciboFicaSemMetodoEVaiTerAFormaCombinada() {
        UUID id = enviado(false);
        aprovar(id, pagamento(false, null)); // atalho -> FINALIZADO
        avancar(id); // FINALIZADO -> PAGO sem forma recebida: vale a combinada (Pix)
        assertEquals(StatusOrcamento.PAGO, status(id));
        assertNull(recibos.findByOrcamentoId(id).orElseThrow().getMetodoPagamento());
        assertEquals("PIX", parcela(id, PapelParcela.TOTAL).metodoPagamentoNome());
    }

    @Test
    void receberPelaTelaDoFinanceiroExecutaATransicaoDoOrcamento() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        avancar(id); // AGUARDANDO_SINAL
        UUID parcelaSinal = parcela(id, PapelParcela.SINAL).id();
        OrcamentoDaParcela ref = financeiro.orcamentoDaParcela(parcelaSinal).orElseThrow();
        orcamentoService.receberParcela(ref, null, dinheiro.getId());
        assertEquals(StatusOrcamento.SINAL_PAGO, status(id));
        assertEquals(SituacaoParcela.BAIXADA, parcela(id, PapelParcela.SINAL).situacao());
        assertNotNull(orcamentos.findById(id).orElseThrow().getDataSinalPago());
    }

    @Test
    void saldoNaoPodeSerRecebidoAntesDeFinalizar() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        OrcamentoDaParcela ref = financeiro.orcamentoDaParcela(parcela(id, PapelParcela.SALDO).id()).orElseThrow();
        BusinessException e = assertThrows(BusinessException.class, () -> orcamentoService.receberParcela(ref, null, pix.getId()));
        assertEquals("O saldo só pode ser recebido com o orçamento finalizado.", e.getMessage());
        assertEquals(SituacaoParcela.EM_ABERTO, parcela(id, PapelParcela.SALDO).situacao());
    }

    @Test
    void baixaDiretaPeloFinanceiroDeParcelaDeOrcamentoEBloqueada() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        UUID p = parcela(id, PapelParcela.SALDO).id();
        BusinessException e = assertThrows(BusinessException.class, () -> financeiro.baixar(p, null, pix.getId()));
        assertEquals("Receba esta parcela pelo orçamento.", e.getMessage());
    }

    @Test
    void desfazerBaixaDoSinalComReciboEmitidoEBloqueado() {
        UUID id = enviado(true);
        aprovarComSinalAteEmProducao(id, "90.00");
        UUID p = parcela(id, PapelParcela.SINAL).id();
        BusinessException e = assertThrows(BusinessException.class, () -> financeiro.desfazerBaixa(p));
        assertEquals("O recibo já foi emitido; use o estorno do orçamento.", e.getMessage());
        assertEquals(SituacaoParcela.BAIXADA, parcela(id, PapelParcela.SINAL).situacao());
    }

    @Test
    void parcelaDeOutraEmpresaNaoEReconhecidaComoDeOrcamento() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        UUID parcelaId = parcela(id, PapelParcela.SALDO).id();
        Usuario outra = usuarioRepository.save(Usuario.builder().email("outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outra.getEmail(), null, List.of()));
        assertTrue(financeiro.orcamentoDaParcela(parcelaId).isEmpty());
        assertThrows(com.penseprecifique.api.shared.exception.ResourceNotFoundException.class, () -> financeiro.baixar(parcelaId, null, pix.getId()));
    }

    // --------------------------------------------------------------------------------------------- alterar pagamento (CEN-NOVO-20, 21)

    @Test
    void alterarPagamentoRefazAsParcelasEmAberto() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        orcamentoService.alterarPagamento(id, pagamento(true, "150.00"));
        List<ParcelaOrcamentoResponse> ps = parcelas(id);
        assertEquals(2, ps.stream().filter(p -> p.situacao() == SituacaoParcela.EM_ABERTO).count());
        assertEquals(2, ps.stream().filter(p -> p.situacao() == SituacaoParcela.CANCELADA).count());
        assertValor("150.00", ps.stream().filter(p -> p.papel() == PapelParcela.SINAL && p.situacao() == SituacaoParcela.EM_ABERTO).findFirst().orElseThrow().valor());
        assertValor("150.00", ps.stream().filter(p -> p.papel() == PapelParcela.SALDO && p.situacao() == SituacaoParcela.EM_ABERTO).findFirst().orElseThrow().valor());
        assertValor("300.00", lancamentoDoOrcamento(id).getValorTotal());
    }

    @Test
    void alterarPagamentoComParcelaRecebidaEBloqueio() {
        UUID id = enviado(true);
        aprovarComSinalAteEmProducao(id, "90.00");
        BusinessException e = assertThrows(BusinessException.class, () -> orcamentoService.alterarPagamento(id, pagamento(true, "100.00")));
        assertEquals("Já há pagamento recebido; use o estorno para desfazer.", e.getMessage());
    }

    @Test
    void incluirSinalDepoisDeAprovadoSemSinalEBloqueio() {
        UUID id = enviado(true);
        aprovar(id, pagamento(false, null));
        BusinessException e = assertThrows(BusinessException.class, () -> orcamentoService.alterarPagamento(id, pagamento(true, "90.00")));
        assertEquals("O sinal é combinado na aprovação; esta já foi feita sem sinal.", e.getMessage());
    }

    @Test
    void removerOSinalEmAguardandoSinalLevaAEmProducao() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        avancar(id); // AGUARDANDO_SINAL
        orcamentoService.alterarPagamento(id, pagamento(false, null));
        assertEquals(StatusOrcamento.EM_PRODUCAO, status(id));
        assertFalse(orcamentos.findById(id).orElseThrow().getSinalAtivo());
        ParcelaOrcamentoResponse total = parcelas(id).stream().filter(p -> p.papel() == PapelParcela.TOTAL).findFirst().orElseThrow();
        assertEquals(SituacaoParcela.EM_ABERTO, total.situacao());
        assertValor("300.00", total.valor());
    }

    @Test
    void alterarPagamentoAntesDeAprovarEBloqueio() {
        UUID id = enviado(true);
        assertThrows(BusinessException.class, () -> orcamentoService.alterarPagamento(id, pagamento(false, null)));
    }

    // --------------------------------------------------------------------------------------------- cancelar (RN-NOVA-12/51)

    private AvancaStatusRequest cancelamento() {
        AvancaStatusRequest r = new AvancaStatusRequest();
        r.setMotivoCancelamento("Cliente desistiu");
        return r;
    }

    @Test
    void cancelarSemNadaRecebidoCancelaAsParcelasEZeraOLancamento() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        orcamentoService.cancelar(id, cancelamento());
        assertTrue(parcelas(id).stream().allMatch(p -> p.situacao() == SituacaoParcela.CANCELADA));
        Lancamento l = lancamentoDoOrcamento(id);
        assertEquals(SituacaoLancamento.CANCELADO, l.getSituacao());
        assertValor("0.00", l.getValorTotal());
        assertTrue(lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL).isEmpty());
    }

    @Test
    void cancelarComSinalRecebidoSemEstornoMantemSinalRecebidoELancamentoCancelado() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        avancar(id);
        receberSinal(id, pix.getId());
        orcamentoService.cancelar(id, cancelamento());
        assertEquals(SituacaoParcela.BAIXADA, parcela(id, PapelParcela.SINAL).situacao());
        assertEquals(SituacaoParcela.CANCELADA, parcela(id, PapelParcela.SALDO).situacao());
        Lancamento l = lancamentoDoOrcamento(id);
        assertEquals(SituacaoLancamento.CANCELADO, l.getSituacao());
        assertValor("90.00", l.getValorTotal());
        assertTrue(lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL).isEmpty());
    }

    @Test
    void estornoDoSinalViraUmaSaidaPagaUmaSoVezSemCategoria() {
        UUID id = enviado(true);
        aprovar(id, pagamento(true, "90.00"));
        avancar(id);
        receberSinal(id, pix.getId());
        AvancaStatusRequest r = cancelamento();
        r.setEstornarSinal(true);
        r.setDataEstornoSinal(LocalDateTime.now());
        orcamentoService.cancelar(id, r);
        List<Lancamento> estornos = lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL);
        assertEquals(1, estornos.size());
        Lancamento estorno = estornos.get(0);
        assertEquals(com.penseprecifique.api.financeiro.TipoLancamento.A_PAGAR, estorno.getTipo());
        assertNull(estorno.getCategoria());
        assertValor("90.00", estorno.getValorTotal());
        // cancelar de novo não duplica o estorno
        assertThrows(BusinessException.class, () -> orcamentoService.cancelar(id, r));
        assertEquals(1, lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL).size());
        // o estorno nunca se desfaz
        UUID parcelaEstorno = financeiroParcelaDo(estorno);
        BusinessException e = assertThrows(BusinessException.class, () -> financeiro.desfazerBaixa(parcelaEstorno));
        assertEquals("O estorno do sinal já foi registrado e não pode ser desfeito.", e.getMessage());
    }

    @Autowired com.penseprecifique.api.financeiro.ParcelaRepository parcelaRepository;

    private UUID financeiroParcelaDo(Lancamento l) {
        var ps = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        assertEquals(1, ps.size());
        assertEquals(SituacaoParcela.BAIXADA, ps.get(0).getSituacao());
        return ps.get(0).getId();
    }

    @Test
    void multaMaiorQueOSinalGeraParcelaMultaEmAbertoComADiferenca() {
        UUID id = enviado(true);
        aprovarComSinalAteEmProducao(id, "120.00");
        AvancaStatusRequest r = cancelamento();
        r.setPercentualMulta(new BigDecimal("50")); // 150,00 - sinal 120,00 = 30,00
        orcamentoService.cancelar(id, r);
        ParcelaOrcamentoResponse multa = parcela(id, PapelParcela.MULTA);
        assertValor("30.00", multa.valor());
        assertEquals(SituacaoParcela.EM_ABERTO, multa.situacao());
        assertEquals(LocalDate.now(), multa.vencimento());
        assertEquals(SituacaoParcela.CANCELADA, parcela(id, PapelParcela.SALDO).situacao());
        assertEquals(SituacaoLancamento.ATIVO, lancamentoDoOrcamento(id).getSituacao());
        assertValor("150.00", lancamentoDoOrcamento(id).getValorTotal());
        assertTrue(lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL).isEmpty());
    }

    @Test
    void multaMenorQueOSinalDevolveADiferencaComoEstornoPago() {
        UUID id = enviado(true);
        aprovarComSinalAteEmProducao(id, "120.00");
        AvancaStatusRequest r = cancelamento();
        r.setPercentualMulta(new BigDecimal("20")); // 60,00 de multa; devolve 60,00
        orcamentoService.cancelar(id, r);
        assertTrue(parcelas(id).stream().noneMatch(p -> p.papel() == PapelParcela.MULTA));
        assertEquals(SituacaoLancamento.CANCELADO, lancamentoDoOrcamento(id).getSituacao());
        List<Lancamento> estornos = lancamentos.findByOrcamentoIdAndSubtipo(id, SubtipoLancamento.ESTORNO_SINAL);
        assertEquals(1, estornos.size());
        assertValor("60.00", estornos.get(0).getValorTotal());
    }

    @Test
    void cancelarOrcamentoAnteriorAVersaoSemLancamentoNaoQuebra() {
        UUID id = enviado(true);
        // ENVIADO nunca teve pagamento: não há lançamento
        orcamentoService.cancelar(id, cancelamento());
        assertEquals(StatusOrcamento.CANCELADO, status(id));
        assertTrue(lancamentos.findByOrcamentoIdAndUsuarioIdAndSubtipoIsNull(id, usuario.getId()).isEmpty());
    }
}
