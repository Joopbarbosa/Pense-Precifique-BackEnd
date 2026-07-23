package com.penseprecifique.api.producao;

import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.ProducaoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #158/RN-NOVA-6 — GET /producoes ignorava o Sort do Pageable (JPQL de buscar() tinha ORDER BY fixo,
 * appendado depois do Sort do cliente — nunca tinha efeito real). Cobre os 4 campos ordenáveis
 * (dataInicio, estado, produto, quantidade), asc/desc, e o default (numero DESC sem sort informado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoOrdenacaoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private UUID pAId, pBId, pCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-ord-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        // 3 produtos simples (sem ficha técnica — criarProducao exige ficha, então seedamos
        // produções diretamente via repository, sem passar pelo Service, pra isolar só a ordenação.
        Produto prodA = novoProduto("Alfa", 1);
        Produto prodB = novoProduto("Bravo", 2);
        Produto prodC = novoProduto("Charlie", 3);

        pAId = novaProducaoDireta(prodA, LocalDate.of(2026, 1, 10), new BigDecimal("5"));
        pBId = novaProducaoDireta(prodB, LocalDate.of(2026, 1, 20), new BigDecimal("15"));
        pCId = novaProducaoDireta(prodC, LocalDate.of(2026, 1, 5), new BigDecimal("10"));
    }

    private Produto novoProduto(String nome, int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).build());
    }

    /** Cria a Producao + ProducaoProduto direto via repository — só o necessário pra testar ordenação. */
    private UUID novaProducaoDireta(Produto produto, LocalDate dataInicio, BigDecimal quantidade) {
        Producao producao = Producao.builder()
                .usuario(usuario)
                .numero(numeroProducao++)
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(dataInicio)
                .dataTerminoPrevista(dataInicio.plusDays(7))
                .build();
        producao = producaoRepository.save(producao);
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(quantidade).build());
        return producao.getId();
    }

    private int numeroProducao = 100;

    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;

    @Test
    void ordenaPorDataInicioAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "dataInicio"));
        assertEquals(List.of(pCId, pAId, pBId), asc); // 05/01, 10/01, 20/01

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "dataInicio"));
        assertEquals(List.of(pBId, pAId, pCId), desc);
    }

    @Test
    void ordenaPorProdutoAlfabetico() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "produto"));
        assertEquals(List.of(pAId, pBId, pCId), asc); // Alfa, Bravo, Charlie

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "produto"));
        assertEquals(List.of(pCId, pBId, pAId), desc);
    }

    @Test
    void ordenaPorQuantidade() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "quantidade"));
        assertEquals(List.of(pAId, pCId, pBId), asc); // 5, 10, 15

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "quantidade"));
        assertEquals(List.of(pBId, pCId, pAId), desc);
    }

    @Test
    void ordenaPorEstado() {
        seed();
        // muda pB pra EM_ANDAMENTO direto no banco só pra ter 2 estados distintos e comparar ordenação
        Producao pb = producaoRepository.findById(pBId).orElseThrow();
        pb.setEstado(EstadoProducao.EM_ANDAMENTO);
        producaoRepository.save(pb);

        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "estado"));
        // AGUARDANDO_INICIO < EM_ANDAMENTO alfabeticamente
        int idxA = asc.indexOf(pAId), idxC = asc.indexOf(pCId), idxB = asc.indexOf(pBId);
        assertTrue(idxA < idxB && idxC < idxB, "AGUARDANDO_INICIO deve vir antes de EM_ANDAMENTO em ordem ASC");
    }

    @Test
    void defaultSemSortUsaNumeroDesc() {
        seed();
        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));
        List<UUID> ids = pagina.getContent().stream().map(ProducaoResponse::getId).toList();
        // numero DESC: última criada (pC, numero=102) primeiro
        assertEquals(List.of(pCId, pBId, pAId), ids);
    }

    @Test
    void campoForaDaAllowlistLancaBusinessException() {
        seed();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "usuario.email"));
        assertThrows(BusinessException.class, () -> producaoService.listar(null, null, null, null, pageable));
    }

    private List<UUID> idsNaOrdem(Sort sort) {
        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(ProducaoResponse::getId).toList();
    }
}
