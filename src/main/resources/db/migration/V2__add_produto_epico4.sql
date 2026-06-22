-- Épicos 3 e 4: colunas que existem nas entidades JPA mas faltam no schema V1

-- insumos: entidade mapeia custo_unitario; V1 tem preco_custo (nome diferente)
ALTER TABLE insumos
  ADD COLUMN IF NOT EXISTS custo_unitario DECIMAL(15,4) NOT NULL DEFAULT 0;

-- produtos: entidade mapeia foto; V1 tem foto_url (nome diferente)
ALTER TABLE produtos
  ADD COLUMN IF NOT EXISTS foto TEXT;

-- ficha_tecnica_itens: entidade mapeia produto_base_id; V1 tem produto_componente_id (nome diferente)
ALTER TABLE ficha_tecnica_itens
  ADD COLUMN IF NOT EXISTS produto_base_id UUID REFERENCES produtos(id);
