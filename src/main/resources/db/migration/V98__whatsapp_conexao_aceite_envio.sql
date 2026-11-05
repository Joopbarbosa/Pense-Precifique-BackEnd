-- #1170 / DT-NOVA-13 e DT-NOVA-14 (V0.19.0) — envio de PDF por WhatsApp (Evolution API).
-- Uma conexão por usuária; o QR nunca é gravado. O histórico de envios NÃO guarda conteúdo (só número,
-- tipo e id do documento, data, resultado e, para número digitado, a confirmação de consentimento).

CREATE TABLE whatsapp_conexoes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios(id),
    instancia VARCHAR(60) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('NAO_CONECTADO', 'AGUARDANDO_QR', 'CONECTADO', 'DESCONECTADO')),
    estado_alterado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE whatsapp_aceites_risco (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    aceito_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao_texto VARCHAR(20) NOT NULL
);
CREATE INDEX idx_whatsapp_aceite_usuario ON whatsapp_aceites_risco(usuario_id, aceito_em DESC);

CREATE TABLE whatsapp_envios (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    numero VARCHAR(15),
    tipo_documento VARCHAR(30) NOT NULL,
    documento_id UUID NOT NULL,
    enviado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    resultado VARCHAR(10) NOT NULL CHECK (resultado IN ('ENVIADO', 'FALHOU', 'BLOQUEADO')),
    motivo VARCHAR(40),
    consentimento_confirmado BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_whatsapp_envio_usuario_data ON whatsapp_envios(usuario_id, enviado_em DESC);
