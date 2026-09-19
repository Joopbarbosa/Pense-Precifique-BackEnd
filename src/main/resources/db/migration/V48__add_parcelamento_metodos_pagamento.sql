-- #491 (retrabalho do teste manual, V0.12.0) — parcelamento de Cartao de Credito.
-- `taxa_parcela_uniforme = TRUE`  -> usa `taxa_maquininha` (ja existente, V40) para toda parcela.
-- `taxa_parcela_uniforme = FALSE` -> usa uma linha por parcela em metodo_pagamento_taxas_parcela.
-- Segue a mesma trava por tipo que a V40 aplicou a `taxa_maquininha`, restringindo a CARTAO_CREDITO
-- (debito nao parcela).
ALTER TABLE metodos_pagamento
  ADD COLUMN max_parcelas          INTEGER,
  ADD COLUMN taxa_parcela_uniforme BOOLEAN;

ALTER TABLE metodos_pagamento
  ADD CONSTRAINT chk_metodo_pagamento_parcelas_tipo CHECK (
    max_parcelas IS NULL OR tipo = 'CARTAO_CREDITO'
  ),
  ADD CONSTRAINT chk_metodo_pagamento_max_parcelas CHECK (
    max_parcelas IS NULL OR max_parcelas BETWEEN 1 AND 24
  ),
  ADD CONSTRAINT chk_metodo_pagamento_uniforme_tipo CHECK (
    taxa_parcela_uniforme IS NULL OR tipo = 'CARTAO_CREDITO'
  );

CREATE TABLE metodo_pagamento_taxas_parcela (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  metodo_pagamento_id UUID NOT NULL,
  parcela             INTEGER NOT NULL,
  taxa                DECIMAL(5,2) NOT NULL,
  CONSTRAINT fk_taxa_parcela_metodo FOREIGN KEY (metodo_pagamento_id)
    REFERENCES metodos_pagamento(id) ON DELETE CASCADE,
  CONSTRAINT chk_taxa_parcela_numero CHECK (parcela BETWEEN 1 AND 24),
  CONSTRAINT chk_taxa_parcela_valor CHECK (taxa >= 0)
);

CREATE UNIQUE INDEX uq_taxa_parcela_metodo_parcela
  ON metodo_pagamento_taxas_parcela(metodo_pagamento_id, parcela);
