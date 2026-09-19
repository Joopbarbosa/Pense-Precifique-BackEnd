-- #488 (retrabalho do teste manual, V0.12.0) — horario de funcionamento por dia da semana.
-- Tabela filha, nao 14 colunas em `empresas`: EmpresaResponseDTO alimenta PdfMapper/
-- PdfMicroservicoEmpresaPayload, e uma lista aninhada mantem esse payload limpo.
-- Abrir o caixa fora da faixa configurada AVISA, nunca bloqueia (decisao do usuario).
CREATE TABLE empresa_horarios (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  empresa_id      UUID NOT NULL,
  -- 1=segunda ... 7=domingo, igual a java.time.DayOfWeek.getValue() (ISO-8601)
  dia_semana      SMALLINT NOT NULL,
  fechado         BOOLEAN NOT NULL DEFAULT FALSE,
  hora_abertura   TIME,
  hora_fechamento TIME,
  CONSTRAINT fk_empresa_horario_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
  CONSTRAINT chk_empresa_horario_dia CHECK (dia_semana BETWEEN 1 AND 7),
  -- Dia aberto exige os dois horarios; dia fechado dispensa ambos.
  CONSTRAINT chk_empresa_horario_preenchido CHECK (
    fechado OR (hora_abertura IS NOT NULL AND hora_fechamento IS NOT NULL)
  ),
  CONSTRAINT chk_empresa_horario_ordem CHECK (
    fechado OR hora_fechamento > hora_abertura
  )
);

CREATE UNIQUE INDEX uq_empresa_horarios_empresa_dia ON empresa_horarios(empresa_id, dia_semana);
CREATE INDEX idx_empresa_horarios_empresa_id ON empresa_horarios(empresa_id);
