-- #516 (V0.13.0, RN-NOVA-9) — Item de Catalogo passou a ter N componentes (Insumo XOR Produto-base,
-- V51); vender um item assim em Orcamento agora precisa baixar/reverter estoque de TODOS os
-- componentes (antes: so o "produto principal" + customizacoes anexadas, sempre Produto). Esta
-- tabela e o snapshot dos componentes no momento em que o item entrou no orcamento (mesmo motivo de
-- orcamento_item_customizacoes existir: sobreviver a uma edicao posterior da composicao do
-- catalogo) — nunca exposta em resposta de API, sem preco proprio (componente de catalogo nao e
-- vendido separado desde RN-NOVA-2/3), existe so para a baixa/reversao de estoque.
CREATE TABLE orcamento_item_componentes (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  orcamento_item_id   UUID NOT NULL,
  insumo_id           UUID,
  produto_base_id     UUID,
  quantidade          DECIMAL(15,4) NOT NULL,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_orc_item_comp_item FOREIGN KEY (orcamento_item_id) REFERENCES orcamento_itens(id),
  CONSTRAINT fk_orc_item_comp_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_orc_item_comp_produto_base FOREIGN KEY (produto_base_id) REFERENCES produtos(id)
);

CREATE INDEX idx_orc_item_comp_item_id ON orcamento_item_componentes(orcamento_item_id);
CREATE INDEX idx_orc_item_comp_insumo_id ON orcamento_item_componentes(insumo_id);
CREATE INDEX idx_orc_item_comp_produto_base_id ON orcamento_item_componentes(produto_base_id);
