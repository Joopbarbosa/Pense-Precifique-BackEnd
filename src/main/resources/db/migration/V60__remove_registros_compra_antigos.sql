-- V0.15.0 — #541/RN-NOVA-21 (DT-NOVA-5, Decisão 15). MIGRAÇÃO DESTRUTIVA E IRREVERSÍVEL.
--
-- Remove todos os registros de compra anteriores ao módulo Compras: as movimentações de insumo com
-- motivo COMPRA (do modal carrinho e as geradas pelo cadastro de insumo antes de #442/V0.10.0) e a
-- tabela lotes_compra. Nenhuma compra antiga vira COM-N. insumos.estoque_atual e
-- insumos.custo_unitario NÃO são tocados (o histórico deixa de fechar com o estoque atual —
-- consequência aceita pelo usuário).
--
-- Operacional: backup do banco (pg_dump) OBRIGATÓRIO imediatamente antes do deploy da V0.15.0
-- (checklist em DEPLOY.md). Migração isolada de propósito: só esta remoção, nada mais.
DELETE FROM movimentacoes_insumo WHERE motivo = 'COMPRA';

DROP TABLE lotes_compra;
