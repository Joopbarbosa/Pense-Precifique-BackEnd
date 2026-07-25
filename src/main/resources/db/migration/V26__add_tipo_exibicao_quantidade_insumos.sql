-- #186 (RN-NOVA-1) — tipo de exibição de quantidade (FRACAO/DECIMAL), aplicável só quando fracionavel = true.
ALTER TABLE insumos ADD COLUMN tipo_exibicao_quantidade VARCHAR(20);

ALTER TABLE insumos ADD CONSTRAINT chk_insumo_tipo_exibicao_quantidade CHECK (
  tipo_exibicao_quantidade IS NULL OR tipo_exibicao_quantidade IN ('FRACAO', 'DECIMAL')
);
