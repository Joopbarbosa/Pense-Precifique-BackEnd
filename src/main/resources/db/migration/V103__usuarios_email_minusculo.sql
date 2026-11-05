-- #699 (V0.19.0) — e-mail de acesso em minúsculas e sem espaços nas pontas.
-- Antes de normalizar, confere se duas contas colidiriam (ex.: "Ana@x.com" e "ana@x.com"). Se colidirem, a migração
-- falha com mensagem clara e NADA é alterado: o Gestor decide qual conta manter (ou apaga a duplicada) e reaplica.
DO $$
DECLARE
    colisoes TEXT;
BEGIN
    SELECT string_agg(chave || ' (' || qtd || ' contas)', '; ')
      INTO colisoes
      FROM (SELECT lower(btrim(email)) AS chave, count(*) AS qtd
              FROM usuarios
             GROUP BY lower(btrim(email))
            HAVING count(*) > 1) d;

    IF colisoes IS NOT NULL THEN
        RAISE EXCEPTION 'V103: contas com e-mail que só difere por maiúsculas/espaços: %. Resolva (apague ou renomeie uma delas) e rode a migração de novo.', colisoes;
    END IF;
END $$;

UPDATE usuarios SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

-- Rede de segurança: a unicidade passa a ignorar maiúsculas, inclusive em corrida entre cadastros.
CREATE UNIQUE INDEX uq_usuarios_email_minusculo ON usuarios (lower(email));
