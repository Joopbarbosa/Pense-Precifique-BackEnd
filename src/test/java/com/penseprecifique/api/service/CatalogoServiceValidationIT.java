package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.ItemCatalogo;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.TipoProduto;
import com.penseprecifique.api.dto.request.CatalogoRequest;
import com.penseprecifique.api.dto.request.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.dto.request.DuplicarCatalogoRequest;
import com.penseprecifique.api.dto.request.ItemCatalogoRequest;
import com.penseprecifique.api.dto.response.CatalogoResponse;
import com.penseprecifique.api.dto.response.ItemCatalogoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.repository.ItemCatalogoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validação end-to-end (camada Service) do checklist do catálogo — sem HTTP,
 * pois ainda não existe Controller. Cada check imprime o valor REAL retornado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogoServiceValidationIT {

    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private final List<String> report = new ArrayList<>();
    private boolean allPass = true;

    private void check(int n, String titulo, boolean pass, String actual) {
        allPass &= pass;
        report.add(String.format("[%s] CHECK %d — %s%n        → %s",
                pass ? "PASS" : "FALHOU", n, titulo, actual));
    }

    @Test
    void checklistCatalogo() {
        try {
            runChecks();
        } finally {
            StringBuilder sb = new StringBuilder("\n\n======== CHECKLIST CATÁLOGO — RESULTADOS REAIS ========\n");
            report.forEach(l -> sb.append(l).append("\n"));
            sb.append("=======================================================\n");
            System.out.println(sb);
        }
        Assertions.assertTrue(allPass, "Um ou mais checks falharam — ver relatório acima.");
    }

    private void runChecks() {
        // ---- seed: usuário autenticado + produtos ----
        Usuario user = usuarioRepository.save(Usuario.builder()
                .email("catalogo-it-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));

        Produto prodMain  = novoProduto(user, "Bolo", TipoProduto.PRODUTO,     new BigDecimal("7.0000"),  new BigDecimal("10.00"));
        Produto prodMain2 = novoProduto(user, "Torta", TipoProduto.PRODUTO,    new BigDecimal("7.0000"),  new BigDecimal("10.00"));
        Produto prodCustom= novoProduto(user, "Topo",  TipoProduto.CUSTOMIZACAO,new BigDecimal("2.0000"), new BigDecimal("5.00"));
        Produto prodZero  = novoProduto(user, "SemCusto", TipoProduto.PRODUTO,  BigDecimal.ZERO,           new BigDecimal("10.00"));

        // ============ CHECK 1 — nome duplicado ============
        try {
            catalogoService.cadastrar(req("Catálogo Alfa", new BigDecimal("50")));
            catalogoService.cadastrar(req("catálogo alfa", new BigDecimal("50"))); // case-insensitive
            check(1, "nome duplicado bloqueia com erro amigável", false, "NÃO bloqueou — cadastrou duplicado");
        } catch (BusinessException e) {
            check(1, "nome duplicado bloqueia com erro amigável (não estoura na constraint)",
                    true, "BusinessException: \"" + e.getMessage() + "\"");
        } catch (Exception e) {
            check(1, "nome duplicado", false, "estourou exceção NÃO amigável: " + e.getClass().getSimpleName() + " / " + e.getMessage());
        }

        // ============ CHECK 2 — margem <= 0 ============
        try {
            catalogoService.cadastrar(req("Margem Zero", BigDecimal.ZERO));
            check(2, "margem <= 0 bloqueia", false, "NÃO bloqueou margem 0");
        } catch (BusinessException e) {
            check(2, "margem <= 0 bloqueia", true, "BusinessException: \"" + e.getMessage() + "\"");
        } catch (Exception e) {
            check(2, "margem <= 0 bloqueia", false, "exceção inesperada: " + e.getClass().getSimpleName());
        }

        // ============ CHECK 3 — Cenário 93: custo 7, margem 50%, qtd 10 → 105,00 ============
        try {
            CatalogoResponse cat34 = catalogoService.cadastrar(req("Cenário 93/94", new BigDecimal("50")));
            ItemCatalogoResponse item3 = itemCatalogoService.adicionar(cat34.getId(), itemReq(prodMain.getId(), 10, null, List.of()));
            boolean p3 = eq(item3.getPrecoSugerido(), "105.00") && eq(item3.getPrecoVenda(), "105.00") && !item3.isOverride();
            check(3, "custo7 x margem50 x qtd10 → precoSugerido e precoVenda = 105,00", p3,
                    "precoSugerido=" + plain(item3.getPrecoSugerido()) + " precoVenda=" + plain(item3.getPrecoVenda()) + " override=" + item3.isOverride());

            // ============ CHECK 4 — Cenário 94: + customização custo 2 qtd 1 → 108,00 ============
            try {
                ItemCatalogoResponse item4 = itemCatalogoService.editar(item3.getId(),
                        itemReq(prodMain.getId(), 10, null, List.of(custom(prodCustom.getId(), new BigDecimal("1")))));
                boolean p4 = eq(item4.getPrecoSugerido(), "108.00") && eq(item4.getPrecoVenda(), "108.00") && !item4.isOverride();
                check(4, "item + customização(custo2 x qtd1) → 108,00", p4,
                        "precoSugerido=" + plain(item4.getPrecoSugerido()) + " precoVenda=" + plain(item4.getPrecoVenda()) + " override=" + item4.isOverride());
            } catch (Exception e) {
                check(4, "item + customização → 108,00", false, "BLOQUEADO: " + raiz(e));
            }
        } catch (Exception e) {
            check(3, "custo7 x margem50 x qtd10 → 105,00", false, "BLOQUEADO: " + raiz(e));
            check(4, "item + customização → 108,00", false, "BLOQUEADO (depende do check 3)");
        }

        // ============ CHECK 5 — margem 60% recalcula item s/ override; item c/ override NÃO muda ============
        try {
            CatalogoResponse cat5 = catalogoService.cadastrar(req("Recálculo Margem", new BigDecimal("50")));
            ItemCatalogoResponse semOverride = itemCatalogoService.adicionar(cat5.getId(), itemReq(prodMain.getId(), 10, null, List.of()));      // 105,00 sem override
            ItemCatalogoResponse comOverride = itemCatalogoService.adicionar(cat5.getId(), itemReq(prodMain2.getId(), 10, new BigDecimal("200.00"), List.of())); // override 200,00
            catalogoService.editar(cat5.getId(), req("Recálculo Margem", new BigDecimal("60"))); // muda margem
            ItemCatalogo semOvDepois = itemCatalogoRepository.findByIdAndDeletedAtIsNull(semOverride.getId()).orElseThrow();
            ItemCatalogo comOvDepois = itemCatalogoRepository.findByIdAndDeletedAtIsNull(comOverride.getId()).orElseThrow();
            boolean p5 = eq(semOvDepois.getPrecoVenda(), "112.00") && !semOvDepois.getOverride()
                    && eq(comOvDepois.getPrecoVenda(), "200.00") && comOvDepois.getOverride();
            check(5, "margem 50→60: item s/ override 105→112,00; item c/ override permanece 200,00", p5,
                    "semOverride: " + plain(semOvDepois.getPrecoVenda()) + " (override=" + semOvDepois.getOverride() + ")"
                            + " | comOverride: " + plain(comOvDepois.getPrecoVenda()) + " (override=" + comOvDepois.getOverride() + ")");

            // ============ CHECK 7 — desativar/reativar só alterna ativo, itens intactos ============
            try {
                long itensAntes = itemCatalogoRepository.countByCatalogoIdAndDeletedAtIsNull(cat5.getId());
                CatalogoResponse desativado = catalogoService.desativar(cat5.getId());
                CatalogoResponse reativado = catalogoService.reativar(cat5.getId());
                long itensDepois = itemCatalogoRepository.countByCatalogoIdAndDeletedAtIsNull(cat5.getId());
                boolean p7 = !desativado.isAtivo() && reativado.isAtivo() && itensAntes == itensDepois;
                check(7, "desativar→ativo=false, reativar→ativo=true, itens intactos", p7,
                        "ativoApósDesativar=" + desativado.isAtivo() + " ativoApósReativar=" + reativado.isAtivo()
                                + " itens antes=" + itensAntes + " depois=" + itensDepois);
            } catch (Exception e) {
                check(7, "desativar/reativar mantém itens", false, "BLOQUEADO: " + raiz(e));
            }

            // ============ CHECK 6 — produto sem precoCusto bloqueia (RN-044) ============
            try {
                itemCatalogoService.adicionar(cat5.getId(), itemReq(prodZero.getId(), 1, null, List.of()));
                check(6, "produto sem precoCusto bloqueia (RN-044)", false, "NÃO bloqueou produto custo 0");
            } catch (BusinessException e) {
                check(6, "produto sem precoCusto bloqueia (RN-044)", true, "BusinessException: \"" + e.getMessage() + "\"");
            } catch (Exception e) {
                check(6, "produto sem precoCusto bloqueia (RN-044)", false, "exceção NÃO amigável: " + raiz(e));
            }
        } catch (Exception e) {
            check(5, "recálculo por margem", false, "BLOQUEADO: " + raiz(e));
            check(6, "RN-044", false, "BLOQUEADO (depende do check 5)");
            check(7, "desativar/reativar", false, "BLOQUEADO (depende do check 5)");
        }

        // ============ CHECK 8 — duplicar com item override: numero novo, override e preço exatos ============
        try {
            CatalogoResponse origem = catalogoService.cadastrar(req("Para Duplicar", new BigDecimal("40")));
            ItemCatalogoResponse itemOv = itemCatalogoService.adicionar(origem.getId(), itemReq(prodMain.getId(), 3, new BigDecimal("77.77"), List.of())); // override 77,77
            DuplicarCatalogoRequest dup = new DuplicarCatalogoRequest();
            dup.setNovoNome("Duplicado XYZ");
            CatalogoResponse copia = catalogoService.duplicar(origem.getId(), dup);
            List<ItemCatalogo> itensCopia = itemCatalogoRepository.findByCatalogoIdAndDeletedAtIsNull(copia.getId());
            ItemCatalogo itemCopia = itensCopia.get(0);
            boolean numeroNovo = copia.getNumero() != null && !copia.getNumero().equals(origem.getNumero());
            boolean p8 = numeroNovo && itensCopia.size() == 1
                    && itemCopia.getOverride() && eq(itemCopia.getPrecoVenda(), "77.77");
            check(8, "duplicar: numero novo + override e preço (77,77) preservados exatos", p8,
                    "numeroOrigem=" + origem.getNumero() + " numeroCopia=" + copia.getNumero()
                            + " itensCopia=" + itensCopia.size() + " override=" + itemCopia.getOverride()
                            + " precoVenda=" + plain(itemCopia.getPrecoVenda()) + " (origem override=" + itemOv.isOverride() + " preco=" + plain(itemOv.getPrecoVenda()) + ")");
        } catch (Exception e) {
            check(8, "duplicar preserva numero/override/preço", false, "BLOQUEADO: " + raiz(e));
        }
    }

    private static String raiz(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return e.getClass().getSimpleName() + " → " + r.getClass().getSimpleName()
                + ": " + String.valueOf(r.getMessage()).replaceAll("\\s+", " ").trim();
    }

    // ---- helpers ----
    private Produto novoProduto(Usuario u, String nome, TipoProduto tipo, BigDecimal custo, BigDecimal precoVenda) {
        return produtoRepository.save(Produto.builder()
                .usuario(u).nome(nome).tipo(tipo).tempoProducao(60)
                .precoCusto(custo).precoVenda(precoVenda).build());
    }

    private CatalogoRequest req(String nome, BigDecimal margem) {
        CatalogoRequest r = new CatalogoRequest();
        r.setNome(nome); r.setMargem(margem);
        return r;
    }

    private ItemCatalogoRequest itemReq(UUID produtoId, int qtd, BigDecimal precoVenda, List<CustomizacaoAnexadaRequest> customs) {
        ItemCatalogoRequest r = new ItemCatalogoRequest();
        r.setProdutoId(produtoId); r.setQuantidadePacote(qtd); r.setPrecoVenda(precoVenda);
        r.setCustomizacoesAnexadas(new ArrayList<>(customs));
        return r;
    }

    private CustomizacaoAnexadaRequest custom(UUID produtoId, BigDecimal qtd) {
        CustomizacaoAnexadaRequest c = new CustomizacaoAnexadaRequest();
        c.setProdutoId(produtoId); c.setQuantidade(qtd);
        return c;
    }

    private static boolean eq(BigDecimal v, String esperado) {
        return v != null && v.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "null" : v.toPlainString();
    }
}
