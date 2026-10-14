-- V0.17.0 (#672, RN-NOVA-10, DT-NOVA-6) — histórico das mudanças do preço sugerido dos produtos.
-- Guarda valores anterior e novo (2 casas), a causa e a referência do evento; nunca o preço de venda.
CREATE TABLE mudancas_preco_sugerido (
  id               UUID PRIMARY KEY,
  usuario_id       UUID NOT NULL REFERENCES usuarios(id),
  produto_id       UUID NOT NULL REFERENCES produtos(id),
  valor_anterior   NUMERIC(15, 2) NOT NULL,
  valor_novo       NUMERIC(15, 2) NOT NULL,
  causa            VARCHAR(30) NOT NULL,
  referencia_id    UUID,
  referencia_texto VARCHAR(40),
  created_at       TIMESTAMP NOT NULL,
  CONSTRAINT chk_mudanca_preco_sugerido_causa CHECK (causa IN
    ('COMPRA_ENTRADA', 'COMPRA_CANCELAMENTO', 'PRODUTO_EDICAO', 'INSUMO_SUBSTITUICAO', 'VALOR_HORA'))
);
CREATE INDEX idx_mudancas_preco_sugerido_produto ON mudancas_preco_sugerido (produto_id, created_at DESC);

-- Insumos cujo custo provocou a mudança (só nas causas de compra).
CREATE TABLE mudancas_preco_sugerido_insumos (
  id         UUID PRIMARY KEY,
  mudanca_id UUID NOT NULL REFERENCES mudancas_preco_sugerido(id) ON DELETE CASCADE,
  insumo_id  UUID NOT NULL REFERENCES insumos(id)
);
CREATE INDEX idx_mudancas_preco_sugerido_insumos_insumo ON mudancas_preco_sugerido_insumos (insumo_id);
CREATE INDEX idx_mudancas_preco_sugerido_insumos_mudanca ON mudancas_preco_sugerido_insumos (mudanca_id);
