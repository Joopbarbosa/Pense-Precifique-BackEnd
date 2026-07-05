-- V4: Ajusta tabela produtos para o bloco Catalogo (RN-038)
-- rendimento: novo campo, obrigatoriedade condicional (com ficha tecnica) validada no Service, nao aqui
-- foto: removida do cadastro (Epico 4)
-- preco_venda: passa a ser exigido apenas para tipo CUSTOMIZACAO (antes: qualquer tipo != PRODUTO_BASE)
-- tempo_producao: passa a representar o tempo do lote inteiro, nao mais da unidade

ALTER TABLE produtos ADD COLUMN rendimento DECIMAL(10,4);

ALTER TABLE produtos DROP COLUMN foto;

ALTER TABLE produtos DROP CONSTRAINT chk_preco_venda_tipo;
ALTER TABLE produtos ADD CONSTRAINT chk_preco_venda_tipo
  CHECK (tipo <> 'CUSTOMIZACAO' OR preco_venda IS NOT NULL);

COMMENT ON COLUMN produtos.tempo_producao IS 'Tempo de producao do lote inteiro (nao da unidade) — RN-038';
