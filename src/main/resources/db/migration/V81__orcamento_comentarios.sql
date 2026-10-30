-- V0.18.0 (#1038, DT-NOVA-6, RN-NOVA-22): conversa de comentários por orçamento. Imutável por construção: a tabela não tem
-- coluna de edição nem de exclusão. O texto é aparado antes de gravar e vai de 1 a 500 caracteres (regra global de texto livre).
CREATE TABLE orcamento_comentarios (
  id           UUID PRIMARY KEY,
  orcamento_id UUID NOT NULL REFERENCES orcamentos(id),
  usuario_id   UUID NOT NULL REFERENCES usuarios(id),
  texto        VARCHAR(500) NOT NULL,
  created_at   TIMESTAMP NOT NULL,
  CONSTRAINT chk_orcamento_comentarios_texto CHECK (char_length(btrim(texto)) BETWEEN 1 AND 500)
);
CREATE INDEX idx_orcamento_comentarios_orcamento ON orcamento_comentarios (orcamento_id, created_at DESC);
