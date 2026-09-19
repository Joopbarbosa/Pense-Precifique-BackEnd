-- Reversão de #489 (V0.12.0) — decisão do usuário de não seguir com campos fiscais de produto
-- nesta versão (custo/benefício não justificava agora). Remove por completo o que V41 criou,
-- inclusive codigo_barras (a busca por código de barras do Caixa, #487, volta a ser só por nome —
-- ver reversão em ProdutoRepository). Sem restore de backup: apenas apaga o que foi criado.
ALTER TABLE produtos
  DROP CONSTRAINT IF EXISTS chk_produto_codigo_barras,
  DROP CONSTRAINT IF EXISTS chk_produto_ncm,
  DROP CONSTRAINT IF EXISTS chk_produto_cfop,
  DROP CONSTRAINT IF EXISTS chk_produto_csosn;

ALTER TABLE produtos
  DROP COLUMN IF EXISTS codigo_barras,
  DROP COLUMN IF EXISTS ncm,
  DROP COLUMN IF EXISTS cfop,
  DROP COLUMN IF EXISTS cest,
  DROP COLUMN IF EXISTS unidade_comercial,
  DROP COLUMN IF EXISTS csosn;
