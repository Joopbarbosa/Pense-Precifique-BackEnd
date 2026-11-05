-- V0.19.0 (#1177 / PP-525, F6, RN-NOVA-23, DT-NOVA-7): categorias de despesa por usuária.
-- A semente (Insumos e materiais, Embalagem, Frete, Aluguel, Contas, Outros) é criada de forma idempotente pelo serviço,
-- com INSERT ... ON CONFLICT DO NOTHING; o nome normalizado (sem acento, minúsculo, aparado) é único por usuária.
CREATE TABLE categorias_financeiras (
  id               UUID PRIMARY KEY,
  usuario_id       UUID NOT NULL REFERENCES usuarios(id),
  nome             VARCHAR(40) NOT NULL,
  nome_normalizado VARCHAR(40) NOT NULL,
  semeada          BOOLEAN NOT NULL DEFAULT FALSE,
  ativa            BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMP NOT NULL,
  updated_at       TIMESTAMP NOT NULL,
  CONSTRAINT uq_categorias_fin_usuario_nome UNIQUE (usuario_id, nome_normalizado),
  CONSTRAINT chk_categorias_fin_nome CHECK (char_length(nome) BETWEEN 2 AND 40)
);
CREATE INDEX idx_categorias_fin_usuario ON categorias_financeiras (usuario_id);

-- Só despesa (A_PAGAR) tem categoria; a regra é do LancamentoFinanceiroService (lançamentos antigos ficam sem categoria).
ALTER TABLE lancamentos_financeiros ADD COLUMN categoria_id UUID REFERENCES categorias_financeiras(id);
CREATE INDEX idx_lancamentos_fin_categoria ON lancamentos_financeiros (categoria_id) WHERE categoria_id IS NOT NULL;
