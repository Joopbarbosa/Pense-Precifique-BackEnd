-- V0.18.0 (#961, DT-NOVA-13, RN-NOVA-28): o histórico do preço sugerido passa a aceitar o item de Catálogo como origem
-- alternativa ao Produto (mesma tabela, origem exclusiva: um dos dois, nunca os dois). Sem registro retroativo.
ALTER TABLE mudancas_preco_sugerido ADD COLUMN item_catalogo_id UUID REFERENCES itens_catalogo(id);
ALTER TABLE mudancas_preco_sugerido ALTER COLUMN produto_id DROP NOT NULL;
ALTER TABLE mudancas_preco_sugerido ADD CONSTRAINT chk_mudanca_preco_sugerido_origem
  CHECK ((produto_id IS NOT NULL AND item_catalogo_id IS NULL) OR (produto_id IS NULL AND item_catalogo_id IS NOT NULL));
-- causas novas: edição do próprio item e mudança só da margem do item
ALTER TABLE mudancas_preco_sugerido DROP CONSTRAINT chk_mudanca_preco_sugerido_causa;
ALTER TABLE mudancas_preco_sugerido ADD CONSTRAINT chk_mudanca_preco_sugerido_causa CHECK (causa IN
  ('COMPRA_ENTRADA', 'COMPRA_CANCELAMENTO', 'PRODUTO_EDICAO', 'PRODUTO_MARGEM', 'INSUMO_SUBSTITUICAO', 'VALOR_HORA',
   'ITEM_EDICAO', 'ITEM_MARGEM'));
CREATE INDEX idx_mudancas_preco_sugerido_item ON mudancas_preco_sugerido (item_catalogo_id, created_at DESC);
