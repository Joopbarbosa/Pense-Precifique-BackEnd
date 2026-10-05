-- V0.16.0 (#681, RN-NOVA-12 a 15, DT-NOVA-11) — vínculo item da nota → insumo, por usuária e CNPJ do
-- emitente (a compra pode seguir sem fornecedor cadastrado, RN-NOVA-10). Nome do item normalizado
-- (sem acento, sem maiúscula, espaços únicos) é a chave; o original fica para exibição.
CREATE TABLE vinculos_item_nota (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id            UUID NOT NULL,
  emitente_cnpj         VARCHAR(14) NOT NULL,
  emitente_nome         VARCHAR(500),
  nome_item             VARCHAR(500) NOT NULL,
  nome_item_normalizado VARCHAR(500) NOT NULL,
  insumo_id             UUID,
  ignorar               BOOLEAN NOT NULL DEFAULT FALSE,
  fator                 DECIMAL(15,4),
  origem                VARCHAR(20) NOT NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_vinculos_item_nota_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_vinculos_item_nota_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT uq_vinculos_item_nota UNIQUE (usuario_id, emitente_cnpj, nome_item_normalizado),
  CONSTRAINT chk_vinculos_item_nota_cnpj CHECK (emitente_cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$'),
  CONSTRAINT chk_vinculos_item_nota_origem CHECK (origem IN ('CASAMENTO_NOME', 'SUGESTAO_IA', 'MANUAL')),
  CONSTRAINT chk_vinculos_item_nota_destino CHECK (
    (ignorar AND insumo_id IS NULL AND fator IS NULL) OR (NOT ignorar AND insumo_id IS NOT NULL AND fator > 0))
);
CREATE INDEX idx_vinculos_item_nota_insumo_id ON vinculos_item_nota (insumo_id);

-- Limite técnico de sugestões de insumo por IA (200 por conta por mês, SUGESTAO_IA_LIMITE_MENSAL_POR_CONTA),
-- fora do limite de leituras do leitor-fiscal. Um registro por usuária e mês (primeiro dia do mês).
CREATE TABLE sugestoes_ia_uso_mensal (
  usuario_id  UUID NOT NULL,
  mes         DATE NOT NULL,
  quantidade  INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT pk_sugestoes_ia_uso_mensal PRIMARY KEY (usuario_id, mes),
  CONSTRAINT fk_sugestoes_ia_uso_mensal_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT chk_sugestoes_ia_uso_mensal_quantidade CHECK (quantidade >= 0)
);
