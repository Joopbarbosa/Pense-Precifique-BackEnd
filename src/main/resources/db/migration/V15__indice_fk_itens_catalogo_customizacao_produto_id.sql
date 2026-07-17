-- Índice de apoio à FK itens_catalogo_customizacao.produto_id
-- Melhora performance de consultas de customizações fixas por produto
CREATE INDEX IF NOT EXISTS idx_icc_produto_id
    ON itens_catalogo_customizacao (produto_id);
