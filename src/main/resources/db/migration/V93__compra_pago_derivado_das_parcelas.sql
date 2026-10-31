-- V0.19.0 (#1173, DT-NOVA-3, RN-NOVA-7/9) — Pago/Não pago da compra passa a ser derivado das parcelas do financeiro.
-- A compra parcelada no cartão de crédito fica "Não paga" mas guarda o método informado; por isso o CHECK anterior
-- (Não pago nunca tem método) deixa de valer. Mantém-se só "Pago exige método". A regra "Não pago só tem método quando é
-- cartão de crédito parcelado" é validada no CompraService (precisa do tipo do método, que está em outra tabela).
ALTER TABLE compras DROP CONSTRAINT chk_compra_pagamento;
ALTER TABLE compras ADD CONSTRAINT chk_compra_pagamento CHECK (NOT pago OR metodo_pagamento_id IS NOT NULL);
