-- V0.15.0 (#575, RN-NOVA-26, DT-NOVA-16) — custo de material por unidade gravado no momento em que o
-- pedido passa a contar como venda (orçamento PAGO → ENTREGUE; venda do Caixa ao registrar). Nulo =
-- venda anterior a esta versão: o CMV estima pelo custo de hoje e sinaliza (RN-NOVA-27). Sem backfill.
ALTER TABLE orcamento_itens                ADD COLUMN custo_material_unitario NUMERIC(15, 4);
ALTER TABLE orcamento_item_customizacoes   ADD COLUMN custo_material_unitario NUMERIC(15, 4);
ALTER TABLE venda_caixa_item               ADD COLUMN custo_material_unitario NUMERIC(15, 4);
ALTER TABLE venda_caixa_item_customizacao  ADD COLUMN custo_material_unitario NUMERIC(15, 4);
