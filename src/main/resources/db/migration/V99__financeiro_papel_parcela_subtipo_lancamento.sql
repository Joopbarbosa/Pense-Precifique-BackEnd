-- V0.19.0 (#1174, RN-NOVA-10/11/12, DT-NOVA-4): parcelas de orçamento ganham papel (Sinal, Saldo, Total, Multa);
-- o lançamento de saída do estorno do sinal ganha subtipo; o recibo de pagamento guarda a forma realmente recebida.
-- Só acrescenta colunas nulas: não mexe em dado existente e é seguro em produção.
ALTER TABLE parcelas_financeiras ADD COLUMN papel VARCHAR(10);
ALTER TABLE parcelas_financeiras ADD CONSTRAINT chk_parcelas_fin_papel CHECK (papel IS NULL OR papel IN ('SINAL', 'SALDO', 'TOTAL', 'MULTA'));

ALTER TABLE lancamentos_financeiros ADD COLUMN subtipo VARCHAR(20);
ALTER TABLE lancamentos_financeiros ADD CONSTRAINT chk_lancamentos_fin_subtipo CHECK (subtipo IS NULL OR subtipo IN ('ESTORNO_SINAL'));

ALTER TABLE recibos_pagamento ADD COLUMN metodo_pagamento_id UUID REFERENCES metodos_pagamento(id);
