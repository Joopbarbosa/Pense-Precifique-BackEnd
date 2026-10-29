package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.QuantidadeProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.SugestaoQuantidadeProducaoResponse;
import com.penseprecifique.api.shared.dto.request.orcamento.DecisaoProducaoCancelamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-39 (V0.18.0, #1166) — produção criada a partir do orçamento com produto de insumo não
 * fracionável pergunta a quantidade a produzir (múltiplo do rendimento, mínimo = cobre o pedido).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoQuantidadeProducaoNaoFracionavelIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-qtd-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Desvincular Produção").ativa(true).build());
    }

    private Produto novoProduto(String nome, boolean naoFracionavel, String rendimento) {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(BigDecimal.ZERO)
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal(rendimento))
                .precoVenda(new BigDecimal("50.00")).build());
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida(unidadeMedida("g"))
                .estoqueAtual(new BigDecimal("100000")).permitirEstoqueNegativo(true).fracionavel(!naoFracionavel)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private OrcamentoItemRequest itemDe(Produto produto, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(quantidade);
        return item;
    }

    private UUID orcamentoCom(OrcamentoItemRequest... itens) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(itens));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private QuantidadeProducaoRequest qtd(Produto p, String valor) {
        QuantidadeProducaoRequest q = new QuantidadeProducaoRequest();
        q.setProdutoId(p.getId());
        q.setQuantidade(valor == null ? null : new BigDecimal(valor));
        return q;
    }

    private CriarProducaoVinculadaRequest criar(QuantidadeProducaoRequest... quantidades) {
        CriarProducaoVinculadaRequest r = new CriarProducaoVinculadaRequest();
        r.setDataTerminoPrevista(java.time.LocalDate.now().plusDays(3));
        if (quantidades.length > 0) {
            r.setQuantidadesProducao(List.of(quantidades));
        }
        return r;
    }

    private BigDecimal quantidadeProduzida(UUID orcamentoId, Produto produto) {
        UUID producaoId = orcamentoProducaoRepository.findByOrcamentoId(orcamentoId).get(0).getProducao().getId();
        return producaoProdutoRepository.findByProducaoId(producaoId).stream()
                .filter(pp -> pp.getProduto().getId().equals(produto.getId()))
                .map(ProducaoProduto::getQuantidade).findFirst().orElseThrow();
    }

    private void assertQtd(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " obtido " + real);
    }

    @Test
    void cen134_pedidoDe5BrownieSugere12ESobram7() {
        seedUsuarioECliente();
        Produto brownie = novoProduto("Brownie recheado", true, "12");
        UUID orc = orcamentoCom(itemDe(brownie, 5));

        List<SugestaoQuantidadeProducaoResponse> sugestoes = orcamentoService.sugerirQuantidadesProducao(orc, null);
        assertEquals(1, sugestoes.size());
        assertQtd("12", sugestoes.get(0).getQuantidadeSugerida());
        assertQtd("7", sugestoes.get(0).getSobra());

        orcamentoService.criarProducaoVinculada(orc, criar());
        assertQtd("12", quantidadeProduzida(orc, brownie));
    }

    @Test
    void cen135_pedido13Sugere24_eAceitaOMultiploIgualAoPedido() {
        seedUsuarioECliente();
        Produto brownie = novoProduto("Brownie recheado", true, "12");
        UUID orc13 = orcamentoCom(itemDe(brownie, 13));
        SugestaoQuantidadeProducaoResponse s = orcamentoService.sugerirQuantidadesProducao(orc13, null).get(0);
        assertQtd("24", s.getQuantidadeSugerida());
        assertQtd("11", s.getSobra());

        UUID orc24 = orcamentoCom(itemDe(brownie, 24));
        orcamentoService.criarProducaoVinculada(orc24, criar(qtd(brownie, "24")));
        assertQtd("24", quantidadeProduzida(orc24, brownie));
        assertQtd("0", orcamentoService.sugerirQuantidadesProducao(orc24, null).get(0).getSobra());
    }

    @Test
    void cen136_quantidadeForaDoMultiploOuAbaixoDoMinimoBloqueia() {
        seedUsuarioECliente();
        Produto brownie = novoProduto("Brownie recheado", true, "12");
        UUID orc = orcamentoCom(itemDe(brownie, 13));

        BusinessException e20 = assertThrows(BusinessException.class,
                () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "20"))));
        assertEquals("Produto Brownie recheado exige quantidade em múltiplos de 12 unidades (mínimo 24)", e20.getMessage());
        assertThrows(BusinessException.class, () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "6"))));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orc).isEmpty());
        assertTrue(producaoRepository.findAll().stream().noneMatch(p -> p.getUsuario().getId().equals(usuario.getId())));
    }

    @Test
    void cen137_doisNaoFracionaveisPerguntamPorProduto_fracionavelNaoPergunta() {
        seedUsuarioECliente();
        Produto brownie = novoProduto("Brownie recheado", true, "12");
        Produto cookie = novoProduto("Cookie", true, "8");
        Produto bolo = novoProduto("Bolo", false, "10");
        UUID orc = orcamentoCom(itemDe(brownie, 5), itemDe(cookie, 9), itemDe(bolo, 3));

        List<SugestaoQuantidadeProducaoResponse> sugestoes = orcamentoService.sugerirQuantidadesProducao(orc, null);
        assertEquals(2, sugestoes.size());
        assertTrue(sugestoes.stream().noneMatch(s -> s.getProdutoId().equals(bolo.getId())));

        orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "24")));
        assertQtd("24", quantidadeProduzida(orc, brownie));
        assertQtd("16", quantidadeProduzida(orc, cookie));
        assertQtd("3", quantidadeProduzida(orc, bolo));
    }

    @Test
    void entradaHostil_nuncaVira500() {
        seedUsuarioECliente();
        Produto brownie = novoProduto("Brownie recheado", true, "12");
        Produto bolo = novoProduto("Bolo", false, "10");
        Produto fora = novoProduto("Fora do pedido", true, "12");
        UUID orc = orcamentoCom(itemDe(brownie, 5), itemDe(bolo, 3));

        // produto de fora do pedido, quantidade para fracionável, duplicada
        assertThrows(BusinessException.class, () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(fora, "12"))));
        assertThrows(BusinessException.class, () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(bolo, "10"))));
        assertThrows(BusinessException.class,
                () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "12"), qtd(brownie, "24"))));
        // zero, negativo, enorme e nulo: validados pelo Bean Validation no controller; no serviço, valor
        // absurdo (cai fora do estoque ou do múltiplo) também é recusado com BusinessException
        assertThrows(BusinessException.class, () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "0"))));
        assertThrows(BusinessException.class, () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "-12"))));
        assertThrows(BusinessException.class,
                () -> orcamentoService.criarProducaoVinculada(orc, criar(qtd(brownie, "999999999999"))));
        // orçamento de outra conta
        UUID outro = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> orcamentoService.sugerirQuantidadesProducao(outro, null));
        assertTrue(orcamentoProducaoRepository.findByOrcamentoId(orc).isEmpty());
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
