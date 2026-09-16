-- #489 — campos fiscais minimos no cadastro de produto, preparacao para emissao futura de
-- NFC-e/NF-e (fora de escopo — Epic #416, RN-NOVA-12/13 em version/V0.12.0/DECISOES_V0.12.0.md).
-- Todos opcionais: nenhum bloqueia salvar/vender um produto nesta versao.
ALTER TABLE produtos
  ADD COLUMN codigo_barras     VARCHAR(14),
  ADD COLUMN ncm               VARCHAR(8),
  ADD COLUMN cfop              VARCHAR(4),
  ADD COLUMN cest              VARCHAR(20),
  ADD COLUMN unidade_comercial VARCHAR(10),
  ADD COLUMN csosn             VARCHAR(3);

-- RN-NOVA-12 — GTIN (codigo_barras) so aceita os tamanhos padrao (8/12/13/14 digitos)
ALTER TABLE produtos ADD CONSTRAINT chk_produto_codigo_barras CHECK (
  codigo_barras IS NULL OR codigo_barras ~ '^(\d{8}|\d{12}|\d{13}|\d{14})$'
);

-- RN-NOVA-12 — NCM sempre 8 digitos quando preenchido
ALTER TABLE produtos ADD CONSTRAINT chk_produto_ncm CHECK (
  ncm IS NULL OR ncm ~ '^\d{8}$'
);

-- RN-NOVA-12 — CFOP sempre 4 digitos quando preenchido
ALTER TABLE produtos ADD CONSTRAINT chk_produto_cfop CHECK (
  cfop IS NULL OR cfop ~ '^\d{4}$'
);

-- RN-NOVA-13 — CSOSN restrito a lista fechada do Simples Nacional
ALTER TABLE produtos ADD CONSTRAINT chk_produto_csosn CHECK (
  csosn IS NULL OR csosn IN ('101', '102', '103', '201', '202', '203', '300', '400', '500', '900')
);
