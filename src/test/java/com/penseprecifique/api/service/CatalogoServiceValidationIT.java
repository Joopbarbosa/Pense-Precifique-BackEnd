package com.penseprecifique.api.service;

import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.DuplicarCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.catalogo.CatalogoService;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
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
 *
 * <p>V0.13.0 (#516, RN-NOVA-1/2/3): Item de Catálogo deixou de herdar produto.precoVenda ×
 * quantidadePacote (CAT-003) e passou a ter composição livre de N componentes (Insumo XOR
 * Produto-base) com custo (precoCusto dos componentes) + mão de obra (tempoProducao × valorHora) +
 * margem própria. Checks 3/4/5/8 foram reescritos nesta versão para refletir a nova fórmula — os
 * exemplos usam tempoProducao=0 e margem padrão 0 (sem ConfiguracaoPrecificacao seedada) pra isolar
 * o efeito do custo dos componentes.</p>
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
            catalogoService.cadastrar(req("Catálogo Alfa"));
            catalogoService.cadastrar(req("catálogo alfa")); // case-insensitive
            check(1, "nome duplicado bloqueia com erro amigável", false, "NÃO bloqueou — cadastrou duplicado");
        } catch (BusinessException e) {
            check(1, "nome duplicado bloqueia com erro amigável (não estoura na constraint)",
                    true, "BusinessException: \"" + e.getMessage() + "\"");
        } catch (Exception e) {
            check(1, "nome duplicado", false, "estourou exceção NÃO amigável: " + e.getClass().getSimpleName() + " / " + e.getMessage());
        }

        // ============ CHECK 2 — cadastrar/editar catálogo sem campo margem no payload (#239 — campo eliminado) ============
        try {
            CatalogoResponse cat2 = catalogoService.cadastrar(req("Sem Margem"));
            CatalogoResponse cat2Editado = catalogoService.editar(cat2.getId(), req("Sem Margem Editado"));
            boolean p2 = "Sem Margem Editado".equals(cat2Editado.getNome());
            check(2, "cadastrar/editar catálogo sem campo margem no payload", p2, "nome=" + cat2Editado.getNome());
        } catch (Exception e) {
            check(2, "cadastrar/editar catálogo sem margem", false, "BLOQUEADO: " + raiz(e));
        }

        // ============ CHECK 3 — precoSugerido = custoComponentes: precoCusto(7,00) x qtd10 → 70,00 ============
        try {
            CatalogoResponse cat34 = catalogoService.cadastrar(req("Cenário 93/94"));
            ItemCatalogoResponse item3 = itemCatalogoService.adicionar(cat34.getId(),
                    itemReq("Kit Bolo", componente(prodMain.getId(), 10), null, List.of()));
            boolean p3 = eq(item3.getPrecoSugerido(), "70.00") && eq(item3.getPrecoVenda(), "70.00") && !item3.isOverride();
            check(3, "precoCusto(7,00) x qtd10 → precoSugerido e precoVenda = 70,00", p3,
                    "precoSugerido=" + plain(item3.getPrecoSugerido()) + " precoVenda=" + plain(item3.getPrecoVenda()) + " override=" + item3.isOverride());

            // ============ CHECK 4 — + componente de customização (precoCusto 2,00 x qtd1): precoSugerido
            // sobe pra 72,00 mas precoVenda persistido NÃO acompanha sozinho (mesmo modelo calculado+
            // override de Produto — mudança de custo/composição só atualiza o "sugerido" de referência;
            // sem margem mudar nem precoVenda novo informado, o valor persistido fica congelado). ============
            try {
                ItemCatalogoResponse item4 = itemCatalogoService.editar(item3.getId(),
                        itemReq("Kit Bolo", componente(prodMain.getId(), 10), null,
                                List.of(componente(prodCustom.getId(), 1))));
                boolean p4 = eq(item4.getPrecoSugerido(), "72.00") && eq(item4.getPrecoVenda(), "70.00") && !item4.isOverride();
                check(4, "item + componente de customização (precoCusto2,00 x qtd1) → precoSugerido 72,00, precoVenda congelado em 70,00", p4,
                        "precoSugerido=" + plain(item4.getPrecoSugerido()) + " precoVenda=" + plain(item4.getPrecoVenda()) + " override=" + item4.isOverride());
            } catch (Exception e) {
                check(4, "item + componente de customização → 72,00", false, "BLOQUEADO: " + raiz(e));
            }
        } catch (Exception e) {
            check(3, "precoCusto(7,00) x qtd10 → 70,00", false, "BLOQUEADO: " + raiz(e));
            check(4, "item + componente de customização → 72,00", false, "BLOQUEADO (depende do check 3)");
        }

        // ============ CHECK 5 — override do item persiste mesmo após precoCusto do produto mudar depois ============
        try {
            CatalogoResponse cat5 = catalogoService.cadastrar(req("Persistência de Override"));
            ItemCatalogoResponse itemOverride = itemCatalogoService.adicionar(cat5.getId(),
                    itemReq("Kit Override", componente(prodMain.getId(), 10), new BigDecimal("999.00"), List.of())); // override

            Produto prodMainAtualizado = produtoRepository.findById(prodMain.getId()).orElseThrow();
            prodMainAtualizado.setPrecoCusto(new BigDecimal("50.0000"));
            produtoRepository.save(prodMainAtualizado);

            ItemCatalogo itemDepois = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemOverride.getId()).orElseThrow();
            boolean p5 = eq(itemDepois.getPrecoVenda(), "999.00") && itemDepois.getOverride();
            check(5, "override do item (999,00) persiste mesmo após precoCusto do produto mudar depois, sem reeditar o item", p5,
                    "precoVenda=" + plain(itemDepois.getPrecoVenda()) + " (override=" + itemDepois.getOverride() + ")");

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
                itemCatalogoService.adicionar(cat5.getId(), itemReq("Kit SemCusto", componente(prodZero.getId(), 1), null, List.of()));
                check(6, "produto sem precoCusto bloqueia (RN-044)", false, "NÃO bloqueou produto custo 0");
            } catch (BusinessException e) {
                check(6, "produto sem precoCusto bloqueia (RN-044)", true, "BusinessException: \"" + e.getMessage() + "\"");
            } catch (Exception e) {
                check(6, "produto sem precoCusto bloqueia (RN-044)", false, "exceção NÃO amigável: " + raiz(e));
            }
        } catch (Exception e) {
            check(5, "override persiste após mudança de precoCusto do produto", false, "BLOQUEADO: " + raiz(e));
            check(6, "RN-044", false, "BLOQUEADO (depende do check 5)");
            check(7, "desativar/reativar", false, "BLOQUEADO (depende do check 5)");
        }

        // ============ CHECK 8 — duplicar com item override: numero novo, override e preço exatos ============
        try {
            CatalogoResponse origem = catalogoService.cadastrar(req("Para Duplicar"));
            ItemCatalogoResponse itemOv = itemCatalogoService.adicionar(origem.getId(),
                    itemReq("Kit Torta", componente(prodMain2.getId(), 3), new BigDecimal("77.77"), List.of())); // override 77,77
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
    private int proximoNumeroProduto = 1;

    private Produto novoProduto(Usuario u, String nome, TipoProduto tipo, BigDecimal custo, BigDecimal precoVenda) {
        return produtoRepository.save(Produto.builder()
                .usuario(u).numero(proximoNumeroProduto++).nome(nome).tipo(tipo).tempoProducao(60)
                .precoCusto(custo).precoVenda(precoVenda).build());
    }

    private CatalogoRequest req(String nome) {
        CatalogoRequest r = new CatalogoRequest();
        r.setNome(nome);
        return r;
    }

    private ItemCatalogoComponenteRequest componente(UUID produtoBaseId, int qtd) {
        ItemCatalogoComponenteRequest c = new ItemCatalogoComponenteRequest();
        c.setProdutoBaseId(produtoBaseId);
        c.setQuantidade(BigDecimal.valueOf(qtd));
        return c;
    }

    private ItemCatalogoRequest itemReq(String nome, ItemCatalogoComponenteRequest principal, BigDecimal precoVenda,
                                         List<ItemCatalogoComponenteRequest> demaisComponentes) {
        ItemCatalogoRequest r = new ItemCatalogoRequest();
        r.setNome(nome);
        r.setTempoProducao(0);
        r.setPrecoVenda(precoVenda);
        List<ItemCatalogoComponenteRequest> componentes = new ArrayList<>();
        componentes.add(principal);
        componentes.addAll(demaisComponentes);
        r.setComponentes(componentes);
        return r;
    }

    private static boolean eq(BigDecimal v, String esperado) {
        return v != null && v.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "null" : v.toPlainString();
    }
}
