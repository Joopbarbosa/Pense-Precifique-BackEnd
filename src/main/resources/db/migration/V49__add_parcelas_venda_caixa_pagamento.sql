-- #487 (retrabalho do teste manual, V0.12.0) — numero de parcelas na venda de Caixa.
-- Fica em venda_caixa_pagamento, NAO em venda_caixa: a venda pode ser dividida entre metodos
-- (RN-NOVA-7), entao "em quantas parcelas" pertence a linha de pagamento do cartao, nao a venda
-- inteira — uma venda pode ter R$ 50 em dinheiro e R$ 100 em credito 3x ao mesmo tempo.
-- `taxa_percentual_aplicada` e snapshot da taxa vigente no momento da venda (mesma disciplina de
-- congelamento de preco ja usada nos itens): mudar a taxa em Configuracoes depois nao reescreve
-- historico de venda.
ALTER TABLE venda_caixa_pagamento
  ADD COLUMN parcelas                 INTEGER,
  ADD COLUMN taxa_percentual_aplicada DECIMAL(5,2);

ALTER TABLE venda_caixa_pagamento
  ADD CONSTRAINT chk_venda_caixa_pagamento_parcelas CHECK (
    parcelas IS NULL OR parcelas >= 1
  );
