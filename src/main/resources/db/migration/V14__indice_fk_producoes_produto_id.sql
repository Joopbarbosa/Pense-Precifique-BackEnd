-- Índice de apoio à FK producoes.produto_id
-- Melhora performance de consultas de histórico de produção por produto
CREATE INDEX IF NOT EXISTS idx_producoes_produto_id
    ON producoes (produto_id);
