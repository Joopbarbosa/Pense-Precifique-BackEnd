package com.penseprecifique.api.producao;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.TipoEventoHistoricoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-PROD-HIST-01 (V0.8.2, #320) — HistoricoStatusProducao passa a registrar 2 tipos de evento
 * (STATUS/ITEM_ADICIONADO). Cobre os 4 casos do prompt P-B014: STATUS ok (sem regressão),
 * ITEM_ADICIONADO ok, STATUS sem status_novo falha, ITEM_ADICIONADO incompleto falha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HistoricoStatusProducaoAmpliadoIT {

    @Autowired ProducaoService producaoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired HistoricoStatusProducaoRepository historicoStatusProducaoRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("hist-producao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("50.00")).build());
    }

    /** Produto pronto para entrar numa produção via criarProducao() — exige ficha técnica + rendimento. */
    private Produto novoProdutoComFichaTecnica(int numero) {
        Produto produto = novoProduto(numero);
        produto.setRendimento(new BigDecimal("10"));
        produto = produtoRepository.save(produto);

        Insumo farinha = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome("Insumo " + numero).marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(farinha).quantidade(new BigDecimal("1")).build());
        return produto;
    }

    private Producao novaProducao() {
        return producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(1).estado(EstadoProducao.AGUARDANDO_INICIO).build());
    }

    private UUID novoOrcamento(Produto produto) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(1);

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Historico Producao").ativa(true).build());

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(false);
        req.setItens(List.of(item));
        req.setSinalAtivo(false);
        return orcamentoService.criar(req).getId();
    }

    @Test
    void criacaoDeProducaoGravaLinhaStatusSemRegressao() {
        seedUsuario();
        Produto produto = novoProdutoComFichaTecnica(1);

        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(produto.getId());
        item.setQuantidade(new BigDecimal("5"));
        criar.setProdutos(List.of(item));

        UUID producaoId = producaoService.criarProducao(criar).getId();

        List<HistoricoStatusProducao> historico =
                historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producaoId);

        assertEquals(1, historico.size());
        HistoricoStatusProducao linha = historico.get(0);
        assertEquals(TipoEventoHistoricoProducao.STATUS, linha.getTipoEvento());
        assertEquals(EstadoProducao.AGUARDANDO_INICIO, linha.getStatusNovo());
        assertEquals(OrigemHistoricoStatus.USUARIO, linha.getOrigem());
    }

    @Test
    void gravaLinhaItemAdicionadoComTodosOsCamposNovos() {
        seedUsuario();
        Produto produto = novoProduto(1);
        Producao producao = novaProducao();
        UUID orcamentoId = novoOrcamento(produto);

        HistoricoStatusProducao linha = historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .tipoEvento(TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                .produto(produto)
                .quantidade(new BigDecimal("3"))
                .referenciaOrcamento(orcamentoRepository.findById(orcamentoId).orElseThrow())
                .origem(OrigemHistoricoStatus.USUARIO)
                .build());

        assertNotNull(linha.getId());
        assertEquals(TipoEventoHistoricoProducao.ITEM_ADICIONADO, linha.getTipoEvento());
        assertEquals(produto.getId(), linha.getProduto().getId());
        assertEquals(new BigDecimal("3"), linha.getQuantidade());
        assertEquals(orcamentoId, linha.getReferenciaOrcamento().getId());
        assertEquals(null, linha.getStatusNovo());
    }

    @Test
    void statusSemStatusNovoFalhaPelaConstraint() {
        seedUsuario();
        Producao producao = novaProducao();

        assertThrows(DataIntegrityViolationException.class, () ->
                historicoStatusProducaoRepository.saveAndFlush(HistoricoStatusProducao.builder()
                        .producao(producao)
                        .tipoEvento(TipoEventoHistoricoProducao.STATUS)
                        .statusNovo(null)
                        .origem(OrigemHistoricoStatus.USUARIO)
                        .build()));
    }

    @Test
    void itemAdicionadoIncompletoFalhaPelaConstraint() {
        seedUsuario();
        Producao producao = novaProducao();

        assertThrows(DataIntegrityViolationException.class, () ->
                historicoStatusProducaoRepository.saveAndFlush(HistoricoStatusProducao.builder()
                        .producao(producao)
                        .tipoEvento(TipoEventoHistoricoProducao.ITEM_ADICIONADO)
                        .origem(OrigemHistoricoStatus.USUARIO)
                        .build()));
    }
}
