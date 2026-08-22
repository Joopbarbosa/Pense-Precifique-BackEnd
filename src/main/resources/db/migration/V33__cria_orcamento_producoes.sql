CREATE TABLE orcamento_producoes (
    id           UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    orcamento_id UUID    NOT NULL REFERENCES orcamentos(id),
    producao_id  UUID    NOT NULL REFERENCES producoes(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_orcamento_producao UNIQUE (orcamento_id, producao_id)
);

CREATE INDEX idx_orcamento_producoes_orcamento ON orcamento_producoes(orcamento_id);
CREATE INDEX idx_orcamento_producoes_producao  ON orcamento_producoes(producao_id);
