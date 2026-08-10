-- #239 (V0.7) — Catálogo deixa de ter margem própria; preço do item de catálogo passa a herdar
-- produto.preco_venda × quantidade_pacote (+ preco_venda das customizações anexadas), mesma lógica
-- calculado+override já usada em Produto (PDT-005). Sem backfill: preco_venda de itens_catalogo
-- já persistidos permanece exatamente como está, não é recalculado por esta migration.
ALTER TABLE catalogos DROP CONSTRAINT chk_catalogo_margem_positiva;
ALTER TABLE catalogos DROP COLUMN margem;
