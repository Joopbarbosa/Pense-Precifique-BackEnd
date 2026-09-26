-- V0.15.0 — #541/#544/#542 (DT-NOVA-4). Uma migração só para os dois CHECKs de
-- movimentacoes_insumo, no padrão de V42/V54:
-- - motivo ganha ESTORNO_COMPRA (cancelamento de compra, RN-NOVA-9);
-- - referencia_tipo ganha COMPRA (referencia_id = compras.id) e perde LOTE_COMPRA, sem uso desde a
--   remoção dos registros antigos (V60).
ALTER TABLE movimentacoes_insumo DROP CONSTRAINT chk_mov_insumo_motivo;

ALTER TABLE movimentacoes_insumo ADD CONSTRAINT chk_mov_insumo_motivo CHECK (motivo IN (
  'COMPRA', 'BAIXA_MANUAL', 'PERDA', 'AVARIA', 'USO_EXTRA', 'CORRECAO', 'OUTRO',
  'PRODUCAO', 'ORCAMENTO', 'ESTORNO_PRODUCAO', 'CAIXA', 'ESTORNO_COMPRA'
));

ALTER TABLE movimentacoes_insumo DROP CONSTRAINT chk_mov_insumo_referencia_tipo;

ALTER TABLE movimentacoes_insumo ADD CONSTRAINT chk_mov_insumo_referencia_tipo CHECK (
  referencia_tipo IS NULL OR referencia_tipo IN ('PRODUCAO', 'ORCAMENTO', 'CAIXA', 'COMPRA')
);
