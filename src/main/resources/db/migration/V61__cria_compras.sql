-- V0.15.0 — módulo Compras (#541, #540, #550; DT-NOVA-3, DT-NOVA-6, DT-NOVA-15).

-- Compra: RASCUNHO → CONFIRMADA → CANCELADA. COM-N (RN-053) nasce no primeiro salvar; excluir
-- rascunho é soft delete (deleted_at) para o MAX(numero)+1 nunca reutilizar um número.
CREATE TABLE compras (
  id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id                UUID NOT NULL,
  numero                    INTEGER NOT NULL,
  status                    VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
  data_compra               DATE NOT NULL,
  multiplos_fornecedores    BOOLEAN NOT NULL DEFAULT FALSE,
  fornecedor_id             UUID,
  pago                      BOOLEAN NOT NULL DEFAULT FALSE,
  metodo_pagamento_id       UUID,
  observacoes               TEXT,
  -- Preparado para NFC-e/NF-e (#561/#562); por enquanto só MANUAL.
  origem                    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  confirmada_em             TIMESTAMP,
  cancelada_em              TIMESTAMP,
  observacao_cancelamento   TEXT,
  deleted_at                TIMESTAMP,
  created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at                TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_compra_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_compra_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES clientes(id),
  CONSTRAINT fk_compra_metodo_pagamento FOREIGN KEY (metodo_pagamento_id) REFERENCES metodos_pagamento(id),
  CONSTRAINT uq_compra_usuario_numero UNIQUE (usuario_id, numero),
  CONSTRAINT chk_compra_status CHECK (status IN ('RASCUNHO', 'CONFIRMADA', 'CANCELADA')),
  CONSTRAINT chk_compra_origem CHECK (origem IN ('MANUAL')),
  -- RN-NOVA-23: Pago exige método; Não pago nunca tem método.
  CONSTRAINT chk_compra_pagamento CHECK (
    (pago AND metodo_pagamento_id IS NOT NULL) OR (NOT pago AND metodo_pagamento_id IS NULL)
  )
);

CREATE INDEX idx_compras_usuario_status ON compras (usuario_id, status);
CREATE INDEX idx_compras_fornecedor_id ON compras (fornecedor_id);

-- Linha da compra. fornecedor_id sempre preenchido por linha quando há fornecedor (no modo
-- fornecedor único, copia o do cabeçalho). Quantidade e preço podem faltar no rascunho (lista de
-- compras gera rascunho sem preço); a confirmação exige os dois. preco_unitario_pago e
-- custo_unitario_anterior/posterior são gravados na confirmação (mesma escala de insumos.custo_unitario).
CREATE TABLE compra_itens (
  id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  compra_id                   UUID NOT NULL,
  insumo_id                   UUID NOT NULL,
  fornecedor_id               UUID,
  quantidade                  DECIMAL(15,4),
  preco_total                 DECIMAL(15,2),
  preco_unitario_pago         DECIMAL(15,4),
  custo_unitario_anterior     DECIMAL(15,4),
  custo_unitario_posterior    DECIMAL(15,4),
  ordem                       INTEGER NOT NULL,
  CONSTRAINT fk_compra_item_compra FOREIGN KEY (compra_id) REFERENCES compras(id),
  CONSTRAINT fk_compra_item_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_compra_item_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES clientes(id),
  CONSTRAINT chk_compra_item_quantidade CHECK (quantidade IS NULL OR quantidade > 0),
  CONSTRAINT chk_compra_item_preco CHECK (preco_total IS NULL OR preco_total > 0)
);

CREATE INDEX idx_compra_itens_compra_id ON compra_itens (compra_id);
CREATE INDEX idx_compra_itens_insumo_id ON compra_itens (insumo_id);
CREATE INDEX idx_compra_itens_fornecedor_id ON compra_itens (fornecedor_id);

-- #540/RN-NOVA-6 — "o que o fornecedor vende e por quanto". Um par por fornecedor+insumo.
-- (A conciliação futura de itens de nota, #561/#562, será outra tabela.)
CREATE TABLE fornecedor_insumo (
  id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id         UUID NOT NULL,
  fornecedor_id      UUID NOT NULL,
  insumo_id          UUID NOT NULL,
  preco_referencia   DECIMAL(15,4),
  created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_fornecedor_insumo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_fornecedor_insumo_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES clientes(id),
  CONSTRAINT fk_fornecedor_insumo_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT uq_fornecedor_insumo UNIQUE (fornecedor_id, insumo_id),
  CONSTRAINT chk_fornecedor_insumo_preco CHECK (preco_referencia IS NULL OR preco_referencia > 0)
);

CREATE INDEX idx_fornecedor_insumo_insumo_id ON fornecedor_insumo (insumo_id);
CREATE INDEX idx_fornecedor_insumo_usuario_id ON fornecedor_insumo (usuario_id);
