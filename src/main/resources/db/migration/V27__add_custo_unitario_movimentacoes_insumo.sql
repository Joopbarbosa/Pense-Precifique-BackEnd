-- Custo unitário do insumo no momento da movimentação — snapshot, não recalculado depois.
-- NULL para registros anteriores a esta migration (dado não existia antes, sem forma de
-- reconstruir retroativamente o valor histórico correto).
ALTER TABLE movimentacoes_insumo ADD COLUMN custo_unitario DECIMAL(15,4);
