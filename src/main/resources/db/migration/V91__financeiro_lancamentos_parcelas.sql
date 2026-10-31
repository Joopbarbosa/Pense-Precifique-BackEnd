-- V0.19.0 (#1172 / PP-520, F1, DT-NOVA-1, RN-NOVA-1 a 6): lançamento a pagar/receber e suas parcelas.
-- "Vencida" NÃO é gravada (DT-NOVA-2): é derivada na consulta (Em aberto com vencimento anterior a hoje, relógio de Brasília).
-- O valor total do lançamento é sempre a soma das parcelas não canceladas (recalculado pelo LancamentoFinanceiroService).
-- Categoria (F6) e marca "fora da projeção" (F4) entram nas migrações das próprias tarefas.
CREATE TABLE lancamentos_financeiros (
  id          UUID PRIMARY KEY,
  usuario_id  UUID NOT NULL REFERENCES usuarios(id),
  tipo        VARCHAR(10) NOT NULL,
  situacao    VARCHAR(10) NOT NULL DEFAULT 'ATIVO',
  descricao   VARCHAR(500) NOT NULL,
  valor_total NUMERIC(12, 2) NOT NULL,
  origem      VARCHAR(10) NOT NULL,
  compra_id   UUID REFERENCES compras(id),
  orcamento_id UUID REFERENCES orcamentos(id),
  created_at  TIMESTAMP NOT NULL,
  updated_at  TIMESTAMP NOT NULL,
  CONSTRAINT chk_lancamentos_fin_tipo CHECK (tipo IN ('A_PAGAR', 'A_RECEBER')),
  CONSTRAINT chk_lancamentos_fin_situacao CHECK (situacao IN ('ATIVO', 'CANCELADO')),
  CONSTRAINT chk_lancamentos_fin_origem CHECK (origem IN ('COMPRA', 'ORCAMENTO', 'AVULSO')),
  CONSTRAINT chk_lancamentos_fin_valor CHECK (valor_total >= 0 AND valor_total <= 99999999.99),
  CONSTRAINT chk_lancamentos_fin_vinculo CHECK (
    (origem = 'COMPRA' AND compra_id IS NOT NULL AND orcamento_id IS NULL)
    OR (origem = 'ORCAMENTO' AND orcamento_id IS NOT NULL AND compra_id IS NULL)
    OR (origem = 'AVULSO' AND compra_id IS NULL AND orcamento_id IS NULL))
);
CREATE INDEX idx_lancamentos_fin_usuario ON lancamentos_financeiros (usuario_id, tipo);
CREATE INDEX idx_lancamentos_fin_compra ON lancamentos_financeiros (compra_id) WHERE compra_id IS NOT NULL;
CREATE INDEX idx_lancamentos_fin_orcamento ON lancamentos_financeiros (orcamento_id) WHERE orcamento_id IS NOT NULL;

CREATE TABLE parcelas_financeiras (
  id                  UUID PRIMARY KEY,
  lancamento_id       UUID NOT NULL REFERENCES lancamentos_financeiros(id),
  usuario_id          UUID NOT NULL REFERENCES usuarios(id),
  numero              INTEGER NOT NULL,
  valor               NUMERIC(12, 2) NOT NULL,
  vencimento          DATE NOT NULL,
  situacao            VARCHAR(10) NOT NULL DEFAULT 'EM_ABERTO',
  data_baixa          DATE,
  metodo_pagamento_id UUID REFERENCES metodos_pagamento(id),
  origem_baixa        VARCHAR(10),
  created_at          TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP NOT NULL,
  CONSTRAINT chk_parcelas_fin_situacao CHECK (situacao IN ('EM_ABERTO', 'BAIXADA', 'CANCELADA')),
  CONSTRAINT chk_parcelas_fin_origem_baixa CHECK (origem_baixa IS NULL OR origem_baixa IN ('AUTOMATICA', 'USUARIA')),
  CONSTRAINT chk_parcelas_fin_valor CHECK (valor > 0 AND valor <= 99999999.99),
  CONSTRAINT chk_parcelas_fin_numero CHECK (numero >= 1),
  CONSTRAINT chk_parcelas_fin_baixa CHECK (
    (situacao = 'BAIXADA' AND data_baixa IS NOT NULL AND metodo_pagamento_id IS NOT NULL AND origem_baixa IS NOT NULL)
    OR (situacao <> 'BAIXADA' AND data_baixa IS NULL AND metodo_pagamento_id IS NULL AND origem_baixa IS NULL))
);
CREATE INDEX idx_parcelas_fin_usuario_vencimento ON parcelas_financeiras (usuario_id, vencimento);
CREATE INDEX idx_parcelas_fin_usuario_situacao ON parcelas_financeiras (usuario_id, situacao);
CREATE INDEX idx_parcelas_fin_lancamento ON parcelas_financeiras (lancamento_id);
