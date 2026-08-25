-- RN-PROD-HIST-01 (V0.8.2, #320) — HistoricoStatusProducao passa a registrar 2 tipos de evento:
-- STATUS (comportamento atual, inalterado) e ITEM_ADICIONADO (produtos entrando via vínculo de
-- orçamento). Nome da tabela/entidade mantido (decisão travada — ver decisoes-producao.md).

ALTER TABLE historico_status_producao
    ADD COLUMN tipo_evento VARCHAR(20) NOT NULL DEFAULT 'STATUS'
        CHECK (tipo_evento IN ('STATUS', 'ITEM_ADICIONADO'));

ALTER TABLE historico_status_producao
    ALTER COLUMN tipo_evento DROP DEFAULT;

ALTER TABLE historico_status_producao
    ADD COLUMN produto_id UUID REFERENCES produtos(id);

ALTER TABLE historico_status_producao
    ADD COLUMN quantidade NUMERIC;

ALTER TABLE historico_status_producao
    ADD COLUMN referencia_orcamento_id UUID REFERENCES orcamentos(id);

-- status_novo deixa de ser obrigatório — linhas ITEM_ADICIONADO não têm transição de status.
ALTER TABLE historico_status_producao
    ALTER COLUMN status_novo DROP NOT NULL;

ALTER TABLE historico_status_producao
    ADD CONSTRAINT chk_historico_producao_status_completo
        CHECK (tipo_evento <> 'STATUS' OR status_novo IS NOT NULL);

ALTER TABLE historico_status_producao
    ADD CONSTRAINT chk_historico_producao_item_completo
        CHECK (tipo_evento <> 'ITEM_ADICIONADO'
            OR (produto_id IS NOT NULL AND quantidade IS NOT NULL AND referencia_orcamento_id IS NOT NULL));

CREATE INDEX idx_historico_status_producao_referencia_orcamento
    ON historico_status_producao(referencia_orcamento_id);
