-- #488/#487 (retrabalho do teste manual, V0.12.0).
--
-- caixa_turnos.fechamento_justificativa — obrigatoria (min. 30 caracteres, mesmo minimo ja usado
-- no motivo de sangria) SOMENTE quando o valor contado diverge do esperado. Por ser condicional,
-- a regra vive no Service, nao como NOT NULL/@Size: fechamento sem diferenca continua sem texto.
--
-- venda_caixa.estoque_retornado — ate aqui o cancelamento SEMPRE devolvia estoque, sem registro da
-- decisao. Agora a usuaria escolhe, e a escolha fica auditavel. NULL = vendas canceladas antes
-- desta versao, quando devolver era o unico comportamento possivel.
ALTER TABLE caixa_turnos ADD COLUMN fechamento_justificativa TEXT;

ALTER TABLE venda_caixa ADD COLUMN estoque_retornado BOOLEAN;
