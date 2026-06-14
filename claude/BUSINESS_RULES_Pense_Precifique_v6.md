# Regras de Negócio — Pense & Precifique

> **v6 — Revisado em 2026-06-14:** RN-032 (prazo de produção obrigatório), RN-033 (data de aprovação automática), RN-034 (paginação "Carregar mais"), RN-035 (observação obrigatória mín. 50 chars para qualquer motivo de baixa manual — refina RN-024), RN-036 (compras de insumos em lote), RN-037 (cancelamento de produção com reversão de estoque); UC-019 (registrar compra em lote), UC-020 (cancelar produção); campos novos em Orçamento e nova entidade ProducaoInsumoConsumido com status de estorno. Este documento substitui o BUSINESS_RULES v5 e incorpora o ADDENDUM_v5_prazo_entrega.md.

---

## Entidades

### Produto *(v4 — sem alterações)*

| Campo | Tipo | Obrigatório | Observação |
|-------|------|-------------|------------|
| nome | texto | sim | |
| tipo | enum | sim | produto, produto_base, customizacao |
| descricao | texto | não | |
| tempo_producao | inteiro | sim | em minutos |
| foto | imagem | não | |
| preco_venda | decimal | não | obrigatório se tipo = produto ou customizacao |
| preco_custo | decimal | calculado | calculado pela ficha técnica |
| estoque_atual | decimal | sim | |
| estoque_minimo | decimal | não | para alerta no dashboard |
| ativo | booleano | sim | default true |

---

### Orçamento *(v6 — campos de prazo e data de aprovação adicionados)*

Campos novos em relação à v5:

| Campo | Tipo | Observação |
|-------|------|------------|
| prazo_producao_dias | inteiro | obrigatório, mínimo 1 — quantidade de dias úteis |
| inicio_assim_que_aprovado | booleano | default true |
| data_inicio_estimada | data | obrigatório se inicio_assim_que_aprovado = false |
| data_aprovacao | datetime | preenchido automaticamente quando status avança para Aprovado |

*(Campos de método de pagamento e demais campos da v5 mantidos sem alteração)*

---

### OrcamentoItemCustomizacao *(v5 — sem alterações)*

---

### Recibo do Sinal *(v5 — sem alterações)*

### Recibo de Estorno *(v5 — sem alterações)*

### Recibo de Pagamento *(v4 — sem alterações)*

---

### Movimentação de Insumo *(v6 — observação obrigatória e suporte a lote/estorno)*

| Campo | Tipo | Observação |
|-------|------|------------|
| insumo_id | referência | |
| tipo | enum | ENTRADA, SAIDA |
| motivo | enum | COMPRA, BAIXA_MANUAL, PRODUCAO, ORCAMENTO, ESTORNO_PRODUCAO |
| quantidade | decimal | |
| observacao | texto | **v6: obrigatória (mín. 50 chars) para baixa manual de qualquer motivo** |
| referencia_id | UUID | id da produção, orçamento ou lote de compra |
| referencia_tipo | enum | PRODUCAO, ORCAMENTO, LOTE_COMPRA |
| estornada | booleano | **novo v6** — true quando a movimentação original foi revertida por cancelamento de produção (exibida riscada no histórico) |

---

### Movimentação de Produto *(v6 — observação obrigatória e suporte a estorno)*

| Campo | Tipo | Observação |
|-------|------|------------|
| produto_id | referência | |
| tipo | enum | ENTRADA, SAIDA |
| motivo | enum | PRODUCAO, ORCAMENTO, PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO, ESTORNO_PRODUCAO |
| quantidade | decimal | |
| observacao | texto | **v6: obrigatória (mín. 50 chars) para baixa manual de qualquer motivo** |
| referencia_id | UUID | id da produção ou orçamento |
| referencia_tipo | enum | PRODUCAO, ORCAMENTO |
| estornada | booleano | **novo v6** — true quando a movimentação original foi revertida por cancelamento de produção |

---

### Produção *(v6 — status de cancelamento)*

