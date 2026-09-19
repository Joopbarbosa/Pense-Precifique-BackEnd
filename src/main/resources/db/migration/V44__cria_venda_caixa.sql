-- #487 — VendaCaixa/VendaCaixaItem/VendaCaixaPagamento (Caixa/PDV, Epic #416). RN-NOVA-1 a 11 em
-- version/V0.12.0/DECISOES_V0.12.0.md. FK real para caixa_turno/metodo_pagamento (DT-NOVA-2).
CREATE TABLE venda_caixa (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id          UUID NOT NULL,
  numero              INTEGER NOT NULL,
  cliente_id          UUID,
  data_venda          TIMESTAMP NOT NULL DEFAULT NOW(),
  caixa_turno_id      UUID NOT NULL,
  status              VARCHAR(10) NOT NULL,
  subtotal            DECIMAL(15,2) NOT NULL,
  desconto_tipo       VARCHAR(10),
  desconto_valor      DECIMAL(15,2),
  total               DECIMAL(15,2) NOT NULL,
  troco               DECIMAL(15,2),
  cancelamento_motivo TEXT,
  CONSTRAINT fk_venda_caixa_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_venda_caixa_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  CONSTRAINT fk_venda_caixa_turno FOREIGN KEY (caixa_turno_id) REFERENCES caixa_turnos(id),
  CONSTRAINT chk_venda_caixa_status CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
  CONSTRAINT chk_venda_caixa_desconto_tipo CHECK (desconto_tipo IS NULL OR desconto_tipo IN ('PERCENTUAL', 'VALOR')),
  -- RN-053 — mesmo padrao ja usado por PRO-N/CLI-N/CTG-N/ORC-N/PRD-N (NumeroSequencialUtil).
  CONSTRAINT uq_venda_caixa_usuario_numero UNIQUE (usuario_id, numero)
);

CREATE INDEX idx_venda_caixa_usuario_id ON venda_caixa(usuario_id);
CREATE INDEX idx_venda_caixa_turno_id ON venda_caixa(caixa_turno_id);

CREATE TABLE venda_caixa_item (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  venda_caixa_id  UUID NOT NULL,
  produto_id      UUID NOT NULL,
  quantidade      DECIMAL(15,4) NOT NULL,
  preco_unitario  DECIMAL(15,2) NOT NULL,
  subtotal        DECIMAL(15,2) NOT NULL,
  CONSTRAINT fk_venda_caixa_item_venda FOREIGN KEY (venda_caixa_id) REFERENCES venda_caixa(id),
  CONSTRAINT fk_venda_caixa_item_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

CREATE INDEX idx_venda_caixa_item_venda_id ON venda_caixa_item(venda_caixa_id);

CREATE TABLE venda_caixa_pagamento (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  venda_caixa_id      UUID NOT NULL,
  metodo_pagamento_id UUID NOT NULL,
  valor               DECIMAL(15,2) NOT NULL,
  CONSTRAINT fk_venda_caixa_pagamento_venda FOREIGN KEY (venda_caixa_id) REFERENCES venda_caixa(id),
  CONSTRAINT fk_venda_caixa_pagamento_metodo FOREIGN KEY (metodo_pagamento_id) REFERENCES metodos_pagamento(id)
);

CREATE INDEX idx_venda_caixa_pagamento_venda_id ON venda_caixa_pagamento(venda_caixa_id);
