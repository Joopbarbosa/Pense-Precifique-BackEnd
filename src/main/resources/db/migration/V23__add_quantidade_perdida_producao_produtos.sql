-- #188/RN-NOVA-4 (Opção A) — perda declarada ao finalizar produção desconta da quantidade que
-- entra em estoque (planejada - perda), não é registro paralelo. Persistida por produto da
-- produção (uma Producao agrupa N produtos — RN-061), não um valor único pra produção inteira.
ALTER TABLE producao_produtos
    ADD COLUMN quantidade_perdida NUMERIC(15,4) NOT NULL DEFAULT 0;
