-- V0.18.0 (#1039, DT-NOVA-5, RN-NOVA-19 a RN-NOVA-21): compromisso criado pela usuária (o mestre da série) e as exceções
-- por ocorrência. As ocorrências NÃO são gravadas: a Agenda as calcula a partir do mestre; só a que fugiu do padrão
-- (concluída, cancelada ou movida/editada só nela) vira exceção.
CREATE TABLE compromissos_usuaria (
  id                     UUID PRIMARY KEY,
  usuario_id             UUID NOT NULL REFERENCES usuarios(id),
  titulo                 VARCHAR(120) NOT NULL,
  data_inicio            DATE NOT NULL,
  horario                TIME,
  orcamento_id           UUID REFERENCES orcamentos(id),
  insumo_id              UUID REFERENCES insumos(id),
  recorrencia            VARCHAR(10) NOT NULL DEFAULT 'NENHUMA',
  dias_semana            VARCHAR(60),
  quantidade_ocorrencias INTEGER,
  situacao               VARCHAR(10) NOT NULL DEFAULT 'PENDENTE',
  observacao_cancelamento VARCHAR(500),
  created_at             TIMESTAMP NOT NULL,
  updated_at             TIMESTAMP NOT NULL,
  CONSTRAINT chk_compromissos_usuaria_recorrencia CHECK (recorrencia IN ('NENHUMA', 'SEMANAL', 'MENSAL')),
  CONSTRAINT chk_compromissos_usuaria_situacao CHECK (situacao IN ('PENDENTE', 'CONCLUIDO', 'CANCELADO')),
  CONSTRAINT chk_compromissos_usuaria_ocorrencias CHECK (quantidade_ocorrencias IS NULL OR quantidade_ocorrencias BETWEEN 2 AND 365)
);
CREATE INDEX idx_compromissos_usuaria_usuario ON compromissos_usuaria (usuario_id, data_inicio);

CREATE TABLE compromissos_usuaria_excecoes (
  id                  UUID PRIMARY KEY,
  compromisso_id      UUID NOT NULL REFERENCES compromissos_usuaria(id),
  data_original       DATE NOT NULL,
  situacao            VARCHAR(10) NOT NULL DEFAULT 'PENDENTE',
  nova_data           DATE,
  titulo              VARCHAR(120),
  horario             TIME,
  horario_proprio     BOOLEAN NOT NULL DEFAULT FALSE,
  orcamento_id        UUID REFERENCES orcamentos(id),
  orcamento_situacao  VARCHAR(10) NOT NULL DEFAULT 'HERDA',
  insumo_id           UUID REFERENCES insumos(id),
  insumo_situacao     VARCHAR(10) NOT NULL DEFAULT 'HERDA',
  observacao_cancelamento VARCHAR(500),
  created_at          TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP NOT NULL,
  CONSTRAINT uq_compromissos_usuaria_excecoes UNIQUE (compromisso_id, data_original),
  CONSTRAINT chk_excecoes_situacao CHECK (situacao IN ('PENDENTE', 'CONCLUIDO', 'CANCELADO')),
  CONSTRAINT chk_excecoes_orcamento_situacao CHECK (orcamento_situacao IN ('HERDA', 'DEFINIDO', 'REMOVIDO')),
  CONSTRAINT chk_excecoes_insumo_situacao CHECK (insumo_situacao IN ('HERDA', 'DEFINIDO', 'REMOVIDO'))
);
CREATE INDEX idx_excecoes_nova_data ON compromissos_usuaria_excecoes (nova_data);
