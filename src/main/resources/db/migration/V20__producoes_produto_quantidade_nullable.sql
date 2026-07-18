-- produto_id/quantidade eram obrigatórios no modelo antigo (1 produto por produção).
-- A nova criarProducao() (N produtos via producao_produtos) não popula essas colunas —
-- ficam null para produções do fluxo novo; produções legadas continuam com as duas preenchidas.
ALTER TABLE producoes
    ALTER COLUMN produto_id DROP NOT NULL,
    ALTER COLUMN quantidade DROP NOT NULL;
