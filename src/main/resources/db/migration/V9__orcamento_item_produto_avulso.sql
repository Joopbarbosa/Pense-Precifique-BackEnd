-- P-031: OrcamentoItem aceita Produto avulso (sem Catálogo) — RN-054
-- item_catalogo_id passa a ser opcional; item passa a poder vir de produto_id + margem_aplicada
-- exatamente um dos dois deve estar preenchido (XOR)

ALTER TABLE orcamento_itens
ALTER COLUMN item_catalogo_id DROP NOT NULL;

ALTER TABLE orcamento_itens
ADD COLUMN produto_id UUID,
ADD COLUMN margem_aplicada NUMERIC(5,2);

ALTER TABLE orcamento_itens
ADD CONSTRAINT fk_orcamento_item_produto
FOREIGN KEY (produto_id) REFERENCES produtos(id);

CREATE INDEX idx_orcamento_itens_produto_id
ON orcamento_itens(produto_id);

ALTER TABLE orcamento_itens
ADD CONSTRAINT chk_orcamento_item_origem_xor
CHECK (
  (item_catalogo_id IS NOT NULL AND produto_id IS NULL)
  OR (item_catalogo_id IS NULL AND produto_id IS NOT NULL)
);
