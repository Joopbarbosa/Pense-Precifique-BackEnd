-- Fluxo antigo (1 produto, baixa imediata, status ATIVA/CANCELADA) removido por completo no P005 —
-- nenhum código lê/escreve mais essas colunas. DROP COLUMN remove em cascata os índices/constraints
-- ligados só a elas (fk_producao_produto, idx_producoes_produto_id, chk_producao_status, idx_producoes_status).
ALTER TABLE producoes
    DROP COLUMN produto_id,
    DROP COLUMN quantidade,
    DROP COLUMN status,
    DROP COLUMN observacao_cancelamento,
    DROP COLUMN data_cancelamento;
