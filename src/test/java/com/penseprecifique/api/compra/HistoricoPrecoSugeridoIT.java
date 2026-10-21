package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.ConfiguracaoService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.produto.ProdutoService;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.config.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.produto.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 — #672 (RN-NOVA-10 e 11, DT-NOVA-6 e 7). Cenários CEN-NOVO-26 a 31. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HistoricoPrecoSugeridoIT {

    @Autowired CompraService compraService;
    @Autowired ProdutoService produtoService;
    @Autowired InsumoService insumoService;
    @Autowired ConfiguracaoService configuracaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private UnidadeMedida metro;
    private int numeroInsumo = 1;
    private int numeroProduto = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("hist-preco-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metro = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
    }

    private Insumo insumo(String nome, String estoque, String custo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(metro).estoqueAtual(new BigDecimal(estoque)).custoUnitario(new BigDecimal(custo))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private Produto produto(String nome, String precoCusto, String margem, String precoVenda) {
        return produtoRepository.save(Produto.builder().usuario(usuario).numero(numeroProduto++).nome(nome)
                .tipo(TipoProduto.PRODUTO).tempoProducao(1).rendimento(BigDecimal.ONE)
                .margemLucro(new BigDecimal(margem)).precoCusto(new BigDecimal(precoCusto))
                .precoVenda(new BigDecimal(precoVenda)).override(true).build());
    }

    private void ficha(Produto produto, Insumo insumo, Produto base, String qtd) {
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(produto).insumo(insumo).produtoBase(base)
                .quantidade(new BigDecimal(qtd)).build());
    }

    private CompraResponse pendente(Insumo insumo, String qtd, String total) {
        return compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(insumo.getId(), null, new BigDecimal(qtd), new BigDecimal(total))),
                null, null, null, false, LocalDate.now().plusDays(3))).compra();
    }

    private EntradaCompraRequest entrada(CompraResponse c) {
        return new EntradaCompraRequest(LocalDate.now(), c.itens().stream()
                .map(i -> new EntradaCompraRequest.ItemEntrada(i.id(), i.quantidade())).toList());
    }

    private List<MovimentacaoProdutoResponse> linhasDePreco(Produto p) {
        return produtoService.listarMovimentacoes(p.getId(), PageRequest.of(0, 50)).getContent().stream()
                .filter(l -> "PRECO_SUGERIDO".equals(l.getTipoLinha())).toList();
    }

    private static void confere(MovimentacaoProdutoResponse l, String anterior, String novo, String causa, String referencia) {
        assertEquals(0, new BigDecimal(anterior).compareTo(l.getPrecoSugeridoAnterior()));
        assertEquals(0, new BigDecimal(novo).compareTo(l.getPrecoSugeridoNovo()));
        assertEquals(causa, l.getCausaPrecoSugerido());
        assertEquals(referencia, l.getReferenciaTexto());
    }

    @Test
    void cen26_cen27_cen30_entradaECancelamentoRegistramEOHistoricoDoInsumoAgrega() {
        Insumo fita = insumo("Fita de cetim", "0", "2.00");
        Produto laco = produto("Laço", "2.00", "50", "4.00");
        Produto kit = produto("Kit Laços", "4.00", "50", "10.00");
        ficha(laco, fita, null, "1");
        ficha(kit, null, laco, "2");

        CompraResponse c = pendente(fita, "10", "40.00");
        compraService.darEntrada(c.id(), entrada(c));

        // CEN-NOVO-26 — uma linha por produto afetado, direto e indireto; preço de venda intacto
        confere(linhasDePreco(laco).get(0), "3.00", "6.00", "COMPRA_ENTRADA", c.identificador());
        confere(linhasDePreco(kit).get(0), "6.00", "12.00", "COMPRA_ENTRADA", c.identificador());
        assertEquals(0, new BigDecimal("4.00").compareTo(produtoRepository.findById(laco.getId()).orElseThrow().getPrecoVenda()));
        assertEquals(0, new BigDecimal("10.00").compareTo(produtoRepository.findById(kit.getId()).orElseThrow().getPrecoVenda()));

        // CEN-NOVO-30 — histórico do insumo: uma linha por evento, com os dois produtos
        List<MovimentacaoInsumoResponseDTO> hist = insumoService.listarMovimentacoes(fita.getId(), PageRequest.of(0, 50)).getContent();
        List<MovimentacaoInsumoResponseDTO> agregadas = hist.stream().filter(l -> "PRECO_SUGERIDO".equals(l.tipoLinha())).toList();
        assertEquals(1, agregadas.size());
        assertEquals(2, agregadas.get(0).precosSugeridos().quantidadeProdutos());
        assertEquals(c.identificador(), agregadas.get(0).precosSugeridos().referenciaTexto());
        assertEquals(List.of("Kit Laços", "Laço"), agregadas.get(0).precosSugeridos().produtos().stream().map(p -> p.nome()).toList());

        // CEN-NOVO-27 — cancelar a compra devolve o preço sugerido
        compraService.cancelar(c.id(), new CancelarCompraRequest("Compra feita por engano, mercadoria devolvida ao fornecedor", false));
        confere(linhasDePreco(laco).get(0), "6.00", "3.00", "COMPRA_CANCELAMENTO", c.identificador());
        assertEquals(2, linhasDePreco(laco).size());
    }

    @Test
    void cen26_produtoAlcancadoPorDoisInsumosDaMesmaCompraFicaComAsDuasOrigens() {
        Insumo fita = insumo("Fita", "0", "2.00");
        Insumo cola = insumo("Cola", "0", "1.00");
        Produto laco = produto("Laço", "3.00", "50", "5.00");
        ficha(laco, fita, null, "1");
        ficha(laco, cola, null, "1");

        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("10"), new BigDecimal("40.00")),
                        new CompraItemRequest(cola.getId(), null, new BigDecimal("10"), new BigDecimal("30.00"))))).compra();

        // custo 3,00 → 4,00 + 3,00 = 7,00 ; sugerido 4,50 → 10,50 (mudança total, não fracionada por insumo)
        confere(linhasDePreco(laco).get(0), "4.50", "10.50", "COMPRA_ENTRADA", c.identificador());
        for (Insumo i : List.of(fita, cola)) {
            var agregada = insumoService.listarMovimentacoes(i.getId(), PageRequest.of(0, 50)).getContent().stream()
                    .filter(l -> "PRECO_SUGERIDO".equals(l.tipoLinha())).toList();
            assertEquals(1, agregada.size());
            assertEquals(0, new BigDecimal("10.50").compareTo(agregada.get(0).precosSugeridos().produtos().get(0).novo()));
        }
    }

    @Test
    void cen28_cen31_cen29_edicaoDeFichaEDeMargemRegistramEValorIgualNaoGeraLinha() {
        Insumo fita = insumo("Fita de cetim", "100", "2.00");
        ProdutoDetalheResponse criado = produtoService.cadastrar(request("Laço", "50", "1"));
        Produto laco = produtoRepository.findById(criado.getId()).orElseThrow();
        assertTrue(linhasDePreco(laco).isEmpty(), "produto novo não tem linha");

        // CEN-NOVO-28 — 1,5 m de fita: sugerido 3,00 → 4,50
        produtoService.editar(laco.getId(), request("Laço", "50", "1.5"));
        confere(linhasDePreco(laco).get(0), "3.00", "4.50", "PRODUTO_EDICAO", "PRO-" + laco.getNumero());
        assertTrue(insumoService.listarMovimentacoes(fita.getId(), PageRequest.of(0, 50)).getContent().stream()
                .noneMatch(l -> "PRECO_SUGERIDO".equals(l.tipoLinha())), "o insumo não ganha linha na edição do produto");

        // CEN-NOVO-29 — só o nome muda: nenhuma linha nova
        produtoService.editar(laco.getId(), request("Laço grande", "50", "1.5"));
        assertEquals(1, linhasDePreco(laco).size());

        // CEN-NOVO-31 — margem 50% → 100%: 4,50 → 6,00
        produtoService.editar(laco.getId(), request("Laço grande", "100", "1.5"));
        confere(linhasDePreco(laco).get(0), "4.50", "6.00", "PRODUTO_MARGEM", "PRO-" + laco.getNumero());
        assertEquals(2, linhasDePreco(laco).size());
    }

    @Test
    void cen29_compraDeInsumoQueOProdutoNaoUsaNaoGeraLinha() {
        Insumo fita = insumo("Fita", "0", "2.00");
        Insumo cola = insumo("Cola", "0", "1.00");
        Produto laco = produto("Laço", "2.00", "50", "4.00");
        ficha(laco, fita, null, "1");

        compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, new BigDecimal("10"), new BigDecimal("50.00")))));

        assertTrue(linhasDePreco(laco).isEmpty());
    }

    @Test
    void valorDaHoraRegistraParaOsProdutosCujoPrecoSugeridoMudou() {
        Insumo fita = insumo("Fita", "100", "2.00");
        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(new BigDecimal("60.00"), new BigDecimal("50")));
        ProdutoDetalheResponse criado = produtoService.cadastrar(request("Laço", "50", "1")); // 1 min a R$ 60/h = R$ 1,00
        Produto laco = produtoRepository.findById(criado.getId()).orElseThrow();

        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(new BigDecimal("120.00"), new BigDecimal("50")));

        // custo 2,00 + 1,00 = 3,00 → 2,00 + 2,00 = 4,00 ; sugerido 4,50 → 6,00
        confere(linhasDePreco(laco).get(0), "4.50", "6.00", "VALOR_HORA", null);
        assertNotNull(fita);
    }

    private ProdutoRequest request(String nome, String margem, String quantidadeFita) {
        Insumo fita = insumoRepository.findAll().stream()
                .filter(i -> i.getUsuario().getId().equals(usuario.getId()) && i.getNome().startsWith("Fita")).findFirst().orElseThrow();
        ProdutoRequest r = new ProdutoRequest();
        r.setNome(nome);
        r.setTipo(TipoProduto.PRODUTO);
        r.setTempoProducao(1);
        r.setRendimento(BigDecimal.ONE);
        r.setMargemLucro(new BigDecimal(margem));
        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(fita.getId());
        item.setQuantidade(new BigDecimal(quantidadeFita));
        r.setFichaTecnica(List.of(item));
        return r;
    }
}
