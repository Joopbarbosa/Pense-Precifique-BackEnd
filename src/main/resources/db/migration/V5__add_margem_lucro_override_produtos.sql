-- V5: Suporte a preco_sugerido com override para produtos CUSTOMIZACAO (RN-038a)
-- margem_lucro: pre-preenchida com margem_padrao na criacao, depois campo proprio (nao acompanha mudancas futuras em margem_padrao)
-- override: true quando a artesa edita preco_venda manualmente; preco_venda fica fixo, nao acompanha mudancas de margem_lucro
-- preco_sugerido nao ganha coluna: e sempre calculado (custo_unitario x (1 + margem_lucro/100)), nunca persistido

ALTER TABLE produtos ADD COLUMN margem_lucro DECIMAL(5,2);
ALTER TABLE produtos ADD COLUMN override BOOLEAN NOT NULL DEFAULT FALSE;
