-- V0.17.0 (#958, RN-NOVA-5, DT-NOVA-1) — novo estado PENDENTE_RECEBIMENTO da compra (entrega não imediata).
ALTER TABLE compras DROP CONSTRAINT chk_compra_status;
ALTER TABLE compras ADD CONSTRAINT chk_compra_status CHECK (status IN ('RASCUNHO', 'PENDENTE_RECEBIMENTO', 'CONFIRMADA', 'CANCELADA'));
