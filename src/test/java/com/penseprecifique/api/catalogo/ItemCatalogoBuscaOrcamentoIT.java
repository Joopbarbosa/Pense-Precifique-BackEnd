package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
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
 * RN-NOVA-6 (#217) — GET /orcamentos/itens-catalogo passa a aceitar `busca`, filtrando por nome
 * do produto (case-insensitive). Achado da Análise: o endpoint só tinha `catalogoId` até aqui —
 * a busca de item de catálogo em `ItemSearch` (CriarOrcamentoPage.tsx) era filtro client-side
 * sobre a lista completa, bug análogo a BUG-BUSCA-PRODUTO/BUG-BUSCA-ORCAMENTO.
 *
 * Regressão coberta explicitamente: bind de parâmetro nulo dentro de `LOWER(CONCAT(:busca, ...))`
 * no JPQL faz o Hibernate/driver inferir o tipo do parâmetro como `bytea`, e o Postgres rejeita
 * com "function lower(bytea) does not exist" — por isso o método sem busca usa uma query JPQL
 * separada (`buscarDisponiveisParaOrcamento`) em vez de `:busca IS NULL OR ...` na mesma query.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ItemCatalogoBuscaOrcamentoIT {

    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;

    private Usuario usuario;
    private Catalogo catalogo;

    private void seedUsuarioECatalogo() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("item-catalogo-busca-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Teste Busca").ativo(true).build());
    }

    private ItemCatalogo novoItem(String nomeProduto, int numero) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nomeProduto).tipo(TipoProduto.PRODUTO)
                .tempoProducao(10).ativo(true).precoVenda(new BigDecimal("10.00")).build());
        return itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(1)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    @Test
    void semBuscaRetornaTodosOsItensDisponiveis() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        novoItem("Laço Decorativo", 2);

        List<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, null);

        assertEquals(2, resultado.size());
    }

    @Test
    void buscaFiltraPorNomeDoProdutoCaseInsensitive() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        novoItem("Laço Decorativo", 2);

        List<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "convite");

        assertEquals(1, resultado.size());
        assertEquals("Kit Convite Floral", resultado.get(0).getNomeProduto());
    }

    @Test
    void buscaSemBrancoTratadaComoAusente() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        novoItem("Laço Decorativo", 2);

        List<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "   ");

        assertEquals(2, resultado.size(), "busca em branco deve equivaler a nenhuma busca (listagem completa)");
    }

    @Test
    void buscaSemCorrespondenciaRetornaListaVaziaSemErro() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);

        // Regressão do bug "function lower(bytea) does not exist" (bind de parâmetro nulo) —
        // aqui o parâmetro é não-nulo mas sem match; o ponto crítico já está coberto pelo teste
        // "semBuscaRetornaTodosOsItensDisponiveis" (busca=null) não lançar exceção.
        List<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "zzz-nao-existe");

        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscaCombinadaComCatalogoIdRespeitaOsDoisFiltros() {
        seedUsuarioECatalogo();
        Catalogo outroCatalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(2).nome("Outro Catálogo").ativo(true).build());

        novoItem("Kit Convite Floral", 1);
        Produto produtoOutroCatalogo = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Kit Convite Rústico").tipo(TipoProduto.PRODUTO)
                .tempoProducao(10).ativo(true).precoVenda(new BigDecimal("10.00")).build());
        itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(outroCatalogo).produto(produtoOutroCatalogo).quantidadePacote(1)
                .precoVenda(new BigDecimal("10.00")).build());

        List<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(catalogo.getId(), "convite");

        assertEquals(1, resultado.size());
        assertEquals("Kit Convite Floral", resultado.get(0).getNomeProduto());
    }
}
