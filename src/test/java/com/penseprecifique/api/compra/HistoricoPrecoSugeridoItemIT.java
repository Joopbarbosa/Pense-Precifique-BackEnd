package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.empresa.ConfiguracaoService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.CausaPrecoSugerido;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.MudancaPrecoSugerido;
import com.penseprecifique.api.produto.MudancaPrecoSugeridoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.config.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.catalogo.HistoricoPrecoItemResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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
 * V0.18.0 — #961 (RN-NOVA-28, DT-NOVA-13): histórico do preço sugerido do item de Catálogo. CEN-NOVO-72 e 73. O histórico do
 * Produto segue coberto por {@link HistoricoPrecoSugeridoIT}, que roda junto como regressão da tabela compartilhada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HistoricoPrecoSugeridoItemIT {

    @Autowired CompraService compraService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired ConfiguracaoService configuracaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired com.penseprecifique.api.catalogo.ItemCatalogoRepository itemCatalogoRepository;
    @Autowired MudancaPrecoSugeridoRepository mudancaRepository;

    private Usuario usuario;
    private UnidadeMedida metro;
    private Catalogo catalogo;
    private int numeroInsumo = 1;
    private int numeroProduto = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("hist-item-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metro = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
        catalogo = catalogoRepository.save(Catalogo.builder().usuario(usuario).numero(1).nome("Festas").build());
        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(BigDecimal.ZERO, new BigDecimal("50")));
    }

    private Insumo insumo(String nome, String custo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(metro).estoqueAtual(BigDecimal.ZERO).custoUnitario(new BigDecimal(custo))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private ItemCatalogoResponse item(String nome, String margem, int tempo, ItemCatalogoComponenteRequest... componentes) {
        ItemCatalogoRequest r = new ItemCatalogoRequest();
        r.setNome(nome);
        r.setTempoProducao(tempo);
        r.setMargemLucro(new BigDecimal(margem));
        r.setComponentes(List.of(componentes));
        r.setPrecoVenda(new BigDecimal("10.00"));
        return itemCatalogoService.adicionar(catalogo.getId(), r);
    }

    private ItemCatalogoComponenteRequest comInsumo(Insumo i, String qtd) {
        ItemCatalogoComponenteRequest c = new ItemCatalogoComponenteRequest();
        c.setInsumoId(i.getId());
        c.setQuantidade(new BigDecimal(qtd));
        return c;
    }

    private ItemCatalogoComponenteRequest comProduto(Produto p, String qtd) {
        ItemCatalogoComponenteRequest c = new ItemCatalogoComponenteRequest();
        c.setProdutoBaseId(p.getId());
        c.setQuantidade(new BigDecimal(qtd));
        return c;
    }

    private ItemCatalogoRequest pedido(String nome, String margem, int tempo, ItemCatalogoComponenteRequest... componentes) {
        ItemCatalogoRequest r = new ItemCatalogoRequest();
        r.setNome(nome);
        r.setTempoProducao(tempo);
        r.setMargemLucro(new BigDecimal(margem));
        r.setComponentes(List.of(componentes));
        r.setPrecoVenda(new BigDecimal("10.00"));
        return r;
    }

    private List<HistoricoPrecoItemResponse> historico(UUID itemId) {
        return itemCatalogoService.historicoPreco(catalogo.getId(), itemId, PageRequest.of(0, 50)).getContent();
    }

    private static void confere(HistoricoPrecoItemResponse l, String anterior, String novo, String causa, String referencia) {
        assertEquals(0, new BigDecimal(anterior).compareTo(l.valorAnterior()), "anterior " + l.valorAnterior());
        assertEquals(0, new BigDecimal(novo).compareTo(l.valorNovo()), "novo " + l.valorNovo());
        assertEquals(causa, l.causa());
        assertEquals(referencia, l.referenciaTexto());
    }

    private CompraRequest compra(Insumo i, String qtd, String total) {
        return new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(i.getId(), null, new BigDecimal(qtd), new BigDecimal(total))));
    }

    /** CEN-NOVO-72 — a entrada de compra que sobe o custo do insumo registra a mudança do item; cancelar devolve; venda intacta. */
    @Test
    void entradaECancelamentoDeCompraRegistram() {
        Insumo papel = insumo("Papel A4", "2.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 0, comInsumo(papel, "1"));
        assertEquals(0, new BigDecimal("3.00").compareTo(kit.getPrecoSugerido()));
        assertTrue(historico(kit.getId()).isEmpty(), "item novo não tem linha");

        var c = compraService.confirmarNova(compra(papel, "10", "40.00")).compra();
        confere(historico(kit.getId()).get(0), "3.00", "6.00", "COMPRA_ENTRADA", c.identificador());
        assertEquals(0, new BigDecimal("10.00").compareTo(itemCatalogoService.listarPorCatalogo(catalogo.getId()).get(0).getPrecoVenda()));

        compraService.cancelar(c.id(), new CancelarCompraRequest("Compra feita por engano, mercadoria devolvida ao fornecedor", false));
        confere(historico(kit.getId()).get(0), "6.00", "3.00", "COMPRA_CANCELAMENTO", c.identificador());
        assertEquals(2, historico(kit.getId()).size());
    }

    /** Item que usa o insumo por um produto componente também entra, com o insumo de origem. */
    @Test
    void itemComProdutoComponenteAlcancadoPelaCompra() {
        Insumo fita = insumo("Fita", "2.00");
        Produto laco = produtoRepository.save(Produto.builder().usuario(usuario).numero(numeroProduto++).nome("Laço")
                .tipo(TipoProduto.PRODUTO).tempoProducao(0).rendimento(BigDecimal.ONE).margemLucro(new BigDecimal("50"))
                .precoCusto(new BigDecimal("2.00")).precoVenda(new BigDecimal("4.00")).override(true).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(laco).insumo(fita).quantidade(BigDecimal.ONE).build());
        ItemCatalogoResponse kit = item("Kit de laços", "50", 0, comProduto(laco, "2"));
        assertEquals(0, new BigDecimal("6.00").compareTo(kit.getPrecoSugerido()));

        var c = compraService.confirmarNova(compra(fita, "10", "40.00")).compra();
        // custo do laço 2,00 → 4,00; o kit usa 2 lacos: 4,00 → 8,00, sugerido 6,00 → 12,00
        confere(historico(kit.getId()).get(0), "6.00", "12.00", "COMPRA_ENTRADA", c.identificador());
    }

    /** CEN-NOVO-73 — compra de insumo que o item não usa, ou valor igual em centavos, não gera linha. */
    @Test
    void semMudancaNaoGeraLinha() {
        Insumo papel = insumo("Papel A4", "2.00");
        Insumo cola = insumo("Cola", "1.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 0, comInsumo(papel, "1"));
        compraService.confirmarNova(compra(cola, "10", "50.00"));
        assertTrue(historico(kit.getId()).isEmpty());
        // só o nome muda: o preço sugerido é o mesmo
        itemCatalogoService.editar(kit.getId(), pedido("Kit festa grande", "50", 0, comInsumo(papel, "1")));
        assertTrue(historico(kit.getId()).isEmpty());
    }

    /** Edição do próprio item: componentes → ITEM_EDICAO; só a margem → ITEM_MARGEM. */
    @Test
    void edicaoDoItemEMudancaDeMargem() {
        Insumo papel = insumo("Papel A4", "2.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 0, comInsumo(papel, "1"));
        itemCatalogoService.editar(kit.getId(), pedido("Kit festa", "50", 0, comInsumo(papel, "2")));
        confere(historico(kit.getId()).get(0), "3.00", "6.00", "ITEM_EDICAO", null);
        itemCatalogoService.editar(kit.getId(), pedido("Kit festa", "100", 0, comInsumo(papel, "2")));
        confere(historico(kit.getId()).get(0), "6.00", "8.00", "ITEM_MARGEM", null);
        assertEquals(2, historico(kit.getId()).size());
    }

    /** O valor da hora alcança todos os itens da conta. */
    @Test
    void valorDaHoraRegistraParaOsItens() {
        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(new BigDecimal("60.00"), new BigDecimal("50")));
        Insumo papel = insumo("Papel A4", "2.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 60, comInsumo(papel, "1")); // 2,00 + 1h a R$ 60 = 62 → 93,00
        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(new BigDecimal("120.00"), new BigDecimal("50")));
        confere(historico(kit.getId()).get(0), "93.00", "183.00", "VALOR_HORA", null);
    }

    /** Histórico paginado, da mais recente para a mais antiga; item de outro catálogo ou de outra conta: não encontrado. */
    @Test
    void historicoPaginadoEIsolado() {
        Insumo papel = insumo("Papel A4", "2.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 0, comInsumo(papel, "1"));
        for (int q = 2; q <= 6; q++) {
            itemCatalogoService.editar(kit.getId(), pedido("Kit festa", "50", 0, comInsumo(papel, String.valueOf(q))));
        }
        var p0 = itemCatalogoService.historicoPreco(catalogo.getId(), kit.getId(), PageRequest.of(0, 2));
        assertEquals(5, p0.getTotalElements());
        assertEquals(2, p0.getContent().size());
        assertEquals(0, new BigDecimal("18.00").compareTo(p0.getContent().get(0).valorNovo()), "a mais recente primeiro");
        assertEquals(0, new BigDecimal("15.00").compareTo(p0.getContent().get(1).valorNovo()));
        assertTrue(itemCatalogoService.historicoPreco(catalogo.getId(), kit.getId(), PageRequest.of(9, 2)).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> itemCatalogoService.historicoPreco(UUID.randomUUID(), kit.getId(), PageRequest.of(0, 2)));

        Usuario outro = usuarioRepository.save(Usuario.builder().email("outro-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outro.getEmail(), null, List.of()));
        assertThrows(ResourceNotFoundException.class, () -> itemCatalogoService.historicoPreco(catalogo.getId(), kit.getId(), PageRequest.of(0, 2)));
    }

    /** Origem exclusiva: o banco recusa uma linha sem origem ou com as duas. */
    @Test
    void origemExclusivaNoBanco() {
        Insumo papel = insumo("Papel A4", "2.00");
        ItemCatalogoResponse kit = item("Kit festa", "50", 0, comInsumo(papel, "1"));
        Produto p = produtoRepository.save(Produto.builder().usuario(usuario).numero(numeroProduto++).nome("Laço").tipo(TipoProduto.PRODUTO)
                .tempoProducao(0).rendimento(BigDecimal.ONE).margemLucro(BigDecimal.TEN).precoCusto(BigDecimal.ONE).precoVenda(BigDecimal.ONE).build());
        assertThrows(DataIntegrityViolationException.class, () -> mudancaRepository.saveAndFlush(MudancaPrecoSugerido.builder()
                .usuarioId(usuario.getId()).valorAnterior(BigDecimal.ONE).valorNovo(BigDecimal.TEN).causa(CausaPrecoSugerido.ITEM_EDICAO).build()));
        assertThrows(DataIntegrityViolationException.class, () -> mudancaRepository.saveAndFlush(MudancaPrecoSugerido.builder()
                .usuarioId(usuario.getId()).produto(p).itemCatalogo(itemCatalogoRepository.findById(kit.getId()).orElseThrow())
                .valorAnterior(BigDecimal.ONE).valorNovo(BigDecimal.TEN).causa(CausaPrecoSugerido.ITEM_EDICAO).build()));
    }
}
