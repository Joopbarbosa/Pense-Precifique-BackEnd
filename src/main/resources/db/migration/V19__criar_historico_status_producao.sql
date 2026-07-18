CREATE TABLE historico_status_producao (
    id              UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    producao_id     UUID      NOT NULL REFERENCES producoes(id),
    status_anterior VARCHAR(30)
        CHECK (status_anterior IN ('AGUARDANDO_INICIO','EM_ANDAMENTO','TRAVADA','FINALIZADA','CANCELADA','NAO_REALIZADA')),
    status_novo     VARCHAR(30) NOT NULL
        CHECK (status_novo IN ('AGUARDANDO_INICIO','EM_ANDAMENTO','TRAVADA','FINALIZADA','CANCELADA','NAO_REALIZADA')),
    data_transicao  TIMESTAMP NOT NULL DEFAULT NOW(),
    justificativa   TEXT,
    origem          VARCHAR(10) NOT NULL CHECK (origem IN ('SISTEMA','USUARIO'))
);

CREATE INDEX idx_historico_status_producao ON historico_status_producao(producao_id);
