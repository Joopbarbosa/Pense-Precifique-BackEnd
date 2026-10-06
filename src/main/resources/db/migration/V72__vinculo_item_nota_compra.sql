-- V0.16.0 (#716, RN-NOVA-24) — o vínculo guarda a compra (rascunho gerado da nota) em que foi criado ou
-- trocado, para o Histórico de Nota Fiscal abrir essa compra. Nulo quando não há compra (vínculos antigos).
ALTER TABLE vinculos_item_nota ADD COLUMN compra_id UUID;
ALTER TABLE vinculos_item_nota ADD CONSTRAINT fk_vinculos_item_nota_compra FOREIGN KEY (compra_id) REFERENCES compras(id) ON DELETE SET NULL;

-- V0.16.0 (#718, RN-NOVA-26) — nova origem do vínculo: ligação proposta a partir do vínculo de outro fornecedor.
ALTER TABLE vinculos_item_nota DROP CONSTRAINT chk_vinculos_item_nota_origem;
ALTER TABLE vinculos_item_nota ADD CONSTRAINT chk_vinculos_item_nota_origem CHECK (origem IN ('CASAMENTO_NOME', 'SUGESTAO_IA', 'MANUAL', 'OUTRO_FORNECEDOR'));
