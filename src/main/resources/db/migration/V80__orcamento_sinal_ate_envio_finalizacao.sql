-- V0.18.0 (#1035, DT-NOVA-4, RN-NOVA-12 e RN-NOVA-14): "Sinal até" (data opcional, só com sinal ativo) e as datas de
-- envio e de finalização do orçamento (gravadas uma vez, como data_pagamento e data_entrega). Sem carga retroativa:
-- orçamentos anteriores ficam com os três campos vazios.
ALTER TABLE orcamentos ADD COLUMN sinal_ate DATE;
ALTER TABLE orcamentos ADD COLUMN data_envio TIMESTAMP;
ALTER TABLE orcamentos ADD COLUMN data_finalizacao TIMESTAMP;
