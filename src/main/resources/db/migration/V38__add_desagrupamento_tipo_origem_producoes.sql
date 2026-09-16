-- RN-NOVA-5 (V0.10.0, #450) — desagrupar produção agrupada: cada produto da produção agrupada
-- gera uma produção filha nova, tipo_origem = DESAGRUPAMENTO (inverso de AGRUPAMENTO, mesmo
-- padrão já usado por DIVISAO). CHECK original (V17) só permitia DIVISAO/AGRUPAMENTO.
ALTER TABLE producoes DROP CONSTRAINT producoes_tipo_origem_check;

ALTER TABLE producoes
    ADD CONSTRAINT producoes_tipo_origem_check
        CHECK (tipo_origem IN ('DIVISAO', 'AGRUPAMENTO', 'DESAGRUPAMENTO'));
