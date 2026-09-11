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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 *
 * RN-NOVA-18 (#353/P-B008) — {@code buscarParaOrcamento} devolve {@code Page<>} completo (não só
 * o conteúdo) desde esta revisão; testes atualizados para ler via {@code getContent()}, mais 3
 * casos novos de metadado de paginação (CEN-NOVO-19/20/21).
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

        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, null, Pageable.unpaged());

        assertEquals(2, resultado.getContent().size());
    }

    @Test
    void respostaExpoeProdutoId() {
        seedUsuarioECatalogo();
        ItemCatalogo item = novoItem("Kit Convite Floral", 1);

        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, null, Pageable.unpaged());

        assertEquals(1, resultado.getContent().size());
        assertEquals(item.getProduto().getId(), resultado.getContent().get(0).getProdutoId(),
                "#218 — produtoId precisa estar presente para montar a navegação de criação de produção");
    }

    /**
     * RN-NOVA-23 (#313, P-B006) — Caminho A: catalogoId precisa estar presente para o Frontend
     * chamar GET /catalogos/{catalogoId}/itens e montar a calculadora de preço após selecionar o
     * item na busca (a busca sozinha não carrega quantidadePacote/customizacoesAnexadas).
     */
    @Test
    void respostaExpoeCatalogoId() {
        seedUsuarioECatalogo();
        ItemCatalogo item = novoItem("Kit Convite Floral", 1);

        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, null, Pageable.unpaged());

        assertEquals(1, resultado.getContent().size());
        assertEquals(item.getCatalogo().getId(), resultado.getContent().get(0).getCatalogoId());
    }

    @Test
    void buscaFiltraPorNomeDoProdutoCaseInsensitive() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        novoItem("Laço Decorativo", 2);

        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "convite", Pageable.unpaged());

        assertEquals(1, resultado.getContent().size());
        assertEquals("Kit Convite Floral", resultado.getContent().get(0).getNomeProduto());
    }

    @Test
    void buscaSemBrancoTratadaComoAusente() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        novoItem("Laço Decorativo", 2);

        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "   ", Pageable.unpaged());

        assertEquals(2, resultado.getContent().size(), "busca em branco deve equivaler a nenhuma busca (listagem completa)");
    }

    @Test
    void buscaSemCorrespondenciaRetornaListaVaziaSemErro() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);

        // Regressão do bug "function lower(bytea) does not exist" (bind de parâmetro nulo) —
        // aqui o parâmetro é não-nulo mas sem match; o ponto crítico já está coberto pelo teste
        // "semBuscaRetornaTodosOsItensDisponiveis" (busca=null) não lançar exceção.
        Page<ItemCatalogoBuscaResponse> resultado = itemCatalogoService.buscarParaOrcamento(null, "zzz-nao-existe", Pageable.unpaged());

        assertTrue(resultado.getContent().isEmpty());
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

        Page<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(catalogo.getId(), "convite", Pageable.unpaged());

        assertEquals(1, resultado.getContent().size());
        assertEquals("Kit Convite Floral", resultado.getContent().get(0).getNomeProduto());
    }

    /**
     * P-B008/#353 — mais itens que o {@code size} da página, confirma que só a página pedida volta
     * (a consulta passa a ter bound, em vez de servir a base inteira sem limite).
     */
    @Test
    void primeiraPaginaRespeitaOSizePedidoQuandoHaMaisItensQueOSize() {
        seedUsuarioECatalogo();
        for (int i = 1; i <= 10; i++) {
            novoItem(String.format("Item %02d", i), i);
        }

        Page<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(null, null, PageRequest.of(0, 8));

        assertEquals(8, resultado.getContent().size());
        assertEquals("Item 01", resultado.getContent().get(0).getNomeProduto());
        assertEquals("Item 08", resultado.getContent().get(7).getNomeProduto());
    }

    /**
     * P-B008/#353 — segunda página traz os itens restantes (não os mesmos da primeira), confirmando
     * que o parâmetro de página é de fato repassado ao repositório, não só o tamanho.
     */
    @Test
    void segundaPaginaTrazOsItensRestantesSemSobreporAPrimeira() {
        seedUsuarioECatalogo();
        for (int i = 1; i <= 10; i++) {
            novoItem(String.format("Item %02d", i), i);
        }

        Page<ItemCatalogoBuscaResponse> segundaPagina =
                itemCatalogoService.buscarParaOrcamento(null, null, PageRequest.of(1, 8));

        assertEquals(2, segundaPagina.getContent().size());
        assertEquals("Item 09", segundaPagina.getContent().get(0).getNomeProduto());
        assertEquals("Item 10", segundaPagina.getContent().get(1).getNomeProduto());
    }

    /**
     * RN-NOVA-18 (#353/P-B008) — CEN-NOVO-19: catálogo com mais de 8 itens elegíveis, sem busca —
     * página cheia (8) com {@code last=false}, expondo que há mais itens além da primeira página
     * (metadado que o contrato antigo, array simples, escondia por completo).
     */
    @Test
    void cenNovo19MaisDeOitoItensSemBuscaDevolveOitoComLastFalse() {
        seedUsuarioECatalogo();
        for (int i = 1; i <= 10; i++) {
            novoItem(String.format("Item %02d", i), i);
        }

        Page<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(null, null, PageRequest.of(0, 8));

        assertEquals(8, resultado.getContent().size());
        assertEquals(10, resultado.getTotalElements());
        assertTrue(!resultado.isLast(), "com 10 itens e página de 8, a primeira página não deve ser a última");
    }

    /**
     * RN-NOVA-18 (#353/P-B008) — CEN-NOVO-20: catálogo com 8 ou menos itens elegíveis — todos numa
     * página só, {@code last=true}.
     */
    @Test
    void cenNovo20OitoOuMenosItensDevolveTodosNumaPaginaComLastTrue() {
        seedUsuarioECatalogo();
        for (int i = 1; i <= 5; i++) {
            novoItem(String.format("Item %02d", i), i);
        }

        Page<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(null, null, PageRequest.of(0, 8));

        assertEquals(5, resultado.getContent().size());
        assertEquals(5, resultado.getTotalElements());
        assertTrue(resultado.isLast());
    }

    /**
     * RN-NOVA-18 (#353/P-B008) — CEN-NOVO-21: busca reduz o conjunto a 8 ou menos, mesmo com
     * catálogo grande sem filtro — {@code last=true}, sem paginação restante para o resultado
     * filtrado (o total sem filtro é maior, mas irrelevante aqui — o metadado reflete a busca).
     */
    @Test
    void cenNovo21BuscaReduzAOitoOuMenosDevolveLastTrueMesmoComCatalogoGrande() {
        seedUsuarioECatalogo();
        novoItem("Kit Convite Floral", 1);
        for (int i = 2; i <= 10; i++) {
            novoItem(String.format("Item %02d", i), i);
        }

        Page<ItemCatalogoBuscaResponse> resultado =
                itemCatalogoService.buscarParaOrcamento(null, "convite", PageRequest.of(0, 8));

        assertEquals(1, resultado.getContent().size());
        assertEquals(1, resultado.getTotalElements());
        assertTrue(resultado.isLast());
    }
}
