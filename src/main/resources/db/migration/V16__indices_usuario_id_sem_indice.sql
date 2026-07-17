-- Índices em usuario_id para tabelas sem índice identificadas na auditoria
-- empresas, configuracoes_precificacao e lotes_compra têm apenas 1 registro
-- por usuária hoje, mas o índice garante performance com crescimento de contas
CREATE INDEX IF NOT EXISTS idx_empresas_usuario_id
    ON empresas (usuario_id);

CREATE INDEX IF NOT EXISTS idx_configuracoes_precificacao_usuario_id
    ON configuracoes_precificacao (usuario_id);

CREATE INDEX IF NOT EXISTS idx_lotes_compra_usuario_id
    ON lotes_compra (usuario_id);
