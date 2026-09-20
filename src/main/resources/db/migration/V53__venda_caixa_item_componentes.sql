-- #516 (V0.13.0, RN-NOVA-9) — mesmo motivo de V52 (orcamento_item_componentes), agora para Caixa:
-- vender um Item de Catalogo com N componentes (V51) precisa de um snapshot dos componentes no
-- momento da venda, para a baixa/reversao de estoque sobreviver a uma edicao posterior da
-- composicao do catalogo — nunca exposta em resposta de API, sem preco proprio (RN-NOVA-2/3).
CREATE TABLE venda_caixa_item_componentes (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  venda_caixa_item_id   UUID NOT NULL,
  insumo_id             UUID,
  produto_base_id       UUID,
  quantidade            DECIMAL(15,4) NOT NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_venda_caixa_item_comp_item FOREIGN KEY (venda_caixa_item_id) REFERENCES venda_caixa_item(id),
  CONSTRAINT fk_venda_caixa_item_comp_insumo FOREIGN KEY (insumo_id) REFERENCES insumos(id),
  CONSTRAINT fk_venda_caixa_item_comp_produto_base FOREIGN KEY (produto_base_id) REFERENCES produtos(id)
);

CREATE INDEX idx_venda_caixa_item_comp_item_id ON venda_caixa_item_componentes(venda_caixa_item_id);
CREATE INDEX idx_venda_caixa_item_comp_insumo_id ON venda_caixa_item_componentes(insumo_id);
CREATE INDEX idx_venda_caixa_item_comp_produto_base_id ON venda_caixa_item_componentes(produto_base_id);
