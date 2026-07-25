-- #179 — coluna órfã confirmada: gravada em prePersist() e nunca lida em nenhum DTO/mapper/response
-- (listagem ordena por numero DESC desde #99, não por data). Não fazia parte do fluxo legado limpo na
-- V21 porque tecnicamente não pertencia a ele — resíduo à parte, levantado na retomada V0.6.
ALTER TABLE producoes DROP COLUMN data_producao;
