-- V0.18.0 (#1159): descrição opcional (até 500 caracteres) do compromisso da usuária, gravada no mestre da série.
ALTER TABLE compromissos_usuaria ADD COLUMN descricao VARCHAR(500);
