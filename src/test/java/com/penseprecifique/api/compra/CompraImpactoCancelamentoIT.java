package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraConfirmacaoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ImpactoCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.SimulacaoCancelamentoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.penseprecifique.api.compra.CompraRegistroIT.compra;
import static com.penseprecifique.api.compra.CompraRegistroIT.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 — #543 (RN-NOVA-8, modal de impacto) e #544 (RN-NOVA-9 cancelar, RN-NOVA-10 duplicar).
 * Cenários CEN-NOVO-14 a 19.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraImpactoCancelamentoIT {

    private static final String OBS = "Fornecedor entregou o pedido errado, devolvido.";

    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    @Autowired ClienteRepository clienteRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroProduto = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-impacto-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
    }

    private Insumo insumo(String nome, String estoque, String custo, boolean permiteNegativo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(new BigDecimal(estoque)).custoUnitario(new BigDecimal(custo))
                .permitirEstoqueNegativo(permiteNegativo).build());
    }

    private Produto produto(String nome, String precoCusto, String margem, String precoVenda, boolean override) {
        return produtoRepository.save(Produto.builder().usuario(usuario).numero(numeroProduto++).nome(nome)
                .tipo(TipoProduto.PRODUTO).tempoProducao(0).rendimento(BigDecimal.ONE)
                .margemLucro(new BigDecimal(margem)).precoCusto(new BigDecimal(precoCusto))
                .precoVenda(new BigDecimal(precoVenda)).override(override).build());
    }

    private void ficha(Produto produto, Insumo insumo, Produto base, String qtd) {
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(produto).insumo(insumo).produtoBase(base)
                .quantidade(new BigDecimal(qtd)).build());
    }

    private BigDecimal custo(Insumo i) {
        return insumoRepository.findById(i.getId()).orElseThrow().getCustoUnitario();
    }

    private BigDecimal estoque(Insumo i) {
        return insumoRepository.findById(i.getId()).orElseThrow().getEstoqueAtual();
    }

    @Test
    void cen14_impactoComProdutoIndiretoSemMudarPrecoDeVenda() {
        // Fita R$ 1,00/m; Laço = 0,5 m de fita (custo 0,50, margem 100%); Kit = 2 Laços (custo 1,00,
        // margem 50%, preço de venda R$ 40,00 editado à mão). Sem valor-hora: mão de obra 0.
        Insumo fita = insumo("Fita de cetim", "0", "1.00", true);
        Produto laco = produto("Laço", "0.50", "100", "1.00", false);
        Produto kit = produto("Kit Presente", "1.00", "50", "40.00", true);
        Produto semRelacao = produto("Vela", "3.00", "10", "3.30", false);
        ficha(laco, fita, null, "0.5");
        ficha(kit, null, laco, "2");

        // 10 m por R$ 18,00 com estoque 0 → fita a R$ 1,80
        CompraConfirmacaoResponse r = compraService.confirmarNova(compra(null, List.of(linha(fita, "10", "18.00"))));
        ImpactoCompraResponse imp = r.impacto();

        assertTrue(imp.alterouCustos());
        assertEquals(0, new BigDecimal("1.00").compareTo(imp.insumos().get(0).custoAntes()));
        assertEquals(0, new BigDecimal("1.80").compareTo(imp.insumos().get(0).custoDepois()));
        assertEquals(List.of("Laço", "Kit Presente"), imp.produtos().stream().map(ImpactoCompraResponse.ProdutoImpacto::nome).toList());

        ImpactoCompraResponse.ProdutoImpacto pLaco = imp.produtos().get(0);
        assertTrue(pLaco.direto());
        assertEquals(0, new BigDecimal("0.50").compareTo(pLaco.custoAntes()));
        assertEquals(0, new BigDecimal("0.90").compareTo(pLaco.custoDepois()));
        assertEquals(0, new BigDecimal("1.00").compareTo(pLaco.precoSugeridoAntes()));
        assertEquals(0, new BigDecimal("1.80").compareTo(pLaco.precoSugeridoDepois()));

        ImpactoCompraResponse.ProdutoImpacto pKit = imp.produtos().get(1);
        assertFalse(pKit.direto());
        assertEquals(0, new BigDecimal("1.00").compareTo(pKit.custoAntes()));
        assertEquals(0, new BigDecimal("1.80").compareTo(pKit.custoDepois()));
        assertEquals(0, new BigDecimal("1.50").compareTo(pKit.precoSugeridoAntes()));
        assertEquals(0, new BigDecimal("2.70").compareTo(pKit.precoSugeridoDepois()));
        assertTrue(pKit.precoVendaManual());

        // preço de venda nunca muda; custo persistido acompanha (o Kit usa o precoCusto do Laço)
        Produto kitDepois = produtoRepository.findById(kit.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40.00").compareTo(kitDepois.getPrecoVenda()));
        assertEquals(0, new BigDecimal("1.80").compareTo(kitDepois.getPrecoCusto()));
        assertEquals(0, new BigDecimal("0.90").compareTo(produtoRepository.findById(laco.getId()).orElseThrow().getPrecoCusto()));
        assertEquals(0, new BigDecimal("3.00").compareTo(produtoRepository.findById(semRelacao.getId()).orElseThrow().getPrecoCusto()));
    }

    @Test
    void compraQueNaoMudaCustoNaoListaProdutos() {
        Insumo fita = insumo("Fita", "10", "1.50", true);
        Produto laco = produto("Laço", "0.75", "100", "1.50", false);
        ficha(laco, fita, null, "0.5");
        // 10 m por R$ 15,00 = mesmo R$ 1,50 → custo não muda
        ImpactoCompraResponse imp = compraService.confirmarNova(compra(null, List.of(linha(fita, "10", "15.00")))).impacto();
        assertFalse(imp.alterouCustos());
        assertTrue(imp.produtos().isEmpty());
    }

    @Test
    void cen15_cancelarAUltimaCompraRestauraCusto() {
        // Cola R$ 12,00, estoque 3; COM de 3 un. por R$ 54,00 → (36 + 54) / 6 = R$ 15,00, estoque 6
        Insumo cola = insumo("Cola Branca 1L", "3", "12.00", true);
        Produto cartao = produto("Cartão", "1.20", "100", "2.40", false);
        ficha(cartao, cola, null, "0.1");
        CompraResponse c = compraService.confirmarNova(compra(null, List.of(linha(cola, "3", "54.00")))).compra();
        assertEquals(0, new BigDecimal("15.00").compareTo(custo(cola)));

        SimulacaoCancelamentoResponse sim = compraService.simularCancelamento(c.id());
        assertTrue(sim.podeCancelar());
        assertTrue(sim.avisos().isEmpty());

        CompraConfirmacaoResponse r = compraService.cancelar(c.id(), new CancelarCompraRequest(OBS, false));
        assertEquals(StatusCompra.CANCELADA, r.compra().status());
        assertEquals(OBS, r.compra().observacaoCancelamento());
        assertEquals(0, new BigDecimal("12.00").compareTo(custo(cola)));
        assertEquals(0, new BigDecimal("3").compareTo(estoque(cola)));
        // o produto volta ao custo anterior (R$ 1,20) e aparece no impacto do cancelamento
        assertEquals(0, new BigDecimal("1.20").compareTo(produtoRepository.findById(cartao.getId()).orElseThrow().getPrecoCusto()));
        assertEquals(1, r.impacto().produtos().size());

        List<MovimentacaoInsumo> movs = movimentacaoInsumoRepository.findByReferenciaIdAndReferenciaTipo(c.id(), ReferenciaMovimentacaoTipo.COMPRA);
        MovimentacaoInsumo entrada = movs.stream().filter(m -> m.getMotivo() == MotivoMovimentacaoInsumo.COMPRA).findFirst().orElseThrow();
        MovimentacaoInsumo estorno = movs.stream().filter(m -> m.getMotivo() == MotivoMovimentacaoInsumo.ESTORNO_COMPRA).findFirst().orElseThrow();
        assertTrue(entrada.getEstornada());
        assertEquals(TipoMovimentacaoInsumo.SAIDA, estorno.getTipo());
        assertEquals(0, new BigDecimal("3").compareTo(estorno.getQuantidade()));
    }

    @Test
    void cen16_cancelarCompraQueNaoEAUltimaAvisaEMantemCusto() {
        Insumo cola = insumo("Cola Branca 1L", "0", "12.00", true);
        CompraResponse primeira = compraService.confirmarNova(compra(null, List.of(linha(cola, "2", "30.00")))).compra(); // 15,00
        compraService.confirmarNova(compra(null, List.of(linha(cola, "2", "34.00"))));                                   // (30+34)/4 = 16,00

        SimulacaoCancelamentoResponse sim = compraService.simularCancelamento(primeira.id());
        assertTrue(sim.podeCancelar());
        assertEquals(1, sim.avisos().size());
        assertEquals("Cola Branca 1L", sim.avisos().get(0).nome());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> compraService.cancelar(primeira.id(), new CancelarCompraRequest(OBS, false)));
        assertTrue(ex.getMessage().contains("Cola Branca 1L manterá o custo atual"), ex.getMessage());
        assertEquals(StatusCompra.CONFIRMADA, compraService.buscar(primeira.id()).status());

        compraService.cancelar(primeira.id(), new CancelarCompraRequest(OBS, true));
        assertEquals(0, new BigDecimal("16.00").compareTo(custo(cola)));
        assertEquals(0, new BigDecimal("2").compareTo(estoque(cola)));
    }

    @Test
    void cen17_estoqueNegativoProibidoBloqueiaOCancelamentoInteiro() {
        Insumo kraft = insumo("Papel Kraft", "0", "2.00", false);
        Insumo cola = insumo("Cola", "0", "12.00", true);
        CompraResponse c = compraService.confirmarNova(compra(null, List.of(
                linha(kraft, "10", "25.00"), linha(cola, "1", "12.00")))).compra();
        Insumo k = insumoRepository.findById(kraft.getId()).orElseThrow();
        k.setEstoqueAtual(new BigDecimal("4"));
        insumoRepository.save(k);

        SimulacaoCancelamentoResponse sim = compraService.simularCancelamento(c.id());
        assertFalse(sim.podeCancelar());
        assertEquals(0, new BigDecimal("-6").compareTo(sim.bloqueios().get(0).estoqueResultante()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> compraService.cancelar(c.id(), new CancelarCompraRequest(OBS, true)));
        assertTrue(ex.getMessage().contains("Papel Kraft"), ex.getMessage());
        assertEquals(0, new BigDecimal("4").compareTo(estoque(kraft)));
        assertEquals(0, new BigDecimal("1").compareTo(estoque(cola)));
        assertEquals(StatusCompra.CONFIRMADA, compraService.buscar(c.id()).status());
    }

    @Test
    void cen18_observacaoComMenosDe30CaracteresEhRecusada() {
        var violacoes = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new CancelarCompraRequest("Pedido errado", true));
        assertTrue(violacoes.stream().anyMatch(v -> v.getMessage().equals("A observação precisa ter pelo menos 30 caracteres")));
    }

    @Test
    void mesmoInsumoEmDuasLinhasVoltaEmCadeia() {
        // modo múltiplos: cola de dois fornecedores. R$ 10,00 → linha 1 (2 por 24,00) → 12,00 →
        // linha 2 (2 por 28,00) → (24 + 28) / 4 = 13,00. Cancelar volta 13 → 12 → 10.
        Insumo cola = insumo("Cola", "0", "10.00", true);
        Cliente f1 = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("F1").ehCliente(false).ehFornecedor(true).ativa(true).build());
        Cliente f2 = clienteRepository.save(Cliente.builder().usuario(usuario).numero(2).nome("F2").ehCliente(false).ehFornecedor(true).ativa(true).build());
        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now(), true, null, false, null, null, List.of(
                new CompraItemRequest(cola.getId(), f1.getId(), new BigDecimal("2"), new BigDecimal("24.00")),
                new CompraItemRequest(cola.getId(), f2.getId(), new BigDecimal("2"), new BigDecimal("28.00"))))).compra();
        assertEquals(0, new BigDecimal("13.00").compareTo(custo(cola)));

        assertTrue(compraService.simularCancelamento(c.id()).avisos().isEmpty());
        compraService.cancelar(c.id(), new CancelarCompraRequest(OBS, false));
        assertEquals(0, new BigDecimal("10.00").compareTo(custo(cola)));
        assertEquals(0, BigDecimal.ZERO.compareTo(estoque(cola)));
    }

    @Test
    void soCompraConfirmadaPodeSerCancelada() {
        Insumo cola = insumo("Cola", "0", "10.00", true);
        CompraResponse rascunho = compraService.criarRascunho(compra(null, List.of(linha(cola, "1", "1.00"))));
        assertEquals("Só é possível cancelar uma compra confirmada.", assertThrows(BusinessException.class,
                () -> compraService.cancelar(rascunho.id(), new CancelarCompraRequest(OBS, true))).getMessage());
    }

    @Test
    void cen19_duplicarCompraCancelada() {
        Insumo cola = insumo("Cola", "0", "12.00", true);
        Insumo inativo = insumo("Glitter", "0", "3.00", true);
        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now().minusDays(5), false, null, false,
                null, "Pedido semanal", List.of(linha(cola, "3", "45.00"), linha(inativo, "2", "7.10")))).compra();
        compraService.cancelar(c.id(), new CancelarCompraRequest(OBS, false));
        Insumo g = insumoRepository.findById(inativo.getId()).orElseThrow();
        g.setAtivo(false);
        insumoRepository.save(g);
        BigDecimal estoqueAntes = estoque(cola);

        CompraResponse copia = compraService.duplicar(c.id());
        assertEquals(StatusCompra.RASCUNHO, copia.status());
        assertEquals("COM-2", copia.identificador());
        assertEquals(LocalDate.now(), copia.dataCompra());
        assertFalse(copia.pago());
        assertEquals("Pedido semanal", copia.observacoes());
        assertEquals(2, copia.itens().size());
        assertEquals(0, new BigDecimal("45.00").compareTo(copia.itens().get(0).precoTotal()));
        assertEquals("Glitter", copia.itens().get(1).insumo().nome());
        assertEquals(0, estoqueAntes.compareTo(estoque(cola)));

        // rascunho não pode ser duplicado
        assertThrows(BusinessException.class, () -> compraService.duplicar(copia.id()));
    }
}
