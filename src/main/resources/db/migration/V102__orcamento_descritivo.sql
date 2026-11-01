-- #382 / DT-NOVA-10 / RN-NOVA-44 (V0.19.0) — orçamento Descritivo (cardápio de opções).
-- Mesma tabela de orçamentos (mesmo ORC-N), com tipo e título; o cliente passa a ser opcional no banco
-- (continua obrigatório para a Venda, validado no serviço por tipo). Opções em tabela própria.

ALTER TABLE orcamentos ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'VENDA' CHECK (tipo IN ('VENDA', 'DESCRITIVO'));
ALTER TABLE orcamentos ADD COLUMN titulo VARCHAR(200);
ALTER TABLE orcamentos ALTER COLUMN cliente_id DROP NOT NULL;
-- Venda sempre tem cliente; só o Descritivo pode ficar sem.
ALTER TABLE orcamentos ADD CONSTRAINT ck_orcamento_cliente_por_tipo CHECK (tipo = 'DESCRITIVO' OR cliente_id IS NOT NULL);

CREATE TABLE orcamento_descritivo_opcoes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    orcamento_id UUID NOT NULL REFERENCES orcamentos(id),
    produto_id UUID REFERENCES produtos(id),
    item_catalogo_id UUID REFERENCES itens_catalogo(id),
    preco_referencia NUMERIC(10, 2) NOT NULL CHECK (preco_referencia >= 0),
    quantidade_unidade VARCHAR(500),
    ordem INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_opcao_descritivo_origem CHECK ((produto_id IS NULL) <> (item_catalogo_id IS NULL))
);
CREATE INDEX idx_opcao_descritivo_orcamento ON orcamento_descritivo_opcoes(orcamento_id, ordem);
