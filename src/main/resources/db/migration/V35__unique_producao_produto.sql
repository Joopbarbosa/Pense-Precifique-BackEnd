-- RN-PROD-VINC-01 (V0.8.2, #320) — a partir de agora, produtos entram numa producao via merge
-- (soma de quantidade), nunca via linha duplicada. Confirmado antes desta migration: 0 duplicatas
-- existentes em producao_produtos (393 linhas totais).

ALTER TABLE producao_produtos
    ADD CONSTRAINT uq_producao_produto UNIQUE (producao_id, produto_id);
