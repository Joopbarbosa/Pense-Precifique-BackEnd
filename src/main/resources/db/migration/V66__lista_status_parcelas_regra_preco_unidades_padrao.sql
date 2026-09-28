-- V0.15.0 — adendo 2 do teste manual (DT-NOVA-24, 25, 26, 28).

-- ============================================================
-- #590 / RN-NOVA-39 / DT-NOVA-24 — regra do preço de referência, no insumo, vale para todos os
-- fornecedores dele. O valor continua em fornecedor_insumo.preco_referencia (a Lista de compras lê
-- a coluna). Backfill: todo insumo nasce com MEDIA, então os vínculos existentes passam a ter a
-- média ponderada das compras CONFIRMADAS do par nos últimos 12 meses (mesma conta de
-- FornecedorInsumoService.recalcular); par sem compra na janela mantém o valor atual.
-- ============================================================
ALTER TABLE insumos ADD COLUMN regra_preco_referencia VARCHAR(20) NOT NULL DEFAULT 'MEDIA';
ALTER TABLE insumos ADD CONSTRAINT chk_insumos_regra_preco_referencia
    CHECK (regra_preco_referencia IN ('MEDIA', 'MENOR_VALOR', 'MANUAL'));

UPDATE fornecedor_insumo fi
SET preco_referencia = s.media, updated_at = NOW()
FROM (SELECT ci.fornecedor_id, ci.insumo_id, ROUND(SUM(ci.preco_total) / SUM(ci.quantidade), 4) AS media
      FROM compra_itens ci
      JOIN compras c ON c.id = ci.compra_id
      WHERE c.status = 'CONFIRMADA' AND c.deleted_at IS NULL
        AND c.data_compra >= CURRENT_DATE - INTERVAL '12 months'
        AND ci.fornecedor_id IS NOT NULL AND ci.quantidade > 0 AND ci.preco_total IS NOT NULL
      GROUP BY ci.fornecedor_id, ci.insumo_id) s
WHERE fi.fornecedor_id = s.fornecedor_id AND fi.insumo_id = s.insumo_id AND s.media > 0;

-- ============================================================
-- #596 / RN-NOVA-41 / DT-NOVA-25 — status da lista de compras, rascunho e compra ligada à lista.
-- Listas existentes já foram geradas → GERADA. Rascunho não tem gerada_em.
-- ============================================================
ALTER TABLE listas_compra ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'GERADA';
ALTER TABLE listas_compra ADD CONSTRAINT chk_listas_compra_status
    CHECK (status IN ('RASCUNHO', 'GERADA', 'PARCIALMENTE_COMPRADA', 'COMPRADA', 'CANCELADA'));
ALTER TABLE listas_compra ALTER COLUMN gerada_em DROP NOT NULL;
ALTER TABLE listas_compra ALTER COLUMN gerada_em DROP DEFAULT;
ALTER TABLE listas_compra ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
UPDATE listas_compra SET created_at = gerada_em WHERE gerada_em IS NOT NULL;

-- Rascunho pode ter linha ainda sem quantidade (exigida ao gerar).
ALTER TABLE lista_compra_itens ALTER COLUMN quantidade DROP NOT NULL;

ALTER TABLE compras ADD COLUMN lista_compra_id UUID;
ALTER TABLE compras ADD CONSTRAINT fk_compras_lista_compra FOREIGN KEY (lista_compra_id) REFERENCES listas_compra(id);
CREATE INDEX idx_compras_lista_compra_id ON compras(lista_compra_id);

-- ============================================================
-- #597 / RN-NOVA-42 / DT-NOVA-26 — parcelas quando a compra é paga no cartão de crédito.
-- ============================================================
ALTER TABLE compras ADD COLUMN parcelas SMALLINT;
ALTER TABLE compras ADD CONSTRAINT chk_compras_parcelas CHECK (parcelas IS NULL OR parcelas >= 1);

-- ============================================================
-- #605 / Alteração de INS-015 / DT-NOVA-28 — 11 unidades de medida do sistema por conta, sem
-- edição nem exclusão. Contas novas recebem as unidades no cadastro (AuthService).
-- Contas existentes: unidade com a mesma sigla (sem diferenciar maiúscula) vira a do sistema e
-- recebe o nome padrão quando o nome não conflita; as que faltam são criadas quando nem nome nem
-- sigla conflitam com uma unidade da pessoa (a unidade dela fica como está).
-- ============================================================
ALTER TABLE unidades_medida ADD COLUMN padrao BOOLEAN NOT NULL DEFAULT false;

CREATE TEMP TABLE unidades_padrao (nome VARCHAR(100), sigla VARCHAR(20)) ON COMMIT DROP;
INSERT INTO unidades_padrao (nome, sigla) VALUES
  ('Unidade', 'un'), ('Rolo', 'rolo'), ('Folha', 'folha'), ('Metro', 'm'), ('Centímetro', 'cm'),
  ('Milímetro', 'mm'), ('Metro quadrado', 'm²'), ('Quilo', 'kg'), ('Grama', 'g'), ('Litro', 'L'),
  ('Mililitro', 'mL');

UPDATE unidades_medida um
SET padrao = true, sigla = p.sigla, updated_at = NOW()
FROM unidades_padrao p
WHERE um.deleted_at IS NULL AND LOWER(um.sigla) = LOWER(p.sigla)
  AND NOT EXISTS (SELECT 1 FROM unidades_medida o
                  WHERE o.usuario_id = um.usuario_id AND o.deleted_at IS NULL AND o.id <> um.id
                    AND o.sigla = p.sigla);

UPDATE unidades_medida um
SET nome = p.nome
FROM unidades_padrao p
WHERE um.padrao AND um.sigla = p.sigla AND um.nome <> p.nome
  AND NOT EXISTS (SELECT 1 FROM unidades_medida o
                  WHERE o.usuario_id = um.usuario_id AND o.deleted_at IS NULL AND o.id <> um.id
                    AND LOWER(o.nome) = LOWER(p.nome));

INSERT INTO unidades_medida (usuario_id, nome, sigla, padrao)
SELECT u.id, p.nome, p.sigla, true
FROM usuarios u CROSS JOIN unidades_padrao p
WHERE NOT EXISTS (SELECT 1 FROM unidades_medida o
                  WHERE o.usuario_id = u.id AND o.deleted_at IS NULL
                    AND (LOWER(o.sigla) = LOWER(p.sigla) OR LOWER(o.nome) = LOWER(p.nome)));
