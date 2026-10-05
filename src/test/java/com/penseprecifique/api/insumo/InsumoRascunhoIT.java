package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.produto.FichaTecnicaService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRascunhoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.16.0 (#687, RN-NOVA-18, RN-NOVA-19, DT-NOVA-12) — insumo em rascunho: criação a partir do item da
 * nota, conclusão, regra única de "insumo utilizável" em cada ponto de uso, exclusão e bloqueio de
 * confirmar compra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoRascunhoIT {

    @Autowired InsumoService insumoService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired CompraService compraService;
    @Autowired FichaTecnicaService fichaTecnicaService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;

    private Usuario usuario;
    private UnidadeMedida metro;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-rascunho-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metro = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("metro").sigla("m").build());
    }

    private InsumoResponseDTO rascunho(String nome, String unidade, String qtd, String valor) {
        return insumoService.criarRascunho(new InsumoRascunhoRequestDTO(nome, null, unidade,
                new BigDecimal(qtd), new BigDecimal(valor)), false);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    private CompraRequest compraCom(UUID insumoId) {
        return new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(insumoId, null, new BigDecimal("5"), new BigDecimal("12.50"))));
    }

    private InsumoRequestDTO completar(InsumoResponseDTO r, String preco, String qtd) {
        return new InsumoRequestDTO(r.nome(), null, metro.getId(), true, null, true, null, null, null, null,
                preco != null ? new BigDecimal(preco) : null, qtd != null ? new BigDecimal(qtd) : null);
    }

    @Test
    void cen27_unidadeDaNotaConhecidaPropoeUnidadeECustoEstoqueZero() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "M", "5", "12.50");

        assertTrue(r.rascunho());
        assertTrue(r.ativo());
        assertEquals("m", r.unidadeMedida());
        igual("2.50", r.custoUnitario());
        assertTrue(r.custoProposto(), "custo marcado como proposto, a revisar");
        igual("0", r.estoqueAtual());
        assertTrue(insumoService.listarMovimentacoes(r.id(), PageRequest.of(0, 10)).isEmpty(), "sem movimentação");
    }

    @Test
    void cen28_unidadeDaNotaDesconhecidaNasceSemUnidadeESemCusto() {
        InsumoResponseDTO r = rascunho("RESMA PAPEL SULFITE", "RM", "2", "60.00");

        assertTrue(r.rascunho());
        assertNull(r.unidadeMedida());
        assertNull(r.unidadeMedidaId());
        igual("0", r.custoUnitario());
        assertFalse(r.custoProposto());
    }

    @Test
    void simularSoPropoeSemGravar() {
        InsumoResponseDTO r = insumoService.criarRascunho(new InsumoRascunhoRequestDTO("FITA CETIM 10MM", null, "m",
                new BigDecimal("5"), new BigDecimal("12.50")), true);

        igual("2.50", r.custoUnitario());
        assertNull(r.id());
        assertEquals(0, insumoRepository.countByUsuarioIdAndDeletedAtIsNull(usuario.getId()));
    }

    @Test
    void rascunhoRespeitaUnicidadeNomeMarca() {
        rascunho("FITA CETIM 10MM", "m", "5", "12.50");
        assertThrows(BusinessException.class, () -> rascunho("FITA CETIM 10MM", "m", "1", "1.00"));
    }

    @Test
    void cen29_completarOCadastroAtivaOInsumo() {
        InsumoResponseDTO r = rascunho("RESMA PAPEL SULFITE", "RM", "2", "60.00");

        InsumoResponseDTO completo = insumoService.editar(r.id(), completar(r, "12.50", "5"));

        assertFalse(completo.rascunho());
        assertTrue(completo.ativo());
        assertEquals("m", completo.unidadeMedida());
        igual("2.50", completo.custoUnitario());
        igual("0", completo.estoqueAtual());
    }

    @Test
    void completarSemPrecoEQuantidadeBloqueiaEContinuaRascunho() {
        InsumoResponseDTO r = rascunho("RESMA PAPEL SULFITE", "RM", "2", "60.00");

        assertThrows(BusinessException.class, () -> insumoService.editar(r.id(), completar(r, null, null)));
        assertTrue(insumoRepository.findById(r.id()).orElseThrow().getRascunho());
    }

    @Test
    void listagemPadraoEscondeRascunhoEListagemDeInsumosMostra() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");

        assertTrue(insumoService.listar(null, null, PageRequest.of(0, 20)).getContent().stream()
                .noneMatch(i -> i.id().equals(r.id())), "seletores de ficha, catálogo e orçamento não veem o rascunho");
        assertTrue(insumoService.listar(null, null, false, true, PageRequest.of(0, 20)).getContent().stream()
                .anyMatch(i -> i.id().equals(r.id()) && i.rascunho()), "listagem de Insumos e compra veem com a marca");
    }

    @Test
    void cen51_rascunhoForaDeFichaTecnica() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Laço")
                .tipo(TipoProduto.PRODUTO).tempoProducao(30).ativo(true).precoCusto(BigDecimal.ZERO)
                .precoVenda(BigDecimal.TEN).build());
        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(r.id());
        item.setQuantidade(BigDecimal.ONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(produto, List.of(item), usuario.getId()));
        assertTrue(ex.getMessage().contains("rascunho"));
    }

    @Test
    void cen51_rascunhoForaDeItemDeCatalogoEDoOrcamento() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");
        CatalogoRequest cat = new CatalogoRequest();
        cat.setNome("Natal");
        UUID catalogoId = catalogoService.cadastrar(cat).getId();
        ItemCatalogoComponenteRequest componente = new ItemCatalogoComponenteRequest();
        componente.setInsumoId(r.id());
        componente.setQuantidade(BigDecimal.ONE);
        ItemCatalogoRequest item = new ItemCatalogoRequest();
        item.setNome("Kit laço");
        item.setTempoProducao(0);
        item.setComponentes(List.of(componente));

        BusinessException ex = assertThrows(BusinessException.class, () -> itemCatalogoService.adicionar(catalogoId, item));
        assertTrue(ex.getMessage().contains("rascunho"));
    }

    @Test
    void cen51_rascunhoNaoRecebeEntradaDeEstoqueNemPodeSerInativado() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");

        assertThrows(BusinessException.class, () -> insumoService.baixaManual(r.id(), new BaixaManualInsumoRequestDTO(
                TipoMovimentacaoInsumo.ENTRADA, BigDecimal.ONE, MotivoMovimentacaoInsumo.OUTRO,
                "Entrada manual de teste com mais de trinta caracteres.")));
        assertThrows(BusinessException.class, () -> insumoService.inativar(r.id()));
        Insumo salvo = insumoRepository.findById(r.id()).orElseThrow();
        assertTrue(salvo.getAtivo());
        igual("0", salvo.getEstoqueAtual());
    }

    @Test
    void cen50_compraComInsumoRascunhoSalvaMasNaoConfirma() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");

        CompraResponse compra = compraService.criarRascunho(compraCom(r.id()));
        BusinessException ex = assertThrows(BusinessException.class, () -> compraService.confirmar(compra.id(), null));

        assertEquals("Insumo em rascunho na compra", ex.getTitulo());
        assertEquals(List.of("FITA CETIM 10MM"), ex.getItens());
        assertEquals(StatusCompra.RASCUNHO, compraService.buscar(compra.id()).status());
        Insumo insumo = insumoRepository.findById(r.id()).orElseThrow();
        igual("0", insumo.getEstoqueAtual());
        igual("2.50", insumo.getCustoUnitario());
    }

    @Test
    void cen50_depoisDeCompletarOInsumoACompraConfirma() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");
        CompraResponse compra = compraService.criarRascunho(compraCom(r.id()));

        insumoService.editar(r.id(), completar(r, "12.50", "5"));

        assertEquals(StatusCompra.CONFIRMADA, compraService.confirmar(compra.id(), null).compra().status());
    }

    @Test
    void cen52_excluirRascunhoSemCompraESimples() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");

        insumoService.excluir(r.id());

        assertTrue(insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(r.id(), usuario.getId()).isEmpty());
    }

    @Test
    void cen56_excluirRascunhoEmCompraSalvaBloqueiaListandoACompraEDepoisPermite() {
        InsumoResponseDTO r = rascunho("FITA CETIM 10MM", "m", "5", "12.50");
        CompraResponse compra = compraService.criarRascunho(compraCom(r.id()));

        BusinessException ex = assertThrows(BusinessException.class, () -> insumoService.excluir(r.id()));
        assertEquals(List.of(compra.identificador()), ex.getItens());
        assertTrue(insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(r.id(), usuario.getId()).isPresent());
        assertEquals(1, compraService.buscar(compra.id()).itens().size());

        Insumo outro = insumoRepository.save(Insumo.builder().usuario(usuario).numero(99).nome("Fita outra")
                .unidadeMedida(metro).custoUnitario(BigDecimal.ONE).ativo(true).build());
        compraService.atualizarRascunho(compra.id(), compraCom(outro.getId()));

        insumoService.excluir(r.id());
        assertTrue(insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(r.id(), usuario.getId()).isEmpty());
    }
}
