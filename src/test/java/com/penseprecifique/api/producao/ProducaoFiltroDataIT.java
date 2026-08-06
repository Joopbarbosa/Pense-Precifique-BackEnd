package com.penseprecifique.api.producao;

import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #184/#192 — RN-NOVA-2: filtro por intervalo de dataInicio em GET /producoes, usado tanto pela
 * Listagem quanto pelo Kanban (mesmo endpoint — só muda `estado`/`size` do request no frontend,
 * confirmado em ListaProducaoPage.tsx). Cobre também o cenário de risco da integração com #158
 * (ordenação por campo agregado + filtro de data juntos, pra garantir que o filtro entra nas DUAS
 * etapas da busca em duas partes: query de IDs e o count).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoFiltroDataIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired ProducaoProdutoRepository producaoProdutoRepository;

    private Usuario usuario;
    private UUID pAId, pBId, pCId; // dataInicio: pA=10/01, pB=20/01, pC=05/01
    private int numeroProducao = 200;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-filtro-data-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Produto prodA = novoProduto("Alfa Filtro", 1);
        Produto prodB = novoProduto("Bravo Filtro", 2);
        Produto prodC = novoProduto("Charlie Filtro", 3);

        pAId = novaProducaoDireta(prodA, LocalDate.of(2026, 1, 10), new BigDecimal("5"));
        pBId = novaProducaoDireta(prodB, LocalDate.of(2026, 1, 20), new BigDecimal("15"));
        pCId = novaProducaoDireta(prodC, LocalDate.of(2026, 1, 5), new BigDecimal("10"));
    }

    private Produto novoProduto(String nome, int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).build());
    }

    private UUID novaProducaoDireta(Produto produto, LocalDate dataInicio, BigDecimal quantidade) {
        Producao producao = Producao.builder()
                .usuario(usuario)
                .numero(numeroProducao++)
                .estado(com.penseprecifique.api.shared.domain.enums.EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(dataInicio)
                .dataTerminoPrevista(dataInicio.plusDays(7))
                .build();
        producao = producaoRepository.save(producao);
        producaoProdutoRepository.save(ProducaoProduto.builder()
                .producao(producao).produto(produto).quantidade(quantidade).build());
        return producao.getId();
    }

    private List<UUID> listarIds(String busca, LocalDate de, LocalDate ate, Sort sort) {
        Page<ProducaoResponse> pagina = producaoService.listar(busca, null, de, ate, PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(ProducaoResponse::getId).toList();
    }

    @Test
    void filtroAplicadoRetornaSoIntervalo() {
        seed();
        List<UUID> ids = listarIds(null, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 15), Sort.unsorted());
        assertEquals(List.of(pAId), ids, "só pA (10/01) está no intervalo [08/01, 15/01]");
    }

    @Test
    void semFiltroMantemComportamentoAtual() {
        seed();
        List<UUID> ids = listarIds(null, null, null, Sort.unsorted());
        assertEquals(3, ids.size(), "sem dataInicioDe/dataInicioAte, nenhum corte de período — todas retornam");
    }

    @Test
    void intervaloSemCorrespondenciaRetornaVazioSemErro() {
        seed();
        List<UUID> ids = listarIds(null, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), Sort.unsorted());
        assertTrue(ids.isEmpty(), "intervalo sem nenhuma produção deve retornar lista vazia, não erro");
    }

    @Test
    void filtroCombinadoComOrdenacaoPorCampoAgregado() {
        seed();
        // intervalo [08/01, 25/01] pega pA e pB (não pC, 05/01) — ordenado por quantidade DESC deve
        // vir pB(15) antes de pA(5). Prova que o filtro de data entra nas duas etapas da busca (IDs
        // ordenados por agregado MIN/SUM + o count), não só na primeira.
        List<UUID> ids = listarIds(null, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 25),
                Sort.by(Sort.Direction.DESC, "quantidade"));
        assertEquals(List.of(pBId, pAId), ids);
    }

    @Test
    void filtroCombinadoComBuscaPorTexto() {
        seed();
        // busca por "Filtro" bate nos 3 produtos, mas o intervalo de data restringe pra só pC (05/01)
        List<UUID> ids = listarIds("Charlie Filtro", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 6), Sort.unsorted());
        assertEquals(List.of(pCId), ids);
    }
}
