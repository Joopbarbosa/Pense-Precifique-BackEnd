-- #210+231+234 — elimina TipoProduto.PRODUTO_BASE, unifica modelo de preço (preco_venda passa
-- a ser obrigatório para PRODUTO também, não só CUSTOMIZACAO).
--
-- Passo 0 (investigação prévia) encontrou 505 produtos tipo PRODUTO (de 540) com preco_venda NULL
-- hoje, além dos 6 PRODUTO_BASE previstos originalmente na spec — escopo do backfill ampliado para
-- cobrir os 511, decisão confirmada com o usuário em 2026-08-07: sem isso, a constraint nova do
-- Passo B quebraria a migration na hora de aplicar.

-- Passo A1: reclassifica PRODUTO_BASE -> PRODUTO
UPDATE produtos SET tipo = 'PRODUTO' WHERE tipo = 'PRODUTO_BASE';

-- Passo A2: backfill de preco_venda para todo produto tipo PRODUTO ainda sem preço de venda
-- (inclui os ex-PRODUTO_BASE reclassificados acima). Fórmula igual a PDT-005 (preco_sugerido =
-- custo_unitario x (1 + margem_lucro/100)), usando preco_custo como custo_unitario persistido
-- (mesma fonte já usada em RN-054/calcularPrecoSugeridoAvulso) e, quando margem_lucro é nulo, a
-- margem padrão do usuário como fallback (RN-NOVA-2) — sem config de precificação, cai para 0.
WITH margem_resolvida AS (
    SELECT p.id, COALESCE(p.margem_lucro, cp.margem_padrao, 0) AS margem
    FROM produtos p
    LEFT JOIN configuracoes_precificacao cp ON cp.usuario_id = p.usuario_id
    WHERE p.tipo = 'PRODUTO' AND p.preco_venda IS NULL
)
UPDATE produtos p
SET margem_lucro = mr.margem,
    preco_venda = ROUND(p.preco_custo * (1 + mr.margem / 100), 2),
    override = false
FROM margem_resolvida mr
WHERE p.id = mr.id;

-- Passo B: schema — preco_venda obrigatório para qualquer tipo de produto, e só restam PRODUTO/CUSTOMIZACAO
ALTER TABLE produtos DROP CONSTRAINT chk_preco_venda_tipo;
ALTER TABLE produtos ADD CONSTRAINT chk_preco_venda_tipo CHECK (preco_venda IS NOT NULL);

ALTER TABLE produtos DROP CONSTRAINT chk_produto_tipo;
ALTER TABLE produtos ADD CONSTRAINT chk_produto_tipo CHECK (tipo::text = ANY (ARRAY['PRODUTO', 'CUSTOMIZACAO']::text[]));
