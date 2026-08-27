-- RN-ORC-VINC-03 (V0.8.2, #320) — desvincular orçamento-produção reverte as quantidades adicionadas
-- via ITEM_ADICIONADO. Cada reversão grava uma linha ITEM_REMOVIDO própria (mesmo padrão de
-- rastreabilidade de origem de RN-PROD-HIST-01/V34) — nunca apaga/edita a linha ITEM_ADICIONADO
-- original.

ALTER TABLE historico_status_producao
    DROP CONSTRAINT chk_historico_producao_item_completo;

ALTER TABLE historico_status_producao
    DROP CONSTRAINT historico_status_producao_tipo_evento_check;

ALTER TABLE historico_status_producao
    ADD CONSTRAINT historico_status_producao_tipo_evento_check
        CHECK (tipo_evento IN ('STATUS', 'ITEM_ADICIONADO', 'ITEM_REMOVIDO'));

ALTER TABLE historico_status_producao
    ADD CONSTRAINT chk_historico_producao_item_completo
        CHECK (tipo_evento NOT IN ('ITEM_ADICIONADO', 'ITEM_REMOVIDO')
            OR (produto_id IS NOT NULL AND quantidade IS NOT NULL AND referencia_orcamento_id IS NOT NULL));
