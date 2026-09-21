-- #516 (V0.13.0, RN-NOVA-1/9) — Insumo passou a poder ser componente direto de Item de Catalogo
-- (V51); vender esse item pelo Caixa agora baixa/reverte o estoque do Insumo tambem, algo que nunca
-- acontecia antes desta versao (Insumo nao tinha vinculo de catalogo nenhum). Mesmo espirito de
-- V42 (motivo/referencia_tipo CAIXA em movimentacoes_produto), so ampliando o CHECK.
ALTER TABLE movimentacoes_insumo DROP CONSTRAINT chk_mov_insumo_motivo;

ALTER TABLE movimentacoes_insumo ADD CONSTRAINT chk_mov_insumo_motivo CHECK (motivo IN (
  'COMPRA', 'BAIXA_MANUAL', 'PERDA', 'AVARIA', 'USO_EXTRA', 'CORRECAO', 'OUTRO',
  'PRODUCAO', 'ORCAMENTO', 'ESTORNO_PRODUCAO', 'CAIXA'
));

ALTER TABLE movimentacoes_insumo DROP CONSTRAINT chk_mov_insumo_referencia_tipo;

ALTER TABLE movimentacoes_insumo ADD CONSTRAINT chk_mov_insumo_referencia_tipo CHECK (
  referencia_tipo IS NULL OR referencia_tipo IN ('PRODUCAO', 'ORCAMENTO', 'LOTE_COMPRA', 'CAIXA')
);
