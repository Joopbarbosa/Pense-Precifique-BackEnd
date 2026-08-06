package com.penseprecifique.api.util;

import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.produto.ProdutoService;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #161 — proximoNumero() era um MAX(numero)+1 sem lock em 6 pontos reais (não 4: NumeroSequencialUtil
 * é só a conta pura, reaproveitada por Cliente/Produto/Insumo, mas cada um tem seu próprio
 * findTopByUsuarioIdOrderByNumeroDesc que precisou do @Lock/lockPorId individualmente — ver relato).
 * Duas requisições concorrentes do mesmo usuário podiam ler o mesmo MAX antes de qualquer salvar,
 * colidindo na UNIQUE(usuario_id, numero). Cada teste dispara N pares de chamadas verdadeiramente
 * concorrentes (CyclicBarrier força as duas a chamarem o Service no mesmo instante) e confirma que
 * todos os números gerados pro mesmo usuário são únicos e nenhuma chamada falha por colisão.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NumeroSequencialConcorrenciaIT {

    private static final int PARES = 8;

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteService clienteService;
    @Autowired ProdutoService produtoService;
    @Autowired InsumoService insumoService;
    @Autowired CatalogoService catalogoService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired ProducaoService producaoService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired InsumoRepository insumoRepository;

    private Usuario novoUsuario(String prefixo) {
        return usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
    }

    /** Roda PARES rodadas de 2 chamadas concorrentes (mesmo usuário), retorna todos os números gerados. */
    private List<Integer> executarConcorrente(String email, Callable<Integer> acaoQueRetornaNumero) throws Exception {
        List<Integer> numeros = new ArrayList<>();
        for (int rodada = 0; rodada < PARES; rodada++) {
            CyclicBarrier barreira = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(email, null, List.of()));
                    barreira.await();
                    return acaoQueRetornaNumero.call();
                }));
            }
            for (Future<Integer> f : futures) {
                numeros.add(f.get());
            }
            pool.shutdown();
        }
        return numeros;
    }

    private void assertSemColisao(List<Integer> numeros) {
        assertEquals(PARES * 2, numeros.size(), "todas as chamadas deveriam ter retornado um número");
        assertEquals(numeros.size(), java.util.Set.copyOf(numeros).size(),
                "números duplicados gerados sob concorrência: " + numeros);
    }

    @Test
    void clienteSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("cli-conc");
        AtomicInteger contador = new AtomicInteger();
        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            ClienteRequest req = new ClienteRequest();
            req.setNome("Cliente Concorrente " + contador.incrementAndGet() + "-" + UUID.randomUUID());
            return clienteService.cadastrar(req).getNumero();
        });
        assertSemColisao(numeros);
    }

    @Test
    void produtoSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("prod-conc");
        AtomicInteger contador = new AtomicInteger();
        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            ProdutoRequest req = new ProdutoRequest();
            req.setNome("Produto Concorrente " + contador.incrementAndGet() + "-" + UUID.randomUUID());
            req.setTipo(TipoProduto.PRODUTO);
            req.setTempoProducao(30);
            return produtoService.cadastrar(req).getNumero();
        });
        assertSemColisao(numeros);
    }

    @Test
    void insumoSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("ins-conc");
        AtomicInteger contador = new AtomicInteger();
        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            InsumoCreateRequestDTO req = new InsumoCreateRequestDTO(
                    "Insumo Concorrente " + contador.incrementAndGet() + "-" + UUID.randomUUID(),
                    "Marca", "un", true, null, true, null,
                    new BigDecimal("10.00"), new BigDecimal("1"));
            return insumoService.cadastrar(req).numero();
        });
        assertSemColisao(numeros);
    }

    @Test
    void catalogoSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("cat-conc");
        AtomicInteger contador = new AtomicInteger();
        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            CatalogoRequest req = new CatalogoRequest();
            req.setNome("Catálogo Concorrente " + contador.incrementAndGet() + "-" + UUID.randomUUID());
            req.setMargem(new BigDecimal("50"));
            return catalogoService.cadastrar(req).getNumero();
        });
        assertSemColisao(numeros);
    }

    @Test
    void producaoSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("prd-conc");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Concorrência").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).rendimento(BigDecimal.TEN).build());
        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha Concorrência").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(new BigDecimal("1")).build());

        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            CriarProducaoRequest req = new CriarProducaoRequest();
            req.setDataTerminoPrevista(LocalDate.now().plusDays(7));
            ProducaoProdutoRequest item = new ProducaoProdutoRequest();
            item.setProdutoId(produto.getId());
            item.setQuantidade(new BigDecimal("1"));
            req.setProdutos(List.of(item));
            return producaoService.criarProducao(req).getNumero();
        });
        assertSemColisao(numeros);
    }

    @Test
    void orcamentoSemColisaoSobConcorrencia() throws Exception {
        Usuario usuario = novoUsuario("orc-conc");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Orçamento Concorrência").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Avulso Concorrência").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).build());

        List<Integer> numeros = executarConcorrente(usuario.getEmail(), () -> {
            OrcamentoRequest req = new OrcamentoRequest();
            req.setClienteId(cliente.getId());
            req.setMetodoPagamento(MetodoPagamento.PIX);
            req.setPrazoProducaoDias(5);
            OrcamentoItemRequest item = new OrcamentoItemRequest();
            item.setProdutoId(produto.getId());
            item.setMargemAplicada(new BigDecimal("50"));
            item.setPrecoUnitario(new BigDecimal("10.00"));
            item.setQuantidade(1);
            req.setItens(List.of(item));
            return orcamentoService.criar(req).getNumero();
        });
        assertSemColisao(numeros);
    }
}
