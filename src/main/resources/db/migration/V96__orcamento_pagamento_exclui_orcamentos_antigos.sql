-- V0.19.0 (#1185 / PP-528, DT-NOVA-8, RN-NOVA-37): o orçamento passa a nascer SEM forma de pagamento (a forma é escolhida
-- no passo Pagamento da aprovação, entre os métodos configuráveis, e gravada pela #1174). Decisão do Gestor (29/10):
-- os orçamentos criados antes da versão são EXCLUÍDOS, junto com os registros que dependem deles.
--
-- DESTRUTIVA. Só pode rodar depois de ensaio em cópia do banco, backup completo e confirmação registrada do Gestor no dia
-- da publicação (PLANO_EXECUCAO_V0.19.0). Numerada V96 (e não V86+) para rodar DEPOIS das migrações das outras tarefas
-- da versão (V92 de PP-521 e PP-525), pois a #1174 e a #1173 criam lançamentos de origem Orçamento/Compra.
--
-- NUNCA exclui produções: só remove os vínculos em orcamento_producoes e as linhas de histórico que apontam para o
-- orçamento. Compromissos da Agenda são da usuária: perdem só o vínculo com o orçamento.

-- 1) Lançamentos financeiros de origem Orçamento e suas parcelas (V91).
DELETE FROM parcelas_financeiras
 WHERE lancamento_id IN (SELECT id FROM lancamentos_financeiros WHERE orcamento_id IS NOT NULL);
DELETE FROM lancamentos_financeiros WHERE orcamento_id IS NOT NULL;

-- 2) Agenda: mantém o compromisso, solta o vínculo.
UPDATE compromissos_usuaria_excecoes SET orcamento_id = NULL WHERE orcamento_id IS NOT NULL;
UPDATE compromissos_usuaria SET orcamento_id = NULL WHERE orcamento_id IS NOT NULL;

-- 3) Produção: remove os vínculos e o histórico de itens que referencia o orçamento; as produções ficam.
DELETE FROM orcamento_producoes;
DELETE FROM historico_status_producao WHERE referencia_orcamento_id IS NOT NULL;

-- 4) Dependentes diretos do orçamento.
DELETE FROM orcamento_item_componentes;
DELETE FROM orcamento_item_customizacoes;
DELETE FROM orcamento_itens;
DELETE FROM orcamento_comentarios;
DELETE FROM recibos_pagamento;
DELETE FROM recibos_estorno;

-- 5) Os orçamentos.
DELETE FROM orcamentos;

-- 6) A forma de pagamento do orçamento deixa de ser o enum fixo e passa a apontar para o método configurável (DT-NOVA-8),
--    NULA até o passo Pagamento da aprovação (RN-NOVA-37). O mesmo vale para o método do sinal recebido, que a #1174
--    passa a gravar junto da parcela. As colunas de observação do enum antigo (texto livre do "Outro") saem.
ALTER TABLE orcamentos DROP COLUMN metodo_pagamento;
ALTER TABLE orcamentos DROP COLUMN metodo_pagamento_obs;
ALTER TABLE orcamentos DROP COLUMN metodo_sinal_recebido;
ALTER TABLE orcamentos DROP COLUMN metodo_sinal_recebido_obs;
ALTER TABLE orcamentos ADD COLUMN metodo_pagamento_id UUID REFERENCES metodos_pagamento(id);
ALTER TABLE orcamentos ADD COLUMN metodo_sinal_recebido_id UUID REFERENCES metodos_pagamento(id);
CREATE INDEX idx_orcamentos_metodo_pagamento ON orcamentos (metodo_pagamento_id) WHERE metodo_pagamento_id IS NOT NULL;
CREATE INDEX idx_orcamentos_metodo_sinal_recebido ON orcamentos (metodo_sinal_recebido_id) WHERE metodo_sinal_recebido_id IS NOT NULL;
