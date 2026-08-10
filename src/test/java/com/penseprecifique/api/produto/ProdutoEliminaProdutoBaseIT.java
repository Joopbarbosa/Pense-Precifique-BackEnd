package com.penseprecifique.api.produto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #210+231+234 — migration V28 (elimina TipoProduto.PRODUTO_BASE, unifica modelo de preço).
 * Passo 0 encontrou 6 produtos PRODUTO_BASE reais (não 4, como a spec original previa) e mais 505
 * produtos PRODUTO com preco_venda NULL — escopo do backfill ampliado pra cobrir os 511, decisão
 * confirmada com o usuário em 2026-08-07. Estes testes verificam o efeito permanente da migration
 * (nenhum produto fora do domínio novo de tipo, nenhum preco_venda nulo) e a nova constraint de
 * schema — não os valores exatos do backfill histórico: essa fórmula (fallback de margem) só
 * existia no SQL da migration, roda uma vez só via Flyway, e não tem equivalente reexecutável na
 * aplicação, então não é testável via dado próprio depois que a migration já rodou. Um teste
 * anterior aqui assumia contagem fixa de 6 produtos "Bolo Base" reais e quebrou em 2026-08-08
 * quando esses registros somem do banco de dev (fora do controle deste teste) — substituído por
 * invariantes genéricas que não dependem de dado histórico específico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoEliminaProdutoBaseIT {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void nenhumProdutoTemTipoForaDoDominioAtual() {
        Long foraDoDominio = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM produtos WHERE tipo NOT IN ('PRODUTO', 'CUSTOMIZACAO')", Long.class);
        assertEquals(0L, foraDoDominio, "migration V28 deve ter reclassificado todo PRODUTO_BASE para PRODUTO");
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
