package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
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

/**
 * #210+231+234, RN-NOVA-1 — CEN-NOVO-2. FichaTecnicaService.java:52-54 tinha validação hard-coded
 * pra TipoProduto.PRODUTO_BASE; achado crítico do P002 (sem essa correção, produto-dentro-de-ficha-
 * técnica-de-outro-produto quebra por completo com a eliminação do tipo). Regra original: aceita
 * qualquer produto tipo PRODUTO ativo como componente; rejeita produto inativo.
 *
 * <p>RN-NOVA-8 (V0.10.0, #462, altera PDT-015) — Customização ativa passa a ser aceita como
 * componente também (antes era BLOQUEIO); teste de rejeição de CUSTOMIZACAO foi substituído por
 * teste de aceitação. RN-NOVA-9/DT-NOVA-3 (mesma tarefa) — proteção contra ciclo direto/indireto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FichaTecnicaComponenteProdutoIT {

    @Autowired FichaTecnicaService fichaTecnicaService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("ficha-componente-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Produto novoProduto(String nome, int numero, TipoProduto tipo, boolean ativo, BigDecimal precoCusto) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(tipo).ativo(ativo)
                .tempoProducao(30).precoCusto(precoCusto).precoVenda(new BigDecimal("10.00")).build());
    }

    private Produto novoProdutoPai(String nome, int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoVenda(new BigDecimal("10.00")).build());
    }

    @Test
    void aceitaProdutoTipoProdutoAtivoComoComponente() {
        seedUsuario();
        Produto componente = novoProduto("Bolo Base Componente Teste", 1, TipoProduto.PRODUTO, true, new BigDecimal("3.5000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(componente.getId());
        item.setQuantidade(new BigDecimal("2"));

        BigDecimal custo = fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId());

        assertEquals(0, new BigDecimal("7.0000").compareTo(custo), "2 * 3.5000 = 7.0000");
    }

    @Test
    void aceitaProdutoTipoCustomizacaoAtivoComoComponente() {
        // RN-NOVA-8 (V0.10.0, #462) — reversão de PDT-015: Customização ativa passa a ser aceita.
        seedUsuario();
        Produto customizacao = novoProduto("Topo de Bolo", 1, TipoProduto.CUSTOMIZACAO, true, new BigDecimal("2.0000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(customizacao.getId());
        item.setQuantidade(new BigDecimal("3"));

        BigDecimal custo = fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId());

        assertEquals(0, new BigDecimal("6.0000").compareTo(custo), "3 * 2.0000 = 6.0000");
    }

    @Test
    void rejeitaProdutoInativoComoComponente() {
        seedUsuario();
        Produto componenteInativo = novoProduto("Bolo Base Descontinuado", 1, TipoProduto.PRODUTO, false, new BigDecimal("3.0000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(componenteInativo.getId());
        item.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId()));
        assertEquals("Apenas produtos/customizações ativos podem ser usados como componente de ficha técnica.",
                ex.getMessage());
    }

    @Test
    void rejeitaCustomizacaoInativaComoComponente() {
        seedUsuario();
        Produto customizacaoInativa = novoProduto("Topo Descontinuado", 1, TipoProduto.CUSTOMIZACAO, false, new BigDecimal("2.0000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(customizacaoInativa.getId());
        item.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId()));
        assertEquals("Apenas produtos/customizações ativos podem ser usados como componente de ficha técnica.",
                ex.getMessage());
    }

    @Test
    void rejeitaCicloDireto() {
        // RN-NOVA-9/DT-NOVA-3 (V0.10.0, #462) — A usa B, B usa A de volta.
        seedUsuario();
        Produto a = novoProdutoPai("Caixa de Presente", 1);
        Produto b = novoProduto("Kit Decoração", 2, TipoProduto.CUSTOMIZACAO, true, new BigDecimal("1.0000"));

        FichaTecnicaItemRequest itemBEmA = new FichaTecnicaItemRequest();
        itemBEmA.setProdutoBaseId(b.getId());
        itemBEmA.setQuantidade(new BigDecimal("1"));
        fichaTecnicaService.salvarFichaTecnica(a, List.of(itemBEmA), usuario.getId());

        FichaTecnicaItemRequest itemAEmB = new FichaTecnicaItemRequest();
        itemAEmB.setProdutoBaseId(a.getId());
        itemAEmB.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(b, List.of(itemAEmB), usuario.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("ciclo"));
    }

    @Test
    void rejeitaCicloIndireto() {
        // RN-NOVA-9/DT-NOVA-3 (V0.10.0, #462) — A usa B, B usa C, C usa A de volta (indireto).
        seedUsuario();
        Produto a = novoProdutoPai("Produto A", 1);
        Produto b = novoProdutoPai("Produto B", 2);
        Produto c = novoProduto("Customização C", 3, TipoProduto.CUSTOMIZACAO, true, new BigDecimal("1.0000"));

        FichaTecnicaItemRequest itemBEmA = new FichaTecnicaItemRequest();
        itemBEmA.setProdutoBaseId(b.getId());
        itemBEmA.setQuantidade(new BigDecimal("1"));
        fichaTecnicaService.salvarFichaTecnica(a, List.of(itemBEmA), usuario.getId());

        FichaTecnicaItemRequest itemCEmB = new FichaTecnicaItemRequest();
        itemCEmB.setProdutoBaseId(c.getId());
        itemCEmB.setQuantidade(new BigDecimal("1"));
        fichaTecnicaService.salvarFichaTecnica(b, List.of(itemCEmB), usuario.getId());

        FichaTecnicaItemRequest itemAEmC = new FichaTecnicaItemRequest();
        itemAEmC.setProdutoBaseId(a.getId());
        itemAEmC.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(c, List.of(itemAEmC), usuario.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("ciclo"));
    }

    @Test
    void aceitaDiamanteSemCiclo() {
        // Não é ciclo: A usa B e C; B e C usam D. Nenhum caminho volta pra A/B/C.
        seedUsuario();
        Produto d = novoProduto("Base Comum D", 1, TipoProduto.PRODUTO, true, new BigDecimal("1.0000"));
        // precoCusto de B/C setado direto (não derivado de calcularPrecoCusto, que não persiste
        // sozinho) — evita NPE em calcularPrecoCusto(A) ao ler produtoBase.getPrecoCusto().
        Produto b = novoProduto("Produto B", 2, TipoProduto.PRODUTO, true, new BigDecimal("5.0000"));
        Produto c = novoProduto("Produto C", 3, TipoProduto.PRODUTO, true, new BigDecimal("6.0000"));
        Produto a = novoProdutoPai("Produto A", 4);

        FichaTecnicaItemRequest itemDEmB = new FichaTecnicaItemRequest();
        itemDEmB.setProdutoBaseId(d.getId());
        itemDEmB.setQuantidade(new BigDecimal("1"));
        fichaTecnicaService.salvarFichaTecnica(b, List.of(itemDEmB), usuario.getId());

        FichaTecnicaItemRequest itemDEmC = new FichaTecnicaItemRequest();
        itemDEmC.setProdutoBaseId(d.getId());
        itemDEmC.setQuantidade(new BigDecimal("1"));
        fichaTecnicaService.salvarFichaTecnica(c, List.of(itemDEmC), usuario.getId());

        FichaTecnicaItemRequest itemBEmA = new FichaTecnicaItemRequest();
        itemBEmA.setProdutoBaseId(b.getId());
        itemBEmA.setQuantidade(new BigDecimal("1"));
        FichaTecnicaItemRequest itemCEmA = new FichaTecnicaItemRequest();
        itemCEmA.setProdutoBaseId(c.getId());
        itemCEmA.setQuantidade(new BigDecimal("1"));

        BigDecimal custo = fichaTecnicaService.salvarFichaTecnica(a, List.of(itemBEmA, itemCEmA), usuario.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(custo);
    }
}
