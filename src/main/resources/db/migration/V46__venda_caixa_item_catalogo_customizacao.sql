-- Reabertura de RN-NOVA-1 (V0.12.0, #487) — achado do teste manual: Caixa passa a vender também
-- ItemCatalogo (com customizações), não só Produto direto. Mesmo padrão XOR já usado em
-- orcamento_itens (V9) e mesma tabela de customização anexada de orcamento_item_customizacoes.

ALTER TABLE venda_caixa_item
ALTER COLUMN produto_id DROP NOT NULL;

ALTER TABLE venda_caixa_item
ADD COLUMN item_catalogo_id UUID;

ALTER TABLE venda_caixa_item
ADD CONSTRAINT fk_venda_caixa_item_catalogo FOREIGN KEY (item_catalogo_id) REFERENCES itens_catalogo(id);

CREATE INDEX idx_venda_caixa_item_catalogo_id ON venda_caixa_item(item_catalogo_id);

ALTER TABLE venda_caixa_item
ADD CONSTRAINT chk_venda_caixa_item_origem_xor
CHECK (
  (item_catalogo_id IS NOT NULL AND produto_id IS NULL)
  OR (item_catalogo_id IS NULL AND produto_id IS NOT NULL)
);

CREATE TABLE venda_caixa_item_customizacao (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  venda_caixa_item_id UUID NOT NULL,
  produto_id          UUID NOT NULL,
  quantidade          INTEGER NOT NULL,
  preco_unitario      DECIMAL(15,2) NOT NULL,
  subtotal            DECIMAL(15,2) NOT NULL,
  CONSTRAINT fk_venda_caixa_item_cust_item FOREIGN KEY (venda_caixa_item_id) REFERENCES venda_caixa_item(id),
  CONSTRAINT fk_venda_caixa_item_cust_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

CREATE INDEX idx_venda_caixa_item_cust_item_id ON venda_caixa_item_customizacao(venda_caixa_item_id);
