package com.penseprecifique.api.producao;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
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
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoOrcamentoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-15 (V0.8.3, #375+308) — seção "Orçamentos vinculados" no Detalhe de Produção:
 * {@code ProducaoDetalheResponse.orcamentosVinculados}, populado via
 * {@code OrcamentoProducaoRepository.findByProducaoId}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoOrcamentosVinculadosIT {

    @Autowired ProducaoService producaoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired OrcamentoProducaoRepository orcamentoProducaoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroProducao = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-orcamentos-vinculados-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Vínculos Produção").ativa(true).build());
    }

    private Produto novoProduto() {
        int numero = proximoNumeroProduto++;
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).rendimento(new BigDecimal("10"))
                .precoVenda(new BigDecimal("50.00")).build());

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private UUID criarOrcamentoComProduto(Produto produto, int quantidade, BigDecimal precoUnitario) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(precoUnitario);
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private void vincular(UUID orcamentoId, Producao producao) {
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        orcamentoService.vincularProducao(orcamentoId, req);
    }

    /** Sem vínculo — lista vazia, Frontend oculta a seção. */
    @Test
    void producaoSemVinculoRetornaListaVazia() {
        seedUsuarioECliente();
        Producao producao = novaProducao();

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertTrue(response.getOrcamentosVinculados().isEmpty());
    }

    /** 1 vínculo — campos corretos (orcamentoId, identificador ORC-N, status, cliente, valor). */
    @Test
    void producaoComUmVinculoExpoeCamposCorretos() {
        seedUsuarioECliente();
        Produto produto = novoProduto();
        UUID orcamentoId = criarOrcamentoComProduto(produto, 4, new BigDecimal("25.00"));
        Producao producao = novaProducao();
        vincular(orcamentoId, producao);

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertEquals(1, response.getOrcamentosVinculados().size());
        ProducaoOrcamentoResponse vinculo = response.getOrcamentosVinculados().get(0);
        assertEquals(orcamentoId, vinculo.getOrcamentoId());
        assertTrue(vinculo.getIdentificadorOrcamento().startsWith("ORC-"));
        assertEquals(StatusOrcamento.RASCUNHO, vinculo.getStatusOrcamento());
        assertEquals("Cliente Vínculos Produção", vinculo.getNomeCliente());
        assertEquals(0, new BigDecimal("100.00").compareTo(vinculo.getValorTotal()),
                "valorTotal deve refletir Orcamento.total (4 x 25.00)");
    }

    /** N vínculos — lista completa, sem paginação/limite, mesmo padrão de "Produções relacionadas". */
    @Test
    void producaoComMultiplosVinculosListaTodosSemLimite() {
        seedUsuarioECliente();
        Produto produtoA = novoProduto();
        Produto produtoB = novoProduto();
        UUID orcamentoA = criarOrcamentoComProduto(produtoA, 2, new BigDecimal("10.00"));
        UUID orcamentoB = criarOrcamentoComProduto(produtoB, 3, new BigDecimal("15.00"));
        Producao producao = novaProducao();
        vincular(orcamentoA, producao);
        vincular(orcamentoB, producao);

        ProducaoDetalheResponse response = producaoService.buscarPorId(producao.getId());

        assertEquals(2, response.getOrcamentosVinculados().size());
        assertTrue(response.getOrcamentosVinculados().stream()
                .anyMatch(v -> v.getOrcamentoId().equals(orcamentoA)));
        assertTrue(response.getOrcamentosVinculados().stream()
                .anyMatch(v -> v.getOrcamentoId().equals(orcamentoB)));
    }
}