| Campo | Tipo | Observação |
|-------|------|------------|
| numero | inteiro | sequencial |
| produto_id | referência | |
| quantidade | decimal | |
| data_producao | datetime | |
| status | enum | **novo v6** — ATIVA, CANCELADA |
| observacao_cancelamento | texto | **novo v6** — obrigatória (mín. 50 chars) quando status = CANCELADA |
| data_cancelamento | datetime | **novo v6** — preenchida ao cancelar |

---

### Lote de Compra *(novo v6 — RN-036)*

| Campo | Tipo | Observação |
|-------|------|------------|
| id | UUID | usado como `referencia_id` nas movimentações de insumo do lote |
| usuario_id | referência | |
| data_compra | datetime | |
| created_at | datetime | |

Cada item do lote gera uma `movimentacao_insumo` (tipo ENTRADA, motivo COMPRA, referencia_tipo LOTE_COMPRA) com o `referencia_id` apontando para o lote.

---

## Regras de Negócio

*(RN-001 a RN-031 mantidas das versões anteriores)*

### RN-032 — Prazo de produção é obrigatório no orçamento *(v6, do Adendum v5.1)*
**Regra:** ao criar um orçamento, a artesã deve informar quantos dias úteis a produção vai levar (campo numérico, mínimo 1) e se o início será assim que aprovado (checkbox, default marcado) ou em uma data estimada específica (datepicker, exibido quando o checkbox é desmarcado). O prazo em dias úteis e a data estimada de início aparecem em todos os PDFs do orçamento.

### RN-033 — Data de aprovação é registrada automaticamente *(v6, do Adendum v5.1)*
**Regra:** quando o status do orçamento avança para "Aprovado", o sistema registra automaticamente a data de aprovação. Esta data aparece nos PDFs de recibo do sinal, PDF de multa e recibo de pagamento.

### RN-034 — Paginação "Carregar mais" em todas as listagens *(nova v6)*
**Regra:** todas as listagens do sistema (Insumos, Produtos, Clientes, Orçamentos, Registro de Produção, históricos de movimentação) exibem inicialmente 20 itens, com botão "Carregar mais" para exibir os próximos 20. Ao aplicar qualquer filtro, busca ou ordenação, a listagem reseta para a primeira página de resultados filtrados.

### RN-035 — Observação obrigatória mín. 50 caracteres para qualquer motivo de baixa manual *(nova v6 — refina RN-024)*
**Regra:** ao registrar baixa manual de insumo ou produto, independentemente do motivo selecionado (Perda, Avaria, Uso extra, Correção de estoque ou Outro), o campo de observação é **obrigatório** e deve ter no mínimo 50 caracteres. A observação é exibida na linha correspondente do histórico de movimentações.

> Esta regra substitui o comportamento da RN-024, em que a observação era obrigatória apenas para o motivo "Outro". A partir da v6, a obrigatoriedade se aplica a todos os motivos.

### RN-036 — Registro de compras de insumos em lote *(nova v6)*
**Regra:** o registro de compra de insumos é feito exclusivamente através de um modal de "carrinho de compras" acessado pela Lista de Insumos, no qual a artesã pode adicionar múltiplos insumos, informando para cada um a quantidade comprada e o preço total pago. Ao confirmar:
- o sistema recalcula o custo unitário de cada insumo do lote (preço total ÷ quantidade comprada);
- adiciona as quantidades compradas ao estoque de cada insumo;
- registra uma movimentação de entrada (motivo COMPRA) por insumo, todas vinculadas ao mesmo `lote_compra` via `referencia_id`;
- exibe um modal de impacto agregado, listando produtos e customizações afetados pela mudança de custo e o novo preço sugerido de cada um.

O preço final de venda das fichas técnicas não é alterado automaticamente — a artesã decide se recalcula manualmente em cada ficha. Não há opção de cadastrar novo insumo a partir deste modal, e não há fluxo de compra individual a partir do Detalhe do Insumo.

