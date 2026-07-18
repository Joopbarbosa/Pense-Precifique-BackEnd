-- Campo status legado (ATIVA/CANCELADA) permanece intacto — ainda usado por ProducaoService.
-- Novo campo "estado" traz o ciclo de vida completo (V0.6), mapeado no enum EstadoProducao.
ALTER TABLE producoes
    ADD COLUMN estado VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_INICIO'
        CHECK (estado IN ('AGUARDANDO_INICIO','EM_ANDAMENTO','TRAVADA','FINALIZADA','CANCELADA','NAO_REALIZADA'));

ALTER TABLE producoes
    ADD COLUMN data_inicio                 DATE,
    ADD COLUMN data_termino_prevista       DATE,
    ADD COLUMN data_termino_real           DATE,
    ADD COLUMN observacoes                 TEXT,
    ADD COLUMN justificativa_cancelamento  TEXT,
    ADD COLUMN justificativa_nao_realizada TEXT,
    ADD COLUMN producao_origem_id          UUID REFERENCES producoes(id),
    ADD COLUMN tipo_origem                 VARCHAR(20)
        CHECK (tipo_origem IN ('DIVISAO','AGRUPAMENTO'));

CREATE INDEX idx_producoes_origem ON producoes(producao_origem_id)
    WHERE producao_origem_id IS NOT NULL;

CREATE INDEX idx_producoes_estado ON producoes(estado);
