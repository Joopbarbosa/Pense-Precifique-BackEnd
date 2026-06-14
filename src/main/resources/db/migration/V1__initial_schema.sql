-- ============================================================
-- Schema: Pense & Precifique
-- v6 — Revisado em 2026-06-14
-- Adições em relação à v5:
--   - orcamentos: prazo_producao_dias, inicio_assim_que_aprovado,
--     data_inicio_estimada, data_aprovacao (Adendum v5.1)
--   - movimentacoes_insumo / movimentacoes_produto: coluna
--     "estornada" (RN-037) + motivo ESTORNO_PRODUCAO
--   - producoes: status, observacao_cancelamento, data_cancelamento (RN-037)
--   - nova tabela lotes_compra (RN-036)
--   - movimentacoes_insumo.referencia_tipo passa a aceitar LOTE_COMPRA
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- USUARIOS
-- ============================================================
CREATE TABLE usuarios (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email             VARCHAR(255) NOT NULL UNIQUE,
  senha_hash        VARCHAR(255) NOT NULL,
  ativo             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMP
);

-- ============================================================
-- EMPRESAS
-- ============================================================
CREATE TABLE empresas (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  nome              VARCHAR(255) NOT NULL,
  email             VARCHAR(255),
  whatsapp          VARCHAR(20),
  endereco          TEXT,
  logo_url          TEXT,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMP,
  CONSTRAINT fk_empresa_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ============================================================
-- CONFIGURACOES DE PRECIFICACAO
-- ============================================================
CREATE TABLE configuracoes_precificacao (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  valor_hora        DECIMAL(10,2) NOT NULL DEFAULT 0,
  margem_padrao     DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_config_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ============================================================
-- CLIENTES
-- ============================================================
CREATE TABLE clientes (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  nome              VARCHAR(255) NOT NULL,
  email             VARCHAR(255),
  whatsapp          VARCHAR(20),
  endereco          TEXT,
  observacoes       TEXT,
  ativa             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMP,
  CONSTRAINT fk_cliente_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_clientes_usuario_id ON clientes(usuario_id);

-- ============================================================
-- INSUMOS
-- ============================================================
CREATE TABLE insumos (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  nome              VARCHAR(255) NOT NULL,
  marca             VARCHAR(255),
  unidade_medida    VARCHAR(50) NOT NULL,
  fracionavel       BOOLEAN NOT NULL DEFAULT TRUE,
  preco_custo       DECIMAL(10,4) NOT NULL,
  estoque_atual     DECIMAL(10,4) NOT NULL DEFAULT 0,
  estoque_minimo    DECIMAL(10,4),
  ativo             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMP,
  CONSTRAINT fk_insumo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_insumos_usuario_id ON insumos(usuario_id);

-- Unicidade nome + marca por conta (RN da v2)
CREATE UNIQUE INDEX idx_insumos_nome_marca_usuario
  ON insumos (usuario_id, nome, COALESCE(marca, ''))
  WHERE deleted_at IS NULL;

-- ============================================================
-- LOTES DE COMPRA (novo v6 — RN-036)
-- ============================================================
CREATE TABLE lotes_compra (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  data_compra       TIMESTAMP NOT NULL DEFAULT NOW(),
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_lote_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ============================================================
-- MOVIMENTACOES DE INSUMO (v6 — observacao/estorno/lote)
-- ============================================================
CREATE TABLE movimentacoes_insumo (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  insumo_id         UUID NOT NULL,
  tipo              VARCHAR(20) NOT NULL,
  -- ENTRADA, SAIDA
  motivo            VARCHAR(50) NOT NULL,
  -- COMPRA, BAIXA_MANUAL, PRODUCAO, ORCAMENTO, ESTORNO_PRODUCAO
  quantidade        DECIMAL(10,4) NOT NULL,
  observacao        TEXT,
  -- v6: obrigatória (validação na app) para BAIXA_MANUAL, mín. 50 chars
  referencia_id     UUID,
  referencia_tipo   VARCHAR(50),
  -- PRODUCAO, ORCAMENTO, LOTE_COMPRA
  estornada         BOOLEAN NOT NULL DEFAULT FALSE,
  -- novo v6 (RN-037): true quando revertida por cancelamento de produção
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_mov_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT chk_mov_insumo_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
  CONSTRAINT chk_mov_insumo_motivo CHECK (motivo IN (
    'COMPRA','BAIXA_MANUAL','PRODUCAO','ORCAMENTO','ESTORNO_PRODUCAO'
  )),
  CONSTRAINT chk_mov_insumo_referencia_tipo CHECK (
    referencia_tipo IS NULL OR referencia_tipo IN ('PRODUCAO','ORCAMENTO','LOTE_COMPRA')
  )
);

CREATE INDEX idx_mov_insumo_insumo_id ON movimentacoes_insumo(insumo_id);
CREATE INDEX idx_mov_insumo_referencia ON movimentacoes_insumo(referencia_id, referencia_tipo);

-- ============================================================
-- PRODUTOS
-- ============================================================
CREATE TABLE produtos (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  nome              VARCHAR(255) NOT NULL,
  tipo              VARCHAR(20) NOT NULL,
  descricao         TEXT,
  tempo_producao    INTEGER NOT NULL,
  foto_url          TEXT,
  preco_venda       DECIMAL(10,2),
  preco_custo       DECIMAL(10,4),
  margem_lucro      DECIMAL(5,2),
  estoque_atual     DECIMAL(10,4) NOT NULL DEFAULT 0,
  estoque_minimo    DECIMAL(10,4),
  ativo             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMP,
  CONSTRAINT fk_produto_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT chk_produto_tipo CHECK (tipo IN ('PRODUTO', 'PRODUTO_BASE', 'CUSTOMIZACAO')),
  CONSTRAINT chk_preco_venda_tipo CHECK (
    tipo = 'PRODUTO_BASE' OR preco_venda IS NOT NULL
  )
);

CREATE INDEX idx_produtos_usuario_id ON produtos(usuario_id);
CREATE INDEX idx_produtos_tipo ON produtos(tipo);

-- ============================================================
-- FICHA TECNICA
-- ============================================================
CREATE TABLE ficha_tecnica_itens (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  produto_id            UUID NOT NULL,
  componente_tipo       VARCHAR(10) NOT NULL,
  insumo_id             UUID,
  produto_componente_id UUID,
  quantidade            DECIMAL(10,4) NOT NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_ficha_produto FOREIGN KEY (produto_id) REFERENCES produtos(id),
  CONSTRAINT fk_ficha_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_ficha_produto_comp FOREIGN KEY (produto_componente_id) REFERENCES produtos(id),
  CONSTRAINT chk_componente_tipo CHECK (componente_tipo IN ('INSUMO', 'PRODUTO')),
  CONSTRAINT chk_componente_ref CHECK (
    (componente_tipo = 'INSUMO' AND insumo_id IS NOT NULL AND produto_componente_id IS NULL) OR
    (componente_tipo = 'PRODUTO' AND produto_componente_id IS NOT NULL AND insumo_id IS NULL)
  )
);

CREATE INDEX idx_ficha_produto_id ON ficha_tecnica_itens(produto_id);
CREATE INDEX idx_ficha_insumo_id ON ficha_tecnica_itens(insumo_id);
CREATE INDEX idx_ficha_produto_comp_id ON ficha_tecnica_itens(produto_componente_id);

-- ============================================================
-- MOVIMENTACOES DE PRODUTO (v6 — observacao/estorno)
-- ============================================================
CREATE TABLE movimentacoes_produto (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  produto_id        UUID NOT NULL,
  tipo              VARCHAR(20) NOT NULL,
  -- ENTRADA, SAIDA
  motivo            VARCHAR(50) NOT NULL,
  -- PRODUCAO, ORCAMENTO, PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO, ESTORNO_PRODUCAO
  quantidade        DECIMAL(10,4) NOT NULL,
  observacao        TEXT,
  -- v6: obrigatória (validação na app) para baixa manual, mín. 50 chars, qualquer motivo
  referencia_id     UUID,
  referencia_tipo   VARCHAR(50),
  -- PRODUCAO, ORCAMENTO
  estornada         BOOLEAN NOT NULL DEFAULT FALSE,
  -- novo v6 (RN-037): true quando revertida por cancelamento de produção
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_mov_produto FOREIGN KEY (produto_id) REFERENCES produtos(id),
  CONSTRAINT chk_mov_produto_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
  CONSTRAINT chk_mov_produto_motivo CHECK (motivo IN (
    'PRODUCAO','ORCAMENTO','PERDA','AVARIA','USO_EXTRA','CORRECAO','OUTRO','ESTORNO_PRODUCAO'
  )),
  CONSTRAINT chk_mov_produto_referencia_tipo CHECK (
    referencia_tipo IS NULL OR referencia_tipo IN ('PRODUCAO','ORCAMENTO')
  )
);

CREATE INDEX idx_mov_produto_produto_id ON movimentacoes_produto(produto_id);
CREATE INDEX idx_mov_produto_referencia ON movimentacoes_produto(referencia_id, referencia_tipo);

-- ============================================================
-- REGISTRO DE PRODUCAO (v6 — status de cancelamento)
-- ============================================================
CREATE TABLE producoes (
  id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id              UUID NOT NULL,
  numero                  SERIAL,
  produto_id              UUID NOT NULL,
  quantidade              DECIMAL(10,4) NOT NULL,
  data_producao           TIMESTAMP NOT NULL DEFAULT NOW(),
  status                  VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
  -- novo v6 (RN-037): ATIVA, CANCELADA
  observacao_cancelamento TEXT,
  -- novo v6: obrigatória (mín. 50 chars) quando status = CANCELADA
  data_cancelamento       TIMESTAMP,
  -- novo v6
  created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at              TIMESTAMP,
  CONSTRAINT fk_producao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_producao_produto FOREIGN KEY (produto_id) REFERENCES produtos(id),
  CONSTRAINT chk_producao_status CHECK (status IN ('ATIVA', 'CANCELADA'))
);

CREATE INDEX idx_producoes_usuario_id ON producoes(usuario_id);
CREATE INDEX idx_producoes_status ON producoes(status);

CREATE TABLE producao_insumos_consumidos (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  producao_id       UUID NOT NULL,
  insumo_id         UUID NOT NULL,
  quantidade        DECIMAL(10,4) NOT NULL,
  CONSTRAINT fk_prod_cons_producao FOREIGN KEY (producao_id) REFERENCES producoes(id),
  CONSTRAINT fk_prod_cons_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id)
);

-- ============================================================
-- ORCAMENTOS (v6 — prazo de produção + data de aprovação)
-- ============================================================
CREATE TABLE orcamentos (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id            UUID NOT NULL,
  cliente_id            UUID NOT NULL,
  numero                SERIAL,
  status                VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO',
  -- RASCUNHO, ENVIADO, APROVADO, AGUARDANDO_SINAL, SINAL_PAGO,
  -- EM_PRODUCAO, FINALIZADO, ENTREGUE, PAGO, CANCELADO

  -- Método de pagamento combinado (v5)
  metodo_pagamento      VARCHAR(20) NOT NULL DEFAULT 'PIX',
  -- PIX, DINHEIRO, CREDITO, DEBITO, TRANSFERENCIA, BOLETO, OUTRO
  metodo_pagamento_obs  TEXT,
  -- obrigatório quando metodo_pagamento = OUTRO (mín. 50 chars)

  -- Prazo de produção (novo v6 — RN-032, do Adendum v5.1)
  prazo_producao_dias       INTEGER,
  -- ex: 10 (dias úteis), obrigatório, mínimo 1
  inicio_assim_que_aprovado BOOLEAN NOT NULL DEFAULT TRUE,
  data_inicio_estimada      DATE,
  -- preenchido se inicio_assim_que_aprovado = false
  data_aprovacao            TIMESTAMP,
  -- novo v6 (RN-033): preenchido automaticamente ao avançar para APROVADO

  -- Sinal
  sinal_ativo           BOOLEAN NOT NULL DEFAULT FALSE,
  percentual_sinal      DECIMAL(5,2),
  valor_sinal           DECIMAL(10,2),
  data_sinal_pago       TIMESTAMP,

  -- Método de pagamento recebido no sinal (v5)
  metodo_sinal_recebido     VARCHAR(20),
  metodo_sinal_recebido_obs TEXT,

  -- Cancelamento
  cancelamento_motivo       TEXT,
  cancelamento_tipo         VARCHAR(20),  -- SIMPLES, MULTA, ESTORNO, JUSTIFICATIVA
  percentual_multa          DECIMAL(5,2),
  estorno_sinal             BOOLEAN DEFAULT FALSE,
  data_estorno_sinal        TIMESTAMP,

  -- Totais
  subtotal              DECIMAL(10,2) NOT NULL DEFAULT 0,
  desconto_tipo         VARCHAR(5) DEFAULT '%',
  desconto_valor        DECIMAL(10,2) DEFAULT 0,
  total                 DECIMAL(10,2) NOT NULL DEFAULT 0,
  observacoes           TEXT,

  -- Datas
  data_validade         TIMESTAMP,
  created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at            TIMESTAMP,

  CONSTRAINT fk_orcamento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT fk_orcamento_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  CONSTRAINT chk_orcamento_status CHECK (status IN (
    'RASCUNHO','ENVIADO','APROVADO','AGUARDANDO_SINAL','SINAL_PAGO',
    'EM_PRODUCAO','FINALIZADO','ENTREGUE','PAGO','CANCELADO'
  )),
  CONSTRAINT chk_metodo_pagamento CHECK (metodo_pagamento IN (
    'PIX','DINHEIRO','CREDITO','DEBITO','TRANSFERENCIA','BOLETO','OUTRO'
  )),
  CONSTRAINT chk_prazo_producao_dias CHECK (
    prazo_producao_dias IS NULL OR prazo_producao_dias >= 1
  ),
  CONSTRAINT chk_data_inicio_estimada CHECK (
    inicio_assim_que_aprovado = TRUE OR data_inicio_estimada IS NOT NULL
  )
);

CREATE INDEX idx_orcamentos_usuario_id ON orcamentos(usuario_id);
CREATE INDEX idx_orcamentos_cliente_id ON orcamentos(cliente_id);
CREATE INDEX idx_orcamentos_status ON orcamentos(status);

-- Itens do orçamento
CREATE TABLE orcamento_itens (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  orcamento_id      UUID NOT NULL,
  produto_id        UUID NOT NULL,
  quantidade        INTEGER NOT NULL DEFAULT 1,
  preco_unitario    DECIMAL(10,2) NOT NULL,
  subtotal          DECIMAL(10,2) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_item_orcamento FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id),
  CONSTRAINT fk_item_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

CREATE INDEX idx_orcamento_itens_orcamento_id ON orcamento_itens(orcamento_id);

-- Customizações por item (v5 — quantidade própria)
CREATE TABLE orcamento_item_customizacoes (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  orcamento_item_id UUID NOT NULL,
  produto_id        UUID NOT NULL,
  quantidade        INTEGER NOT NULL DEFAULT 1,
  preco_unitario    DECIMAL(10,2) NOT NULL,
  subtotal          DECIMAL(10,2) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_custom_item FOREIGN KEY (orcamento_item_id) REFERENCES orcamento_itens(id),
  CONSTRAINT fk_custom_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

-- ============================================================
-- RECIBOS DE PAGAMENTO
-- ============================================================
CREATE TABLE recibos_pagamento (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  orcamento_id          UUID NOT NULL UNIQUE,
  data_pagamento        TIMESTAMP NOT NULL DEFAULT NOW(),
  valor_total           DECIMAL(10,2) NOT NULL,
  valor_sinal_pago      DECIMAL(10,2) NOT NULL DEFAULT 0,
  valor_restante_pago   DECIMAL(10,2) NOT NULL,
  total_quitado         DECIMAL(10,2) NOT NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_recibo_orcamento FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id)
);

-- ============================================================
-- RECIBOS DE ESTORNO DE SINAL
-- ============================================================
CREATE TABLE recibos_estorno (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  orcamento_id      UUID NOT NULL UNIQUE,
  data_estorno      TIMESTAMP NOT NULL,
  valor_estornado   DECIMAL(10,2) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_estorno_orcamento FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id)
);
