-- #298 (DT-NOVA-2, V0.14.0) — nova tabela unidades_medida (nome+sigla, cadastrável em
-- Configurações). Insumo.unidade_medida (texto livre) é substituída por
-- Insumo.unidade_medida_id (FK) — sem coluna de transição, tudo na mesma migration.

CREATE TABLE unidades_medida (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id  UUID NOT NULL,
  nome        VARCHAR(100) NOT NULL,
  sigla       VARCHAR(20) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMP,
  CONSTRAINT fk_unidade_medida_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_unidades_medida_usuario_id ON unidades_medida(usuario_id);

-- RN-NOVA-8 — nome único por usuária, case-insensitive (mesmo critério de metodos_pagamento, V40).
CREATE UNIQUE INDEX idx_unidades_medida_nome_usuario
  ON unidades_medida (usuario_id, LOWER(nome))
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_unidades_medida_sigla_usuario
  ON unidades_medida (usuario_id, sigla)
  WHERE deleted_at IS NULL;

-- ============================================================
-- Backfill: 1 linha de unidades_medida por (usuario_id, valor distinto hoje livre em
-- insumos.unidade_medida, agrupado sem diferenciar maiúscula/minúscula para não violar o índice
-- único de nome case-insensitive acima) — nome = sigla = valor legado (dado livre não tem nome
-- descritivo; editável depois pela usuária). Inclui insumos já excluídos (deleted_at) — nenhum
-- dado legado, ativo ou não, fica sem unidade cadastrada (decisão explícita do usuário).
-- ============================================================
INSERT INTO unidades_medida (usuario_id, nome, sigla)
SELECT DISTINCT ON (usuario_id, LOWER(unidade_medida)) usuario_id, unidade_medida, unidade_medida
FROM insumos
ORDER BY usuario_id, LOWER(unidade_medida), unidade_medida;

ALTER TABLE insumos ADD COLUMN unidade_medida_id UUID;

-- Join case-insensitive: o valor legado do insumo pode ter casing diferente do representante
-- escolhido acima pelo DISTINCT ON para o mesmo grupo (usuario_id, LOWER(unidade_medida)).
UPDATE insumos i
SET unidade_medida_id = um.id
FROM unidades_medida um
WHERE um.usuario_id = i.usuario_id AND LOWER(um.sigla) = LOWER(i.unidade_medida);

ALTER TABLE insumos ALTER COLUMN unidade_medida_id SET NOT NULL;
ALTER TABLE insumos ADD CONSTRAINT fk_insumo_unidade_medida FOREIGN KEY (unidade_medida_id) REFERENCES unidades_medida(id);
CREATE INDEX idx_insumos_unidade_medida_id ON insumos(unidade_medida_id);

ALTER TABLE insumos DROP COLUMN unidade_medida;
