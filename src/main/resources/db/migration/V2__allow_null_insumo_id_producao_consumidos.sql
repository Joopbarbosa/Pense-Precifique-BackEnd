-- Permite insumo_id nulo em producao_insumos_consumidos
-- para suportar componentes do tipo produto_base no registro de produção
ALTER TABLE producao_insumos_consumidos
  ALTER COLUMN insumo_id DROP NOT NULL;

-- Coluna para registrar componentes do tipo produto_base consumidos na produção
ALTER TABLE producao_insumos_consumidos
  ADD COLUMN IF NOT EXISTS produto_base_id UUID REFERENCES produtos(id);