### RN-037 — Cancelamento de produção reverte estoque e gera estorno no histórico *(nova v6)*
**Regra:** uma produção com status ATIVA pode ser cancelada pela artesã através do menu de ações (⋮) na Lista de Registro de Produção. O cancelamento exige observação obrigatória com no mínimo 50 caracteres, justificando o motivo. Ao confirmar:
- o sistema subtrai do estoque do produto produzido a quantidade que havia sido adicionada pela produção;
- o sistema devolve ao estoque de cada insumo consumido a quantidade que havia sido baixada;
- registra, para o produto e para cada insumo afetado, uma nova movimentação com motivo ESTORNO_PRODUCAO, contendo a observação informada;
- marca a movimentação original (do produto e de cada insumo) com `estornada = true`, fazendo-a aparecer **riscada** no histórico, com a nova linha de estorno imediatamente após;
- altera o status da produção para CANCELADA, registra `data_cancelamento` e `observacao_cancelamento`.

Uma produção CANCELADA **não pode ser reativada** e não pode ser cancelada novamente. O menu ⋮ de uma produção CANCELADA não exibe a opção "Cancelar produção".

---

## Tabela completa de cancelamento por status (orçamento) *(v5 — sem alterações)*

| Status | Tipo de cancelamento | Documentos gerados |
|--------|---------------------|-------------------|
| Rascunho | Confirmação simples | Nenhum |
| Enviado | Confirmação simples | Nenhum |
| Aprovado | Confirmação simples | Nenhum |
| Aguardando Sinal | Confirmação simples | Nenhum |
| Sinal Pago | Wizard 2 passos (estorno opcional) | Recibo de estorno (se optou por estornar) |
| Em Produção | Wizard 3 passos (consumo + multa) | PDF de multa (se cobrou multa) |
| Finalizado | Wizard 3 passos (consumo + multa) | PDF de multa (se cobrou multa) |
| Entregue | Justificativa mín. 50 chars | Nenhum |
| Pago | Justificativa mín. 50 chars | Nenhum |

---

## Casos de Uso

*(UC-001 a UC-018 mantidos das versões anteriores)*

### UC-019 — Registrar compra de insumos em lote *(novo v6)*
**Ator:** Artesã
**Fluxo:**
1. Artesã acessa Insumos → Lista de Insumos → "Registrar compra"
2. Sistema abre modal de carrinho de compras
3. Artesã busca e adiciona insumos, um a um, informando quantidade comprada e preço total pago para cada
4. Artesã confirma o lote
5. Sistema recalcula custo unitário de cada insumo, atualiza estoques, registra movimentações vinculadas ao lote
6. Sistema exibe modal de impacto agregado com produtos/customizações afetados e novos preços sugeridos
7. Artesã fecha o modal → retorna à lista de insumos atualizada

### UC-020 — Cancelar produção *(novo v6)*
**Ator:** Artesã
**Pré-condição:** produção com status ATIVA
**Fluxo:**
1. Artesã acessa Registro de Produção → Lista
2. Abre o menu ⋮ da produção desejada → "Cancelar produção"
3. Sistema exibe modal de confirmação com resumo da produção (produto, quantidade, insumos consumidos) e campo de observação obrigatório (mín. 50 caracteres)
4. Artesã preenche a observação e confirma
5. Sistema reverte o estoque do produto e dos insumos consumidos, registra movimentações de estorno, marca as movimentações originais como estornadas (exibidas riscadas no histórico) e altera o status da produção para CANCELADA
6. Lista de produções é atualizada, exibindo a produção cancelada com indicador visual de status

---

## Métodos de pagamento aceitos *(v5 — sem alterações)*

| Código | Label | Observação |
|--------|-------|-----------|
| PIX | Pix | |
| DINHEIRO | Dinheiro | |
| CREDITO | Crédito | |
| DEBITO | Débito | |
| TRANSFERENCIA | Transferência | TED/DOC |
| BOLETO | Boleto Bancário | |
| OUTRO | Outro | Justificativa obrigatória mín. 50 chars |

---

## Próximos passos
→ ✅ **Skill 4:** Design concluído — 21 telas
→ ✅ **Skill 5:** Estrutura técnica concluída
→ ✅ **Skill 6:** Frontend — fechamento do MVP (C-000 a C-034)
→ **Skill 1:** Entrevistas — Módulo de Vendas / Mini PDV; refinamentos de Compras de Insumos
→ **Skill 7:** Backend — implementar RN-027 a RN-037
