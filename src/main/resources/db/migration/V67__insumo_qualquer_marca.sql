ALTER TABLE insumos ADD COLUMN qualquer_marca boolean NOT NULL DEFAULT false;
ALTER TABLE insumos ADD CONSTRAINT ck_insumos_qualquer_marca
    CHECK (NOT qualquer_marca OR marca IS NULL OR marca = '');
