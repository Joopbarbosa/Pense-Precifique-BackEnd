package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.Produto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #210+231+234 — migration V28 (elimina TipoProduto.PRODUTO_BASE, unifica modelo de preço).
 * Passo 0 encontrou 6 produtos PRODUTO_BASE reais (não 4, como a spec original previa) e mais 505
 * produtos PRODUTO com preco_venda NULL — escopo do backfill ampliado pra cobrir os 511, decisão
 * confirmada com o usuário em 2026-08-07. Estes testes verificam o resultado real do backfill (já
 * aplicado pelo Flyway na subida do contexto) e a nova constraint de schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoEliminaProdutoBaseIT {

    @Autowired ProdutoRepository produtoRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void produtosBoloBaseReclassificadosParaPrudutoComPrecoVendaZeroCalculado() {
        // Os 6 "Bolo Base" pré-existentes (tipo PRODUTO_BASE, preco_custo=0, margem_lucro=NULL,
        // sem config de precificação) foram reclassificados para PRODUTO; fórmula
        // preco_sugerido = preco_custo * (1 + margem/100) com margem=0 (fallback) dá 0.00 exato.
        List<Produto> bolosBase = produtoRepository.findAll().stream()
                .filter(p -> "Bolo Base".equals(p.getNome()))
                .toList();

        assertEquals(6, bolosBase.size(), "esperado exatamente os 6 produtos 'Bolo Base' reais migrados");
        for (Produto p : bolosBase) {
            assertEquals("PRODUTO", p.getTipo().name());
            assertEquals(0, new BigDecimal("0.00").compareTo(p.getPrecoVenda()),
                    "preco_venda deve ser 0.00 (preco_custo=0 * qualquer margem = 0)");
            assertEquals(0, new BigDecimal("0.00").compareTo(p.getMargemLucro()),
                    "margem_lucro deve ter sido resolvida para 0 (sem margem própria nem config de precificação)");
            assertTrue(!p.getOverride(), "backfill não é override — é o preço inicial calculado");
        }
    }

    @Test
    void nenhumProdutoRestaComPrecoVendaNuloAposMigration() {
        Long comPrecoVendaNulo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM produtos WHERE preco_venda IS NULL", Long.class);
        assertEquals(0L, comPrecoVendaNulo);
    }

    @Test
    void constraintChkProdutoTipoRejeitaProdutoBase() {
        UUID usuarioId = criarUsuarioRaw();
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO produtos (usuario_id, numero, nome, tipo, tempo_producao, preco_venda) " +
                        "VALUES (?, 999, 'Teste PRODUTO_BASE Raw', 'PRODUTO_BASE', 10, 5.00)",
                usuarioId));
    }

    @Test
    void constraintChkPrecoVendaTipoExigeNaoNuloParaQualquerTipo() {
        UUID usuarioId = criarUsuarioRaw();
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO produtos (usuario_id, numero, nome, tipo, tempo_producao, preco_venda) " +
                        "VALUES (?, 999, 'Teste Preco Nulo', 'PRODUTO', 10, NULL)",
                usuarioId));
    }

    private UUID criarUsuarioRaw() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO usuarios (id, email, senha_hash, ativo) VALUES (?, ?, 'x', true)",
                id, "produto-elimina-base-" + UUID.randomUUID() + "@test.com");
        return id;
    }
}
