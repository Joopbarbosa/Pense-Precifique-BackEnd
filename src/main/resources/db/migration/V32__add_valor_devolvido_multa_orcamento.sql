ALTER TABLE orcamentos ADD COLUMN valor_devolvido_multa DECIMAL(10,2);

-- RN-NOVA-1 (V0.8.2, #309+310+311) — mini-estorno: quando o sinal já pago excede o valor bruto
-- da multa, a diferença é devolvida ao cliente. Coluna nula quando não há devolução (sinal <=
-- multa bruta, ou sem sinal pago) — mesmo critério de "nulo = não aplicável" já usado em
-- valor_multa (V31). Sem backfill: nenhum orçamento cancelado antes desta migration tinha essa
-- regra aplicada, então não há histórico a recalcular (diferente de V31, que backfillou
-- valor_multa para registros já existentes).
