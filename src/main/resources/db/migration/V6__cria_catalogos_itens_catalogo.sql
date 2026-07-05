CREATE TABLE catalogos (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  usuario_id        UUID NOT NULL,
  numero            INTEGER NOT NULL,
  nome              VARCHAR(255) NOT NULL,
  margem            DECIMAL(5,2) NOT NULL,
  ativo             BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_catalogo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  CONSTRAINT chk_catalogo_margem_positiva CHECK (margem > 0),
  CONSTRAINT uq_catalogo_usuario_numero UNIQUE (usuario_id, numero),
  CONSTRAINT uq_catalogo_usuario_nome UNIQUE (usuario_id, nome)
);

CREATE INDEX idx_catalogos_usuario_id ON catalogos(usuario_id);

CREATE TABLE itens_catalogo (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  catalogo_id         UUID NOT NULL,
  produto_id          UUID NOT NULL,
  quantidade_pacote   INTEGER NOT NULL,
  preco_venda         DECIMAL(10,2) NOT NULL,
  override            BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at          TIMESTAMP,
  CONSTRAINT fk_item_catalogo_catalogo FOREIGN KEY (catalogo_id) REFERENCES catalogos(id),
  CONSTRAINT fk_item_catalogo_produto FOREIGN KEY (produto_id) REFERENCES produtos(id),
  CONSTRAINT chk_item_catalogo_qtd_pacote CHECK (quantidade_pacote >= 1)
);

CREATE INDEX idx_itens_catalogo_catalogo_id ON itens_catalogo(catalogo_id);
CREATE INDEX idx_itens_catalogo_produto_id ON itens_catalogo(produto_id);

CREATE TABLE itens_catalogo_customizacao (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  item_catalogo_id  UUID NOT NULL,
  produto_id        UUID NOT NULL,
  quantidade        DECIMAL(10,4) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_item_cat_custom_item FOREIGN KEY (item_catalogo_id) REFERENCES itens_catalogo(id),
  CONSTRAINT fk_item_cat_custom_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

CREATE INDEX idx_itens_cat_custom_item_id ON itens_catalogo_customizacao(item_catalogo_id);
