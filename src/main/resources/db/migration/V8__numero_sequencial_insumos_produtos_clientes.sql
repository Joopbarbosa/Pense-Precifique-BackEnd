-- EP-10 (P-027): numero sequencial por usuario em insumos/produtos/clientes (RN-053).
-- producoes e orcamentos ja tem a coluna numero (preenchida pelo Service, MAX+1 por usuario);
-- so faltava a constraint UNIQUE(usuario_id, numero) nessas duas. catalogos ja tem desde o V7 (nao tocar).

-- Insumos: adiciona coluna + backfill sequencial por usuario, ordem de criacao (created_at, id como desempate).
ALTER TABLE insumos ADD COLUMN numero INTEGER;

UPDATE insumos i
SET numero = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY usuario_id ORDER BY created_at, id) AS rn
    FROM insumos
) sub
WHERE i.id = sub.id;

ALTER TABLE insumos ALTER COLUMN numero SET NOT NULL;
ALTER TABLE insumos ADD CONSTRAINT uq_insumo_usuario_numero UNIQUE (usuario_id, numero);

-- Produtos: mesma logica.
ALTER TABLE produtos ADD COLUMN numero INTEGER;

UPDATE produtos p
SET numero = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY usuario_id ORDER BY created_at, id) AS rn
    FROM produtos
) sub
WHERE p.id = sub.id;

ALTER TABLE produtos ALTER COLUMN numero SET NOT NULL;
ALTER TABLE produtos ADD CONSTRAINT uq_produto_usuario_numero UNIQUE (usuario_id, numero);

-- Clientes: mesma logica.
ALTER TABLE clientes ADD COLUMN numero INTEGER;

UPDATE clientes c
SET numero = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY usuario_id ORDER BY created_at, id) AS rn
    FROM clientes
) sub
WHERE c.id = sub.id;

ALTER TABLE clientes ALTER COLUMN numero SET NOT NULL;
ALTER TABLE clientes ADD CONSTRAINT uq_cliente_usuario_numero UNIQUE (usuario_id, numero);

-- Producoes e orcamentos: numero ja existe e ja e unico por usuario (confirmado antes desta migration),
-- so falta declarar a constraint.
ALTER TABLE producoes ADD CONSTRAINT uq_producao_usuario_numero UNIQUE (usuario_id, numero);
ALTER TABLE orcamentos ADD CONSTRAINT uq_orcamento_usuario_numero UNIQUE (usuario_id, numero);
