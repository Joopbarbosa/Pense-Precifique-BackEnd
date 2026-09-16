-- #491 — MetodoPagamento: lista configuravel de metodos de pagamento por usuaria, consumida pelo
-- pagamento dividido de VendaCaixa (Caixa/PDV, Epic #416). RN-NOVA-15/16/17 em DECISOES_V0.12.0.md.
CREATE TABLE metodos_pagamento (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id      UUID NOT NULL,
  tipo            VARCHAR(20) NOT NULL,
  nome            VARCHAR(100),
  taxa_maquininha DECIMAL(5,2),
  ativo           BOOLEAN NOT NULL DEFAULT TRUE,
  ordem           INTEGER,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_metodo_pagamento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT chk_metodo_pagamento_tipo CHECK (
    tipo IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'OUTRO')
  ),
  CONSTRAINT chk_metodo_pagamento_nome_outro CHECK (tipo <> 'OUTRO' OR nome IS NOT NULL),
  CONSTRAINT chk_metodo_pagamento_taxa_tipo CHECK (
    taxa_maquininha IS NULL OR tipo IN ('CARTAO_CREDITO', 'CARTAO_DEBITO')
  )
);

CREATE INDEX idx_metodos_pagamento_usuario_id ON metodos_pagamento(usuario_id);

-- RN-NOVA-16 — tipos fixos nunca duplicam por usuaria; OUTRO nunca duplica por nome (case-insensitive)
CREATE UNIQUE INDEX uq_metodos_pagamento_usuario_tipo_fixo
  ON metodos_pagamento(usuario_id, tipo) WHERE tipo <> 'OUTRO';
CREATE UNIQUE INDEX uq_metodos_pagamento_usuario_nome_outro
  ON metodos_pagamento(usuario_id, LOWER(nome)) WHERE tipo = 'OUTRO';

-- RN-NOVA-15 — seed dos 4 metodos fixos. Contas novas ganham isso em AuthServiceImpl#register
-- (DT-NOVA-6); este backfill cobre as usuarias ja existentes antes desta versao, que sem ele
-- ficariam sem nenhum metodo de pagamento disponivel para a primeira venda de Caixa (achado desta
-- implementacao, nao coberto explicitamente pela spec — ver decisoes-config-perfil.md).
INSERT INTO metodos_pagamento (usuario_id, tipo, ativo, ordem)
SELECT u.id, t.tipo, TRUE, t.ordem
FROM usuarios u
CROSS JOIN (VALUES ('DINHEIRO', 1), ('PIX', 2), ('CARTAO_CREDITO', 3), ('CARTAO_DEBITO', 4)) AS t(tipo, ordem);
