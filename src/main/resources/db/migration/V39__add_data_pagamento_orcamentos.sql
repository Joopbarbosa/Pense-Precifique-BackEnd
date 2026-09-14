-- DT-NOVA-4 (V0.10.0, #466) — RN-NOVA-10 troca a ordem FINALIZADO->ENTREGUE->PAGO por
-- FINALIZADO->PAGO->ENTREGUE. A condição de visibilidade do "Recibo de pagamento" no preview do
-- Orçamento (documentosDisponiveis(), frontend) usava status = 'PAGO' (igualdade exata) — com a
-- nova ordem, isso faria o recibo sumir do preview assim que o orçamento avançasse pra ENTREGUE.
-- data_pagamento é setado uma única vez, na transição para PAGO, e nunca sobrescrito depois — mesmo
-- padrão já usado por data_sinal_pago/data_estorno_sinal (evento pontual, não status atual).
ALTER TABLE orcamentos
    ADD COLUMN data_pagamento TIMESTAMP;

-- Backfill: orçamentos já em PAGO ou ENTREGUE (estado terminal alcançado antes desta migration)
-- recebem data_pagamento = updated_at como aproximação razoável (a transição real que gerou o
-- ReciboPagamento não guardou timestamp próprio até agora) — nenhum orçamento nessas duas situações
-- deveria ficar sem o recibo visível por causa da mudança de regra.
UPDATE orcamentos
SET data_pagamento = updated_at
WHERE status IN ('PAGO', 'ENTREGUE')
  AND data_pagamento IS NULL;
