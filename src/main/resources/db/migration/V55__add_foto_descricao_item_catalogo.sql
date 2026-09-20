-- #518 (DT-NOVA-3) — foto (URL pública no R2, nunca o binário) e descrição (texto livre,
-- validado em até 150 caracteres no Service, RN-NOVA-7) por Item de Catálogo.
ALTER TABLE itens_catalogo
    ADD COLUMN foto_url VARCHAR(500),
    ADD COLUMN descricao VARCHAR(150);
