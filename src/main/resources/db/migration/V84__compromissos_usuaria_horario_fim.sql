-- V0.18.0 (#1063, RN-NOVA-2 v4): hora de término opcional do compromisso da usuária (duração do bloco na Agenda).
-- Na exceção por ocorrência o término acompanha o horário próprio (horario_proprio): início e término valem juntos.
ALTER TABLE compromissos_usuaria ADD COLUMN horario_fim TIME;
ALTER TABLE compromissos_usuaria_excecoes ADD COLUMN horario_fim TIME;
ALTER TABLE compromissos_usuaria ADD CONSTRAINT chk_compromissos_usuaria_fim CHECK (horario_fim IS NULL OR (horario IS NOT NULL AND horario_fim > horario));
