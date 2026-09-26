-- V0.15.0 — #560/RN-NOVA-22 (DT-NOVA-10): data de entrega do orçamento, "data da compra" do cliente
-- nos indicadores e gráficos. Gravada uma única vez na transição PAGO → ENTREGUE, nunca
-- sobrescrita (mesmo padrão de data_pagamento, V39).
ALTER TABLE orcamentos ADD COLUMN data_entrega TIMESTAMP;

-- Orçamentos já entregues antes desta versão: data de pagamento e, na falta dela, a última
-- atualização (aproximação aceita pelo usuário, Decisão 19).
UPDATE orcamentos SET data_entrega = COALESCE(data_pagamento, updated_at) WHERE status = 'ENTREGUE';
