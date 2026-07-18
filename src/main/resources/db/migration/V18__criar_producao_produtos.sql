CREATE TABLE producao_produtos (
    id           UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    producao_id  UUID    NOT NULL REFERENCES producoes(id),
    produto_id   UUID    NOT NULL REFERENCES produtos(id),
    quantidade   NUMERIC NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_producao_produtos_producao ON producao_produtos(producao_id);
CREATE INDEX idx_producao_produtos_produto  ON producao_produtos(produto_id);

-- Migra o vínculo 1-1 existente (producoes.produto_id/quantidade) para a nova tabela N.
-- producoes.produto_id e producoes.quantidade NÃO são removidos aqui — só nas tarefas #115+,
-- quando ProducaoService for refatorado para consumir producao_produtos.
INSERT INTO producao_produtos (id, producao_id, produto_id, quantidade)
SELECT uuid_generate_v4(), id, produto_id, quantidade
FROM producoes
WHERE produto_id IS NOT NULL;
