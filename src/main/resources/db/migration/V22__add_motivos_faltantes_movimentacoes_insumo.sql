-- #148 — Adiciona PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO ao motivo de movimentacoes_insumo
-- Constraint original (V1) só permitia COMPRA/BAIXA_MANUAL/PRODUCAO/ORCAMENTO/ESTORNO_PRODUCAO,
-- bloqueando no banco os motivos que o enum MotivoMovimentacaoInsumo passou a aceitar.
ALTER TABLE movimentacoes_insumo DROP CONSTRAINT chk_mov_insumo_motivo;

ALTER TABLE movimentacoes_insumo ADD CONSTRAINT chk_mov_insumo_motivo CHECK (motivo IN (
  'COMPRA', 'BAIXA_MANUAL', 'PERDA', 'AVARIA', 'USO_EXTRA', 'CORRECAO', 'OUTRO', 'PRODUCAO', 'ORCAMENTO', 'ESTORNO_PRODUCAO'
));
