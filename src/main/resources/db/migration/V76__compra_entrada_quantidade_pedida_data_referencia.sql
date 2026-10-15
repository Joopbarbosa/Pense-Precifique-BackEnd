-- V0.17.0 (#959, DT-NOVA-2 e DT-NOVA-4) — entrada de compra pendente.
-- quantidade_pedida: guardada na entrada; compra_itens.quantidade passa a ser a recebida.
ALTER TABLE compra_itens ADD COLUMN quantidade_pedida NUMERIC(15, 4);
-- data_referencia: data do recebimento exibida no histórico do insumo; ordem e saldos seguem created_at.
ALTER TABLE movimentacoes_insumo ADD COLUMN data_referencia DATE;
