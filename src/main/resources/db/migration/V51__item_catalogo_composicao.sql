-- #516/#523 (V0.13.0) — Item de Catalogo passa de "1 produto + N customizacoes anexadas" para
-- composicao livre de N componentes (Produto/Customizacao OU Insumo, com quantidade propria cada),
-- mesmo formato ja usado por ficha_tecnica_itens (RN-NOVA-1, DT-NOVA-1).
--
-- Item de Catalogo tambem ganha custo/margem/preco proprios (RN-NOVA-2/3, DT-NOVA-2): nome, tempo
-- de producao e margem de lucro. preco_venda/override ja existiam (herdavam do produto ate aqui) e
-- passam a significar a mesma coisa que ja significam em Produto (preco_sugerido calculado ao vivo
-- no Service, nunca persistido).

ALTER TABLE itens_catalogo ADD COLUMN nome VARCHAR(255);
ALTER TABLE itens_catalogo ADD COLUMN tempo_producao INTEGER NOT NULL DEFAULT 0;
ALTER TABLE itens_catalogo ADD COLUMN margem_lucro DECIMAL(5,2);

-- ============================================================
-- Nova tabela de componentes (substitui a FK direta itens_catalogo.produto_id e a tabela
-- itens_catalogo_customizacao) — mesmo formato de ficha_tecnica_itens: insumo_id XOR
-- produto_base_id, validado so na camada de Service (nao por CHECK), mesmo criterio ja usado la.
-- ============================================================
CREATE TABLE item_catalogo_componentes (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  item_catalogo_id  UUID NOT NULL,
  insumo_id         UUID,
  produto_base_id   UUID,
  quantidade        DECIMAL(15,4) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_item_cat_comp_item FOREIGN KEY (item_catalogo_id) REFERENCES itens_catalogo(id),
  CONSTRAINT fk_item_cat_comp_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_item_cat_comp_produto_base FOREIGN KEY (produto_base_id) REFERENCES produtos(id)
);

CREATE INDEX idx_item_cat_comp_item_id ON item_catalogo_componentes(item_catalogo_id);
CREATE INDEX idx_item_cat_comp_insumo_id ON item_catalogo_componentes(insumo_id);
CREATE INDEX idx_item_cat_comp_produto_base_id ON item_catalogo_componentes(produto_base_id);

-- ============================================================
-- Migracao de dados: cada Item de Catalogo existente vira 1 componente (o antigo "produto
-- principal"); cada customizacao anexada existente vira mais 1 componente do mesmo item.
-- ============================================================
INSERT INTO item_catalogo_componentes (item_catalogo_id, produto_base_id, quantidade)
SELECT id, produto_id, quantidade_pacote FROM itens_catalogo WHERE deleted_at IS NULL;

INSERT INTO item_catalogo_componentes (item_catalogo_id, produto_base_id, quantidade)
SELECT item_catalogo_id, produto_id, quantidade FROM itens_catalogo_customizacao;

-- Backfill do nome (ate aqui o item nao tinha nome proprio, exibia sempre o nome do produto).
UPDATE itens_catalogo ic SET nome = p.nome FROM produtos p WHERE ic.produto_id = p.id;
ALTER TABLE itens_catalogo ALTER COLUMN nome SET NOT NULL;

-- Itens existentes mantem o preco_venda atual travado (override = true): a formula de calculo
-- mudou de "herda o preco de venda do produto" (CAT-003) para "soma o custo dos componentes + mao
-- de obra + margem propria" (RN-NOVA-2/3) — sem isso, o preco exibido de todo catalogo ja
-- cadastrado mudaria sozinho nesta migracao, sem a usuaria ter editado nada.
UPDATE itens_catalogo SET override = true WHERE override = false;

-- ============================================================
-- Remove o modelo antigo (FK direta a produto + tabela de customizacao dedicada).
-- ============================================================
ALTER TABLE itens_catalogo DROP CONSTRAINT fk_item_catalogo_produto;
DROP INDEX idx_itens_catalogo_produto_id;
ALTER TABLE itens_catalogo DROP COLUMN produto_id;
ALTER TABLE itens_catalogo DROP COLUMN quantidade_pacote;

DROP TABLE itens_catalogo_customizacao;
