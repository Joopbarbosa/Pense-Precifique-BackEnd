package com.penseprecifique.api.pdf;

import com.penseprecifique.api.orcamento.OrcamentoService;
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
import com.penseprecifique.api.pdf.OrcamentoPdfPayloadService;
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
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCondicoesPayload;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.testsupport.MetodosPagamentoTeste;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

/** V0.19.0 #445 (CEN-NOVO-51 e 52): página 2 do PDF montada pelo serviço real, com banco, antes e depois da aprovação. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoPdfCondicoesIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoPdfPayloadService payloadService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private UUID orcamentoId;

    @BeforeEach
    void preparar() {
        usuario = usuarioRepository.save(Usuario.builder().email("pdf-cond-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Cliente").ativa(true).build());
        MetodosPagamentoTeste.pix(metodos, usuario);
        MetodosPagamentoTeste.criar(metodos, usuario, TipoMetodoPagamento.DINHEIRO, false);
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Produto").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("300.00")).build());
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
        orcamentoId = orcamentoService.criar(req).getId();
    }

    private PdfMicroservicoCondicoesPayload condicoes() {
        return payloadService.montarPayloadOrcamento(orcamentoId).getDocumento().getCondicoes();
    }

    @Test
    void cen51_antesDaAprovacaoListaSoMetodosAtivosEAvisoFixo() {
        PdfMicroservicoCondicoesPayload rascunho = condicoes();
        assertEquals("ANTES_APROVACAO", rascunho.getModo());
        assertEquals(List.of("Pix"), rascunho.getFormasAceitas());
        assertEquals(PdfMapper.AVISO_CONDICOES_ANTES_APROVACAO, rascunho.getAviso());
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        PdfMicroservicoCondicoesPayload enviado = condicoes();
        assertEquals("ANTES_APROVACAO", enviado.getModo());
        assertEquals(List.of("Pix"), enviado.getFormasAceitas());
    }

    @Test
    void cen52_depoisDaAprovacaoMostraOCombinadoLidoDasParcelas() {
        orcamentoService.avancarStatus(orcamentoId, new AvancaStatusRequest());
        PagamentoOrcamentoRequest p = new PagamentoOrcamentoRequest();
        p.setMetodoPagamentoId(metodos.findByUsuarioIdOrderByOrdemAscTipoAsc(usuario.getId()).stream()
                .filter(m -> m.getTipo() == TipoMetodoPagamento.PIX).findFirst().orElseThrow().getId());
        p.setTemSinal(true);
        p.setTipoSinal(PagamentoOrcamentoRequest.TipoSinal.VALOR);
        p.setValorSinal(new BigDecimal("90.00"));
        p.setSinalAte(LocalDate.now().plusDays(5));
        AvancaStatusRequest r = new AvancaStatusRequest();
        r.setPagamento(p);
        orcamentoService.avancarStatus(orcamentoId, r);

        PdfMicroservicoCondicoesPayload c = condicoes();
        assertEquals("APROVADO", c.getModo());
        assertEquals("Pix", c.getForma());
        assertTrue(c.getSinal().contains("90,00"), c.getSinal());
        assertNotNull(c.getSinalAte());
        assertTrue(c.getSaldo().contains("210,00"), c.getSaldo());
        assertTrue(c.getRecebido().contains("0,00"));
        assertNull(c.getFormasAceitas());
    }
}
