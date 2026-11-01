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

/** #1174 (RN-NOVA-38, CEN-47): aprovar e gerar as parcelas são uma transação só; se gerar falha, a aprovação desfaz. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoAprovacaoAtomicaIT {

    @Autowired OrcamentoService orcamentoService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean LancamentoFinanceiroService financeiro;
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

    @Test
    void falhaAoGerarAsParcelasDesfazAAprovacao() {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("300.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        UUID id = orcamentoService.criar(req).getId();
        orcamentoService.avancarStatus(id, new AvancaStatusRequest());
        PagamentoOrcamentoRequest p = new PagamentoOrcamentoRequest();
        p.setMetodoPagamentoId(pix.getId());
        p.setTemSinal(true);
        p.setTipoSinal(PagamentoOrcamentoRequest.TipoSinal.VALOR);
        p.setValorSinal(new BigDecimal("90.00"));
        p.setSinalAte(LocalDate.now().plusDays(5));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha simulada")).when(financeiro)
                .gerarParcelasDoOrcamento(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        AvancaStatusRequest r = new AvancaStatusRequest();
        r.setPagamento(p);
        assertThrows(IllegalStateException.class, () -> orcamentoService.avancarStatus(id, r));
        var o = orcamentos.findById(id).orElseThrow();
        assertEquals(StatusOrcamento.ENVIADO, o.getStatus());
        assertFalse(o.getSinalAtivo());
        assertNull(o.getMetodoPagamento());
        assertTrue(lancamentos.findByOrcamentoIdAndUsuarioIdAndSubtipoIsNull(id, usuario.getId()).isEmpty());
    }
}
