-- #320 (P-B014, V0.8.2) — remove dados de teste curl das sessões de validação de PDC-029/RN-NOVA-6
-- (bloqueio antigo de vínculo Orçamento↔Produção), cliente "Cliente Vincular Produção".
-- Executado uma vez em 2026-08-25. Contagem confirmada antes: 14 clientes, 14 orçamentos,
-- 11 produções (todas AGUARDANDO_INICIO), 14 orcamento_itens, 11 orcamento_producoes.
-- Script mantido só como registro de auditoria — não é migration, não roda automaticamente.

BEGIN;

CREATE TEMP TABLE tmp_clientes AS
  SELECT id FROM clientes WHERE nome ILIKE '%Vincular Produção%';

CREATE TEMP TABLE tmp_orcamentos AS
  SELECT o.id FROM orcamentos o WHERE o.cliente_id IN (SELECT id FROM tmp_clientes);

CREATE TEMP TABLE tmp_producoes AS
  SELECT DISTINCT p.id FROM producoes p
  JOIN orcamento_producoes op ON op.producao_id = p.id
  WHERE op.orcamento_id IN (SELECT id FROM tmp_orcamentos);

DELETE FROM orcamento_producoes WHERE orcamento_id IN (SELECT id FROM tmp_orcamentos);
DELETE FROM orcamento_itens WHERE orcamento_id IN (SELECT id FROM tmp_orcamentos);
DELETE FROM orcamentos WHERE id IN (SELECT id FROM tmp_orcamentos);
DELETE FROM producoes WHERE id IN (SELECT id FROM tmp_producoes);
DELETE FROM clientes WHERE id IN (SELECT id FROM tmp_clientes);

COMMIT;
