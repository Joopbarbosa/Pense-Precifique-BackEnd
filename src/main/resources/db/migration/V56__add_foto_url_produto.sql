-- #531 (DT-NOVA-5) — foto do produto (URL pública no R2, nunca o binário), mesmo formato de
-- itens_catalogo.foto_url (V55).
ALTER TABLE produtos
    ADD COLUMN foto_url VARCHAR(500);
