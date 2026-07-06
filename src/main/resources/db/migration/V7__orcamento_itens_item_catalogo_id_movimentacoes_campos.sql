-- P-015: migration orcamento_itens → item_catalogo_id + movimentacoes_produto campos
-- Remove produto_id de orcamento_itens, adiciona item_catalogo_id
-- Adiciona catalogo_referencia e preco_vendido em movimentacoes_produto
-- Reset de banco é aceitável (projeto pré-produção sem usuários reais)

-- ===== Reset dados de teste =====
-- Limpar dados de orcamento_itens e tabelas relacionadas (reset aceitável per PRD)
-- Ordem: dependents primeiro, depois tabela principal
DELETE FROM orcamento_item_customizacoes;
DELETE FROM recibos_pagamento;
DELETE FROM orcamento_itens;
DELETE FROM movimentacoes_produto;
DELETE FROM orcamentos;

-- ===== orcamento_itens =====
-- Remover FK para produtos
ALTER TABLE orcamento_itens
DROP CONSTRAINT fk_item_produto;

-- Remover coluna produto_id
ALTER TABLE orcamento_itens
DROP COLUMN produto_id;

-- Adicionar coluna item_catalogo_id com FK para itens_catalogo
ALTER TABLE orcamento_itens
ADD COLUMN item_catalogo_id UUID NOT NULL;

ALTER TABLE orcamento_itens
ADD CONSTRAINT fk_item_catalogo
FOREIGN KEY (item_catalogo_id) REFERENCES itens_catalogo(id);

-- Criar índice para fk_item_catalogo
CREATE INDEX idx_orcamento_itens_item_catalogo_id
ON orcamento_itens(item_catalogo_id);

-- ===== movimentacoes_produto =====
-- Adicionar coluna catalogo_referencia (nullable, preenchida apenas quando motivo = 'ORCAMENTO')
ALTER TABLE movimentacoes_produto
ADD COLUMN catalogo_referencia VARCHAR(255);

-- Adicionar coluna preco_vendido (nullable, preenchida apenas quando motivo = 'ORCAMENTO')
ALTER TABLE movimentacoes_produto
ADD COLUMN preco_vendido NUMERIC(10,2);
