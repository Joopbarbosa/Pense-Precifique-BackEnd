-- Índices de apoio às FKs da tabela producao_insumos_consumidos
-- Melhora performance de cancelamento de produção (consulta por producao_id)
-- e de histórico de insumos (consulta por insumo_id)
CREATE INDEX IF NOT EXISTS idx_pic_producao_id
    ON producao_insumos_consumidos (producao_id);

CREATE INDEX IF NOT EXISTS idx_pic_insumo_id
    ON producao_insumos_consumidos (insumo_id);

CREATE INDEX IF NOT EXISTS idx_pic_produto_base_id
    ON producao_insumos_consumidos (produto_base_id);
