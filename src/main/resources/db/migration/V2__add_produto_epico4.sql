-- Épico 4: colunas novas na tabela ficha_tecnica_itens
ALTER TABLE ficha_tecnica_itens
  ADD COLUMN IF NOT EXISTS produto_base_id UUID REFERENCES produtos(id),
  ADD COLUMN IF NOT EXISTS insumo_id UUID REFERENCES insumos(id);

-- Épico 4: tabelas novas
CREATE TABLE IF NOT EXISTS movimentacoes_produto (
  id UUID PRIMARY KEY,
  produto_id UUID NOT NULL REFERENCES produtos(id),
  tipo VARCHAR(20) NOT NULL,
  motivo VARCHAR(30) NOT NULL,
  quantidade NUMERIC(12,4) NOT NULL,
  observacao TEXT,
  referencia_id UUID,
  referencia_tipo VARCHAR(20),
  estornada BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lotes_compra (
  id UUID PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuarios(id),
  data_compra TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
