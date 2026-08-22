ALTER TABLE orcamentos ADD COLUMN valor_multa DECIMAL(10,2);

-- Backfill de orçamentos já cancelados por multa antes desta migration — mesma fórmula do
-- RN-NOVA-1 (total * percentual_multa / 100, descontado o sinal já pago quando sinal_ativo,
-- piso zero), para não deixar o PDF de multa desses registros históricos sem valor (o cálculo
-- deixa de ser feito ao vivo no PdfMapper e passa a ler só a coluna persistida).
UPDATE orcamentos
SET valor_multa = GREATEST(
    ROUND(total * percentual_multa / 100, 2)
        - COALESCE(CASE WHEN sinal_ativo THEN valor_sinal ELSE 0 END, 0),
    0
)
WHERE cancelamento_tipo = 'MULTA' AND percentual_multa IS NOT NULL;
