-- V0.17.0 (#957, RN-NOVA-4, DT-NOVA-1) — entrega imediata, previsão de entrega e data de entrada da compra.
-- entrega_imediata nulo = não se aplica (compras anteriores à V0.17.0); só o registro novo grava sim ou não.
ALTER TABLE compras ADD COLUMN entrega_imediata BOOLEAN;
ALTER TABLE compras ADD COLUMN previsao_entrega DATE;
ALTER TABLE compras ADD COLUMN data_entrada DATE;

-- Backfill: toda compra confirmada já entrou no estoque na data da compra (RN-NOVA-9).
UPDATE compras SET data_entrada = data_compra WHERE status = 'CONFIRMADA';
