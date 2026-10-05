-- V0.16.0 (#687, RN-NOVA-18, DT-NOVA-12) — insumo em rascunho, criado a partir do item da nota.
-- Rascunho dispensa unidade até ser completado; o custo continua NOT NULL e vale 0 quando a nota não
-- permitiu propor custo ("sem custo"). Rascunho fica sempre ativo (não pode ser inativado).
ALTER TABLE insumos ADD COLUMN rascunho BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE insumos ALTER COLUMN unidade_medida_id DROP NOT NULL;
ALTER TABLE insumos ADD CONSTRAINT ck_insumos_completo_tem_unidade CHECK (rascunho OR unidade_medida_id IS NOT NULL);
ALTER TABLE insumos ADD CONSTRAINT ck_insumos_rascunho_ativo CHECK (NOT rascunho OR ativo);
