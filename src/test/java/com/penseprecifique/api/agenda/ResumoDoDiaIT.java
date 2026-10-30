package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.agenda.CompromissoUsuariaRequest;
import com.penseprecifique.api.shared.dto.response.agenda.ResumoDoDiaResponse;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.18.0 (#1071, RN-NOVA-27) — Resumo do dia: contagens, "a pagar" indisponível e insumos que as produções do dia precisam. CEN-NOVO-107 a 112. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ResumoDoDiaIT {

    @Autowired ResumoDoDiaService resumoService;
    @Autowired CompromissoUsuariaService compromissos;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private LocalDate hoje;
    private int numero = 1;
    private final java.util.Map<String, UnidadeMedida> unidades = new java.util.HashMap<>();

    private void seed() {
        hoje = LocalDate.now(clock);
        unidades.clear();
        usuario = usuarioRepository.save(Usuario.builder().email("resumo-dia-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private void avulso(String titulo, LocalDate data) {
        compromissos.criar(new CompromissoUsuariaRequest(titulo, data, null, null, null, null, null, null));
    }

    private Insumo insumo(String nome, String estoque) {
        UnidadeMedida un = unidades.computeIfAbsent("g", sigla -> unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("grama").sigla(sigla).build()));
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).marca("X").unidadeMedida(un)
                .estoqueAtual(new BigDecimal(estoque)).permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private Produto produtoCom(Insumo insumo, String quantidadePorLote) {
        Produto p = produtoRepository.save(Produto.builder().usuario(usuario).numero(numero++).nome("Bolo " + insumo.getNome()).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(BigDecimal.ZERO).permitirEstoqueNegativo(true).rendimento(BigDecimal.ONE)
                .precoVenda(new BigDecimal("50.00")).build());
        fichaRepository.save(FichaTecnicaItem.builder().produto(p).insumo(insumo).quantidade(new BigDecimal(quantidadePorLote)).build());
        return p;
    }

    private void producaoQueComeca(int emDias, Produto produto) {
        Producao pr = producaoRepository.save(Producao.builder().usuario(usuario).numero(numero++).estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(hoje.plusDays(emDias)).dataTerminoPrevista(hoje.plusDays(emDias + 2)).build());
        producaoProdutoRepository.save(ProducaoProduto.builder().producao(pr).produto(produto).quantidade(BigDecimal.ONE).build());
    }

    /** CEN-NOVO-107 — conta o de hoje e o atrasado; o futuro fica fora. */
    @Test
    void contaHojeEAtrasadosSemOFuturo() {
        seed();
        avulso("Ontem", hoje.minusDays(1));
        avulso("Hoje", hoje);
        avulso("Amanhã", hoje.plusDays(1));
        ResumoDoDiaResponse r = resumoService.resumo();
        assertEquals(hoje, r.data());
        assertEquals(1, r.atrasados());
        assertEquals(1, r.paraHoje());
        assertEquals(List.of("Ontem", "Hoje"), r.outros().stream().map(c -> c.titulo()).toList());
    }

    /** CEN-NOVO-108 — "a pagar" já existe no resumo, indisponível e sem valores, até o Financeiro. */
    @Test
    void aPagarFicaIndisponivel() {
        seed();
        ResumoDoDiaResponse r = resumoService.resumo();
        assertFalse(r.aPagar().disponivel());
        assertNull(r.aPagar().total());
        assertTrue(r.aPagar().itens().isEmpty());
        assertTrue(r.aReceber().disponivel());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.aReceber().total()));
    }

    /** CEN-NOVO-109 — insumo que a produção do dia precisa e o estoque não cobre aparece com a falta; o que cobre, não. */
    @Test
    void insumoQueNaoCobreAProducaoDoDiaApareceComAFalta() {
        seed();
        producaoQueComeca(0, produtoCom(insumo("Farinha", "300"), "500"));
        producaoQueComeca(0, produtoCom(insumo("Açúcar", "900"), "100"));
        List<ResumoDoDiaResponse.InsumoFaltante> faltas = resumoService.resumo().insumosFaltantes();
        assertEquals(1, faltas.size());
        assertEquals("Farinha", faltas.get(0).nome());
        assertEquals("g", faltas.get(0).siglaUnidade());
        assertEquals(0, new BigDecimal("200").compareTo(faltas.get(0).falta()));
    }

    /** CEN-NOVO-110 — produção que começa depois de hoje não entra nos insumos do dia. */
    @Test
    void producaoFuturaNaoEntraNosInsumos() {
        seed();
        producaoQueComeca(3, produtoCom(insumo("Chocolate", "1"), "500"));
        assertTrue(resumoService.resumo().insumosFaltantes().isEmpty());
    }

    /** CEN-NOVO-111 — produção atrasada (devia ter começado) entra nos insumos. */
    @Test
    void producaoAtrasadaEntraNosInsumos() {
        seed();
        producaoQueComeca(-2, produtoCom(insumo("Leite", "10"), "50"));
        assertEquals(1, resumoService.resumo().insumosFaltantes().size());
    }

    /** CEN-NOVO-112 — o resumo é de cada usuária: o atrasado de outra conta não aparece. */
    @Test
    void naoMostraDadosDeOutraConta() {
        seed();
        avulso("Minha", hoje);
        Usuario primeira = usuario;
        seed();
        ResumoDoDiaResponse r = resumoService.resumo();
        assertEquals(0, r.paraHoje() + r.atrasados());
        assertTrue(r.outros().isEmpty());
        assertFalse(primeira.getId().equals(usuario.getId()));
    }
}
