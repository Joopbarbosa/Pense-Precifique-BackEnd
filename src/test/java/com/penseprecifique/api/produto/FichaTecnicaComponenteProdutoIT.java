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
 * técnica-de-outro-produto quebra por completo com a eliminação do tipo). Regra nova: aceita
 * qualquer produto tipo PRODUTO ativo como componente; rejeita CUSTOMIZACAO ou produto inativo.
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
    void rejeitaProdutoTipoCustomizacaoComoComponente() {
        seedUsuario();
        Produto customizacao = novoProduto("Topo de Bolo", 1, TipoProduto.CUSTOMIZACAO, true, new BigDecimal("2.0000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(customizacao.getId());
        item.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId()));
        assertEquals("Apenas produtos ativos do tipo Produto podem ser usados como componente de ficha técnica.",
                ex.getMessage());
    }

    @Test
    void rejeitaProdutoTipoProdutoInativoComoComponente() {
        seedUsuario();
        Produto componenteInativo = novoProduto("Bolo Base Descontinuado", 1, TipoProduto.PRODUTO, false, new BigDecimal("3.0000"));
        Produto pai = novoProdutoPai("Bolo Decorado", 2);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setProdutoBaseId(componenteInativo.getId());
        item.setQuantidade(new BigDecimal("1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(pai, List.of(item), usuario.getId()));
        assertEquals("Apenas produtos ativos do tipo Produto podem ser usados como componente de ficha técnica.",
                ex.getMessage());
    }
}
