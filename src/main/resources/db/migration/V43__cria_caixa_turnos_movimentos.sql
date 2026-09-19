-- #488 — CaixaTurno/CaixaMovimento (Caixa/PDV, Epic #416). RN-NOVA-6/8/9 em
-- version/V0.12.0/DECISOES_V0.12.0.md. FK real para usuario/turno (DT-NOVA-2, nao referencia
-- solta) — nenhum dos dois e ponteiro polimorfico.
CREATE TABLE caixa_turnos (
  id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id                  UUID NOT NULL,
  data_abertura               TIMESTAMP NOT NULL DEFAULT NOW(),
  valor_abertura              DECIMAL(15,2) NOT NULL,
  data_fechamento             TIMESTAMP,
  valor_fechamento_esperado   DECIMAL(15,2),
  valor_fechamento_informado  DECIMAL(15,2),
  diferenca                   DECIMAL(15,2),
  status                      VARCHAR(10) NOT NULL,
  CONSTRAINT fk_caixa_turno_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT chk_caixa_turno_status CHECK (status IN ('ABERTO', 'FECHADO'))
);

CREATE INDEX idx_caixa_turnos_usuario_id ON caixa_turnos(usuario_id);

-- RN-NOVA-6 — so 1 turno ABERTO por usuaria por vez, reforcado no banco (defesa em profundidade,
-- mesmo padrao de uq_empresas_usuario_id/#142: check em Service e depois constraint como rede de
-- seguranca contra corrida real).
CREATE UNIQUE INDEX uq_caixa_turnos_usuario_aberto
  ON caixa_turnos(usuario_id) WHERE status = 'ABERTO';

CREATE TABLE caixa_movimentos (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  caixa_turno_id  UUID NOT NULL,
  tipo            VARCHAR(10) NOT NULL,
  valor           DECIMAL(15,2) NOT NULL,
  motivo          TEXT NOT NULL,
  data_movimento  TIMESTAMP NOT NULL DEFAULT NOW(),
  responsavel_id  UUID NOT NULL,
  CONSTRAINT fk_caixa_movimento_turno FOREIGN KEY (caixa_turno_id) REFERENCES caixa_turnos(id),
  CONSTRAINT fk_caixa_movimento_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
  CONSTRAINT chk_caixa_movimento_tipo CHECK (tipo IN ('SANGRIA', 'SUPRIMENTO'))
  -- RN-NOVA-8 (minimo de 30 caracteres do motivo, mesmo padrao de PDT-009) validado só no DTO
  -- (@Size(min=30)), sem CHECK de banco — mesmo padrão já usado por PDT-009 hoje, sem precedente
  -- de reforço no schema para validação de tamanho de texto neste projeto.
);

CREATE INDEX idx_caixa_movimentos_turno_id ON caixa_movimentos(caixa_turno_id);
