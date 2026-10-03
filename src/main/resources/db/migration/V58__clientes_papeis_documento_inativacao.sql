-- V0.15.0 — cadastro único Clientes e Fornecedores (#536, #538; DT-NOVA-1, DT-NOVA-2).

-- #536 — papéis (RN-NOVA-1). Todo registro existente passa a ser Cliente.
ALTER TABLE clientes ADD COLUMN eh_cliente BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clientes ADD COLUMN eh_fornecedor BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE clientes ADD CONSTRAINT chk_cliente_papel CHECK (eh_cliente OR eh_fornecedor);

-- #536 — tipo de pessoa e documento (RN-NOVA-17). Documento guardado normalizado (sem máscara,
-- maiúsculo). O índice único parcial cobre ativos e inativos (duplicado na conta é BLOQUEIO).
ALTER TABLE clientes ADD COLUMN tipo_pessoa VARCHAR(20) NOT NULL DEFAULT 'FISICA';
ALTER TABLE clientes ADD CONSTRAINT chk_cliente_tipo_pessoa
    CHECK (tipo_pessoa IN ('FISICA', 'JURIDICA', 'ESTRANGEIRO'));
ALTER TABLE clientes ADD COLUMN documento VARCHAR(30);
CREATE UNIQUE INDEX uq_cliente_usuario_documento ON clientes (usuario_id, documento)
    WHERE documento IS NOT NULL;

ALTER TABLE clientes ADD COLUMN telefone VARCHAR(20);
ALTER TABLE clientes ADD COLUMN site VARCHAR(255);

-- #538 — inativar passa a ser reversível e só alterna `ativa` (RN-NOVA-2). Os registros inativados
-- antes desta versão tinham ativa = false e deleted_at juntos; viram inativos reativáveis.
UPDATE clientes SET ativa = FALSE WHERE deleted_at IS NOT NULL;
ALTER TABLE clientes DROP COLUMN deleted_at;
