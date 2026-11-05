-- V0.19.0 (#692, RN-NOVA-58) — multa do cancelamento em R$ ou em %: grava também o valor total da multa
-- (referência dos lançamentos), porque valor_multa guarda só a parte a receber (multa menos o recebido) e o
-- percentual arredondado a 2 casas pode perder o valor (R$ 0,01 de R$ 1.000,00 = 0,00%). Nulo = sem multa
-- ou cancelamento anterior à regra; sem backfill.
ALTER TABLE orcamentos ADD COLUMN valor_multa_bruta DECIMAL(10,2);
