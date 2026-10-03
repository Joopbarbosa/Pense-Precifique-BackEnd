-- V0.15.0 — #546/RN-NOVA-12 (DT-NOVA-8): lista de compras gerada = retrato imutável (LST-N).
-- A prévia é calculada na hora e não é salva; "Gerar" grava a lista com os valores COPIADOS
-- (nome, unidade, estoques, fornecedor, preço de referência), que não mudam depois. Sem edição nem
-- exclusão de lista gerada.
CREATE TABLE listas_compra (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id  UUID NOT NULL,
  numero      INTEGER NOT NULL,
  gerada_em   TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_lista_compra_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT uq_lista_compra_usuario_numero UNIQUE (usuario_id, numero)
);

CREATE TABLE lista_compra_itens (
  id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  lista_id           UUID NOT NULL,
  insumo_id          UUID NOT NULL,
  insumo_nome        VARCHAR(255) NOT NULL,
  unidade            VARCHAR(50),
  estoque_atual      DECIMAL(15,4) NOT NULL,
  estoque_minimo     DECIMAL(15,4),
  quantidade         DECIMAL(15,4) NOT NULL,
  fornecedor_id      UUID,
  fornecedor_nome    VARCHAR(255),
  preco_referencia   DECIMAL(15,4),
  ordem              INTEGER NOT NULL,
  CONSTRAINT fk_lista_compra_item_lista FOREIGN KEY (lista_id) REFERENCES listas_compra(id),
  CONSTRAINT fk_lista_compra_item_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_lista_compra_item_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES clientes(id),
  CONSTRAINT chk_lista_compra_item_quantidade CHECK (quantidade > 0)
);

CREATE INDEX idx_listas_compra_usuario_id ON listas_compra (usuario_id);
CREATE INDEX idx_lista_compra_itens_lista_id ON lista_compra_itens (lista_id);
