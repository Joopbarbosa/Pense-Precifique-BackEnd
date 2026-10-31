ALTER TABLE clientes
    ADD COLUMN whatsapp_canonico VARCHAR(15),
    ADD COLUMN aceita_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN aceita_whatsapp_em DATE,
    ADD COLUMN aceita_whatsapp_por UUID REFERENCES usuarios(id),
    ADD COLUMN recusou_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN recusou_whatsapp_em DATE,
    ADD COLUMN recusou_whatsapp_por UUID REFERENCES usuarios(id);

UPDATE clientes
SET whatsapp_canonico = CASE
    WHEN regexp_replace(COALESCE(whatsapp, telefone, ''), '[^0-9]', '', 'g') ~ '^[0-9]{10,11}$'
        THEN '55' || regexp_replace(COALESCE(whatsapp, telefone, ''), '[^0-9]', '', 'g')
    ELSE regexp_replace(COALESCE(whatsapp, telefone, ''), '[^0-9]', '', 'g')
END;

CREATE INDEX idx_cliente_usuario_whatsapp_canonico
    ON clientes(usuario_id, whatsapp_canonico)
    WHERE whatsapp_canonico IS NOT NULL;

CREATE TABLE cliente_consentimento_eventos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('ACEITE', 'RECUSA')),
    data_informada DATE NOT NULL,
    descricao VARCHAR(200),
    registrado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cliente_consentimento_descricao CHECK (
        (tipo = 'ACEITE' AND (descricao IS NULL OR length(trim(descricao)) BETWEEN 10 AND 200))
        OR (tipo = 'RECUSA' AND descricao IS NULL)
    )
);

CREATE INDEX idx_cliente_consentimento_eventos_cliente
    ON cliente_consentimento_eventos(cliente_id, registrado_em);
