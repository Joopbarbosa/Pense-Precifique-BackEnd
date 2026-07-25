-- #142 — reforça a invariante já assumida em todo o sistema ("uma empresa por usuária",
-- "uma configuração por usuária" — já documentado em BUSINESS_RULES.md), ausente até aqui.

-- empresas tem soft delete (deleted_at) — índice parcial, mesmo padrão de idx_insumos_nome_marca_usuario (V1).
CREATE UNIQUE INDEX uq_empresas_usuario_id
  ON empresas (usuario_id)
  WHERE deleted_at IS NULL;

-- configuracoes_precificacao não tem soft delete — constraint direta.
ALTER TABLE configuracoes_precificacao
  ADD CONSTRAINT uq_config_precificacao_usuario_id UNIQUE (usuario_id);
