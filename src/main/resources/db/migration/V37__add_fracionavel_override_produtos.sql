-- RN-NOVA-2 (V0.10.0, #299, altera PDT-016) — campo fracionável de Produto passa a ser
-- persistido e editável (padrão calculado+override, mesmo modelo já usado por preco_venda/override).
-- fracionavel: valor atual exibido (deriva automaticamente da ficha técnica enquanto não há
-- override; fica congelado quando a artesã edita manualmente).
-- fracionavel_override: true quando a artesã já editou manualmente — trava o recálculo automático.
ALTER TABLE produtos
    ADD COLUMN fracionavel BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN fracionavel_override BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: produtos existentes recebem o valor derivado da ficha técnica no momento da migration
-- (nenhum insumo não-fracionável na ficha → fracionavel = true; ao menos um → false). Sem override
-- (fracionavel_override permanece false) — continuam recalculando ao vivo até a 1ª edição manual.
UPDATE produtos p
SET fracionavel = NOT EXISTS (
    SELECT 1
    FROM ficha_tecnica_itens fti
    JOIN insumos i ON i.id = fti.insumo_id
    WHERE fti.produto_id = p.id
      AND fti.insumo_id IS NOT NULL
      AND i.fracionavel = FALSE
);
