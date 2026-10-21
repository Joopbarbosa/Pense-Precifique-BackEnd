-- #1017 (RN-NOVA-36) — marca opcional em produto e item de catálogo. Sem índice único: cadastros antigos repetem nome; a unicidade nome + marca é feita no serviço.
ALTER TABLE produtos ADD COLUMN marca VARCHAR(100);
ALTER TABLE itens_catalogo ADD COLUMN marca VARCHAR(100);
