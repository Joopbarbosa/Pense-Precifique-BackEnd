-- #490 — MovimentacaoProduto ganha motivo/referencia_tipo = CAIXA (RN-NOVA-14, V0.12.0), para
-- baixa/reversao de estoque originada de venda de Caixa/PDV (Epic #416). Mesma referencia solta
-- ja usada por PRODUCAO/ORCAMENTO (DT-NOVA-3 em version/V0.12.0/DECISOES_V0.12.0.md) — sem FK,
-- so amplia o CHECK.
ALTER TABLE movimentacoes_produto DROP CONSTRAINT chk_mov_produto_motivo;

ALTER TABLE movimentacoes_produto ADD CONSTRAINT chk_mov_produto_motivo CHECK (motivo IN (
  'PRODUCAO', 'ORCAMENTO', 'PERDA', 'AVARIA', 'USO_EXTRA', 'CORRECAO', 'OUTRO', 'ESTORNO_PRODUCAO', 'CAIXA'
));

ALTER TABLE movimentacoes_produto DROP CONSTRAINT chk_mov_produto_referencia_tipo;

ALTER TABLE movimentacoes_produto ADD CONSTRAINT chk_mov_produto_referencia_tipo CHECK (
  referencia_tipo IS NULL OR referencia_tipo IN ('PRODUCAO', 'ORCAMENTO', 'CAIXA')
);
