-- #763 (V0.16.0): ordem de lançamento dos itens da venda do Caixa.
-- clock_timestamp() (e não now()) porque now() devolve o início da transação e todos os itens da mesma venda
-- ficariam empatados. Itens antigos recebem carimbos distintos mas arbitrários (a ordem original nunca foi gravada).
ALTER TABLE venda_caixa_item ADD COLUMN created_at timestamp NOT NULL DEFAULT clock_timestamp();
CREATE INDEX idx_venda_caixa_item_venda_ordem ON venda_caixa_item (venda_caixa_id, created_at, id);
