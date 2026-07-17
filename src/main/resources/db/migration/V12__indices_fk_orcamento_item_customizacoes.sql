-- Índice de apoio à FK orcamento_item_customizacoes.orcamento_item_id
-- Melhora performance de JOINs e cascades ao buscar customizações de um item
CREATE INDEX IF NOT EXISTS idx_oic_orcamento_item_id
    ON orcamento_item_customizacoes (orcamento_item_id);
