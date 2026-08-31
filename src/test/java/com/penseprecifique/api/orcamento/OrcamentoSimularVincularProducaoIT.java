package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
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
 * RN-PROD-VINC-03 (V0.8.2, #320) — POST /orcamentos/{id}/simular-vincular-producao: alerta
 * combinado de insumo/rendimento somando produtos já persistidos na produção com os produtos do
 * orçamento, sem persistir nada. Reaproveita o mesmo cenário numérico de
 * {@code ProducaoSimularAlertasIT} (insumo "Papel", cada produto consome 3 por unidade de
 * rendimento) para provar equivalência com o alerta isolado quando a produção está vazia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoSimularVincularProducaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private UUID produtoAId;
    private UUID produtoBId;
    private int proximoNumeroProducao = 1;

    private void seed(BigDecimal estoquePapel, boolean permitirEstoqueNegativo) {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-sim-vinc-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Simular Vínculo").ativa(true).build());

        Insumo papel = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Papel").marca("X").unidadeMedida("un")
                .estoqueAtual(estoquePapel).permitirEstoqueNegativo(permitirEstoqueNegativo)
                .fracionavel(true).build());

        Produto produtoA = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto A").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        Produto produtoB = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Produto B").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoA).insumo(papel).quantidade(new BigDecimal("3")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoB).insumo(papel).quantidade(new BigDecimal("3")).build());

        produtoAId = produtoA.getId();
        produtoBId = produtoB.getId();
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private UUID criarOrcamentoComProduto(UUID produtoId, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produtoId);
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(quantidade);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    private AlertaInsumoResponse papelDe(List<AlertaInsumoResponse> alertas) {
        return alertas.stream().filter(a -> a.getNomeInsumo().equals("Papel")).findFirst()
                .orElseThrow(() -> new AssertionError("Alerta de Papel não retornado"));
    }

    /** Caso 1 — produção vazia: alerta combinado deve ser idêntico ao alerta isolado do orçamento. */
    @Test
    void producaoVaziaProduzAlertaEquivalenteAoIsolado() {
        seed(new BigDecimal("5"), true);
        UUID orcamentoId = criarOrcamentoComProduto(produtoAId, 1);
        Producao producao = novaProducao();

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        List<AlertaInsumoResponse> combinado = orcamentoService.simularVincularProducao(orcamentoId, req);

        ProducaoProdutoRequest isoladoRequest = new ProducaoProdutoRequest();
        isoladoRequest.setProdutoId(produtoAId);
        isoladoRequest.setQuantidade(BigDecimal.ONE);
        List<AlertaInsumoResponse> isolado = producaoService.simularAlertas(List.of(isoladoRequest));

        assertEquals(0, papelDe(isolado).getQuantidadeNecessaria().compareTo(papelDe(combinado).getQuantidadeNecessaria()));
        assertEquals(papelDe(isolado).getSituacao(), papelDe(combinado).getSituacao());
        assertEquals(0, new BigDecimal("3").compareTo(papelDe(combinado).getQuantidadeNecessaria()));
    }

    /** Caso 2 — produção com produto existente + item novo do orçamento: soma, não isola. */
    @Test
    void producaoComProdutoExistenteSomaComItemNovoDoOrcamento() {
        seed(new BigDecimal("5"), true);
        Producao producao = novaProducao();
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produtoRepository.findById(produtoBId).orElseThrow())
                .quantidade(BigDecimal.ONE).build());

        UUID orcamentoId = criarOrcamentoComProduto(produtoAId, 1);
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        List<AlertaInsumoResponse> alertas = orcamentoService.simularVincularProducao(orcamentoId, req);

        AlertaInsumoResponse papel = papelDe(alertas);
        assertEquals(0, new BigDecimal("6").compareTo(papel.getQuantidadeNecessaria()),
                "consumo do produto já persistido (3) + produto novo do orçamento (3) deve somar 6");
        assertEquals(SituacaoAlertaInsumo.AVISO, papel.getSituacao());
    }

    /** Caso 3 — estoque suficiente para a soma inteira: nenhum alerta de bloqueio/aviso, feliz caminho. */
    @Test
    void estoqueSuficienteParaSomaNaoGeraAlertaDeFalta() {
        seed(new BigDecimal("100"), true);
        Producao producao = novaProducao();
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produtoRepository.findById(produtoBId).orElseThrow())
                .quantidade(BigDecimal.ONE).build());

        UUID orcamentoId = criarOrcamentoComProduto(produtoAId, 1);
        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        List<AlertaInsumoResponse> alertas = orcamentoService.simularVincularProducao(orcamentoId, req);

        AlertaInsumoResponse papel = papelDe(alertas);
        assertEquals(SituacaoAlertaInsumo.SUFICIENTE, papel.getSituacao());
        assertEquals(0, new BigDecimal("6").compareTo(papel.getQuantidadeNecessaria()));
    }

    /** RN-PROD-VINC-02 — mesma restrição de estado do vínculo real: preview nunca promete o que não pode persistir. */
    @Test
    void producaoForaDeAguardandoInicioBloqueiaPreview() {
        seed(new BigDecimal("5"), true);
        UUID orcamentoId = criarOrcamentoComProduto(produtoAId, 1);
        Producao producao = producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(proximoNumeroProducao++).estado(EstadoProducao.EM_ANDAMENTO).build());

        VincularProducaoRequest req = new VincularProducaoRequest();
        req.setProducaoId(producao.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.simularVincularProducao(orcamentoId, req));
        assertTrue(ex.getMessage().toLowerCase().contains("já começou"));
    }
}
