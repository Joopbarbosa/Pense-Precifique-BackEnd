-- V0.16.0 (#714) — fim do insumo em rascunho: o cadastro pela conciliação da nota passa a ser completo.
-- A V69 (#687) é desfeita: sai a coluna `rascunho`, voltam a unidade obrigatória e os CHECKs somem.
-- Versão ainda não publicada: não há rascunho em produção; em bancos de desenvolvimento, quem ficou sem
-- unidade recebe a primeira unidade da conta antes de a coluna voltar a ser obrigatória.
ALTER TABLE insumos DROP CONSTRAINT IF EXISTS ck_insumos_rascunho_ativo;
ALTER TABLE insumos DROP CONSTRAINT IF EXISTS ck_insumos_completo_tem_unidade;
UPDATE insumos i
   SET unidade_medida_id = (SELECT u.id FROM unidades_medida u
                             WHERE u.usuario_id = i.usuario_id AND u.deleted_at IS NULL
                             ORDER BY u.created_at LIMIT 1)
 WHERE i.unidade_medida_id IS NULL;
ALTER TABLE insumos ALTER COLUMN unidade_medida_id SET NOT NULL;
ALTER TABLE insumos DROP COLUMN rascunho;
