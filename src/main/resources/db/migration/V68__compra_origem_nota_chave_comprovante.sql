-- V0.16.0 (#683, DT-NOVA-10) — compra criada a partir de nota fiscal: origem NFCE_QR/NFE_PDF/NFE_FOTO/NFE_XML,
-- chave de acesso (44 posições, única por usuária entre compras não excluídas — RN-NOVA-5) e comprovante
-- (link do QR ou arquivo original guardado no R2).

ALTER TABLE compras DROP CONSTRAINT chk_compra_origem;
ALTER TABLE compras ADD CONSTRAINT chk_compra_origem
    CHECK (origem IN ('MANUAL', 'NFCE_QR', 'NFE_PDF', 'NFE_FOTO', 'NFE_XML'));

ALTER TABLE compras ADD COLUMN chave_acesso     VARCHAR(44);
ALTER TABLE compras ADD COLUMN comprovante_tipo VARCHAR(10);
ALTER TABLE compras ADD COLUMN comprovante_nome VARCHAR(255);
ALTER TABLE compras ADD COLUMN comprovante_url  TEXT;

ALTER TABLE compras ADD CONSTRAINT chk_compra_chave_acesso
    CHECK (chave_acesso IS NULL OR chave_acesso ~ '^[0-9A-Z]{44}$');
ALTER TABLE compras ADD CONSTRAINT chk_compra_comprovante_tipo
    CHECK (comprovante_tipo IS NULL OR comprovante_tipo IN ('LINK', 'PDF', 'IMAGEM', 'XML'));

-- RN-NOVA-5: uma compra viva por nota e usuária; rascunho excluído (deleted_at) não conta.
CREATE UNIQUE INDEX uq_compra_usuario_chave_acesso
    ON compras (usuario_id, chave_acesso)
    WHERE chave_acesso IS NOT NULL AND deleted_at IS NULL;
