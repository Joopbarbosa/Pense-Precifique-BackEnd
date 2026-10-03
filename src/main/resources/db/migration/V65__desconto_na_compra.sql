-- V0.15.0 (#576, RN-NOVA-28, DT-NOVA-17) — desconto por linha e por nota na compra. preco_total continua
-- sendo o preço PAGO (líquido): custo médio, vínculo, lista e evolução não mudam de regra.
ALTER TABLE compra_itens ADD COLUMN preco_cheio        NUMERIC(15, 2);
ALTER TABLE compra_itens ADD COLUMN desconto_tipo      VARCHAR(20);
ALTER TABLE compra_itens ADD COLUMN desconto_informado NUMERIC(15, 2);
ALTER TABLE compra_itens ADD COLUMN desconto_linha     NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE compra_itens ADD COLUMN desconto_nota      NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE compra_itens ADD CONSTRAINT chk_compra_itens_desconto_tipo
    CHECK (desconto_tipo IS NULL OR desconto_tipo IN ('VALOR', 'PERCENTUAL'));

ALTER TABLE compras ADD COLUMN desconto_nota_tipo      VARCHAR(20);
ALTER TABLE compras ADD COLUMN desconto_nota_informado NUMERIC(15, 2);
ALTER TABLE compras ADD COLUMN desconto_nota           NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE compras ADD CONSTRAINT chk_compras_desconto_nota_tipo
    CHECK (desconto_nota_tipo IS NULL OR desconto_nota_tipo IN ('VALOR', 'PERCENTUAL'));

-- Compras existentes: sem desconto, preço cheio = preço pago.
UPDATE compra_itens SET preco_cheio = preco_total WHERE preco_total IS NOT NULL;
