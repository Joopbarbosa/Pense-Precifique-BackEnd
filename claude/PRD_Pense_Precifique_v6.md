# PRD — Pense & Precifique

> **v6 — Revisado em 2026-06-14:** consolidação do Adendum v5.1 (prazo de produção e data de aprovação no orçamento), adição da paginação "Carregar mais" (RN-034), baixa manual com observação obrigatória para qualquer motivo (RN-035), compras de insumos em lote (RN-036), cancelamento de produção com reversão de estoque (RN-037 — novo Épico 6), e erratas de UI consolidadas (cores de badge por tipo de produto, remoção de botões de compra individual). Este documento substitui o PRD v5 e incorpora integralmente o ADDENDUM_v5_prazo_entrega.md, que pode ser arquivado.

## Visão geral
Pense & Precifique é uma plataforma web responsiva para artesãs que produzem itens personalizados. Resolve a principal dor do negócio: precificar com segurança, controlar estoque de insumos e produtos, e gerar orçamentos profissionais em PDF.

---

## Perfis de usuário
- **Artesã (admin da conta):** única usuária da conta — cada empresa tem login próprio e vê apenas seus dados.

---

## Fluxo crítico
Recebe pedido → cria orçamento → seleciona produtos e customizações → define método de pagamento, prazo de produção e sinal → gera PDF → cliente aprova (sistema registra data de aprovação automaticamente) → aguarda sinal (se configurado) → confirma recebimento com método real → gera recibo → Em Produção → Finalizado (baixa estoque) → Entregue → Pago (recibo de quitação).

---

## Metodologia de precificação
**Preço = Custo dos componentes (ficha técnica) + Mão de obra (tempo × valor/hora) + Margem de lucro (%)**

---

## Modelo de Produto *(v4 — sem alterações de comportamento; erratas de UI na v6)*

Todo item cadastrado em **Produtos** pertence a um dos 3 tipos:

| Tipo | Descrição | Preço de venda | Aparece no orçamento |
|------|-----------|---------------|---------------------|
| Produto | Item final vendável | ✅ Sim | ✅ Como item principal |
| Produto Base | Componente usado em fichas técnicas | ❌ Não | ❌ Não aparece diretamente |
| Customização | Extra opcional adicionado ao pedido | ✅ Sim | ✅ Como extra ao selecionar produto |

### Errata v6 — Cores de badge por tipo de produto

A cor do badge de tipo foi corrigida (estava invertida entre Produto e Produto Base nas versões anteriores):

| Tipo | Cor do badge |
|------|-------------|
| Produto | **Azul** |
| Produto Base | **Cinza** |
| Customização | **Teal** |

---

## Épicos e fluxos

### Épico 1 — Autenticação *(sem alterações)*
### Épico 2 — Configuração do perfil *(sem alterações)*

---

### Épico 3 — Insumos *(v6 — compras em lote)*

#### Fluxo: Cadastrar insumo *(sem alterações)*

#### Fluxo: Registrar compras em lote *(v6 — substitui o fluxo de compra individual)*
- Tela: Insumos → Lista de Insumos → botão "Registrar compra"
- Abre modal "carrinho de compras": artesã busca e adiciona múltiplos insumos, informando para cada um a quantidade comprada e o preço total pago
- Ao confirmar, o sistema:
  - Recalcula o custo unitário de cada insumo do carrinho (preço total ÷ quantidade)
  - Adiciona as quantidades ao estoque de cada insumo
  - Registra uma movimentação por insumo com origem "compra", todas vinculadas ao mesmo lote (`referencia_id` comum)
  - Exibe modal de impacto agregado: lista de produtos e customizações afetados pelas mudanças de custo, com o novo preço sugerido de cada um
- Preço final de venda das fichas técnicas **não é alterado automaticamente** — artesã decide se quer recalcular manualmente em cada ficha
- Próxima tela: lista de insumos atualizada

**Errata v6:** o botão "Cadastrar novo insumo" dentro do modal de compra em lote foi **removido** (decisão de simplicidade — insumos novos devem ser cadastrados previamente em Insumos → Novo Insumo).

**Errata v6:** o botão de compra individual no Detalhe do Insumo foi **removido** — compras de insumo são sempre centralizadas na Lista de Insumos via o fluxo de compra em lote acima.

#### Fluxo: Baixa manual de estoque *(v6 — observação sempre obrigatória)*
- Tela: Insumos → detalhe do insumo
- Usuária informa quantidade a subtrair, motivo (Perda, Avaria, Uso extra, Correção de estoque, Outro) e **observação com no mínimo 50 caracteres, obrigatória para qualquer motivo selecionado**
- Sistema registra a movimentação (incluindo a observação) e atualiza o saldo
- A observação é exibida no histórico de movimentações
- Próxima tela: histórico de movimentações do insumo

#### Fluxo: Visualizar histórico de movimentações e preços *(sem alterações de comportamento; paginação v6 — ver RN-034)*

---

### Épico 4 — Produtos *(v4 — sem alterações estruturais; baixa manual com observação obrigatória v6)*

#### Fluxo: Cadastrar / Editar produto *(sem alterações)*

#### Fluxo: Detalhe do produto *(sem alterações; histórico paginado — RN-034)*

#### Fluxo: Baixa manual de produto *(v6 — observação sempre obrigatória)*
- Motivos: Perda, Avaria, Uso extra, Correção de estoque, Outro
- Campo **Observação obrigatório com no mínimo 50 caracteres para qualquer motivo selecionado** (não apenas "Outro")
- A observação é exibida no histórico de movimentações do produto

---

### Épico 5 — Clientes *(v4 — sem alterações; lista paginada — RN-034)*

---

### Épico 6 — Registro de Produção *(v6 — cancelamento de produção adicionado)*
**Objetivo:** artesã registra o que já produziu para dar baixa nos insumos e atualizar o estoque dos produtos, podendo cancelar produções lançadas incorretamente.

#### Fluxo: Lançar produção *(sem alterações)*
- Modal com toggle de tipo: **Produto | Produto Base | Customização**
- Buscador filtra itens do tipo selecionado
- Sistema exibe insumos que serão consumidos com indicador de suficiência
- Confirmar → baixa insumos + adiciona estoque do item produzido

#### Fluxo: Detalhe da produção *(sem alterações)*
- Header: número e data da produção
- Card: produto produzido + quantidade
- Tabela de insumos consumidos
- **Novo v6:** botão de ação ⋮ disponível apenas na **lista** de produções (não duplicado no detalhe), com opção "Cancelar produção" quando a produção está ativa

#### Fluxo: Cancelar produção *(novo v6 — RN-037)*
- Tela: Registro de Produção → menu ⋮ na lista → "Cancelar produção"
- Sistema exibe modal de confirmação com:
  - Resumo da produção (produto, quantidade, insumos consumidos)
  - Campo de observação obrigatório, mínimo 50 caracteres, justificando o cancelamento
  - Aviso de que a ação reverte o estoque e não pode ser desfeita
- Ao confirmar:
  - O sistema reverte o estoque do produto produzido (subtrai a quantidade que havia sido adicionada)
  - O sistema reverte o estoque de cada insumo consumido (devolve as quantidades ao estoque)
  - A produção é marcada como **cancelada** e não pode ser reativada
  - No histórico do produto e de cada insumo envolvido, a linha original da movimentação aparece **riscada**, e uma nova linha de **estorno** é adicionada, com a observação informada
- Próxima tela: lista de produções, com a produção cancelada exibida com indicador visual de status

---

### Épico 7 — Orçamentos *(v6 — prazo de produção, data de aprovação e listas paginadas)*

**Status completo:**
Rascunho → Enviado → Aprovado → [Aguardando Sinal*] → [Sinal Pago*] → Em Produção → Finalizado → Entregue → Pago → [Cancelado**]

*Apenas quando sinal configurado | **Disponível em todos os status com regras distintas

#### Criar Orçamento — Seção 3: Condições de pagamento e prazo *(v6)*

A seção 3 passa a ter três blocos, na seguinte ordem:

**Bloco A — Método de pagamento combinado** *(v5 — sem alterações)*
- Opções: Pix | Dinheiro | Crédito | Débito | Transferência | Boleto Bancário | Outro
- "Outro" exige justificativa com mínimo de 50 caracteres
- Método aparece no PDF do orçamento como "método de pagamento combinado"

**Bloco B — Prazo de produção** *(novo v6, incorporado do Adendum v5.1)*
- Campo numérico: "Quantos dias úteis para esta produção?" (obrigatório, mínimo 1)
- Checkbox: "Início assim que aprovado" (default: marcado)
- Se o checkbox for desmarcado → aparece o campo de data "Data estimada de início" (obrigatório)

**Bloco C — Sinal (entrada)** *(v5 — sem alterações, posição ajustada para após o prazo)*
- Toggle Sim/Não
- Se Sim: campo de valor (% ou R$), exibe sinal calculado e restante

#### Customizações no orçamento *(v5 — sem alterações)*
- Cada customização adicionada a um produto tem quantidade própria
- Modal de customizações tem campo de busca e stepper de quantidade por customização

#### Confirmar Sinal — Modal *(v5 — sem alterações)*
- Método de pagamento recebido, separado do método combinado
- Pode ser diferente do método combinado; "Outro" exige mínimo 50 caracteres
- Método recebido aparece no recibo do sinal

#### Data de aprovação *(novo v6 — RN-033)*
- Quando o status do orçamento avança para **Aprovado**, o sistema registra automaticamente a data de aprovação (timestamp)
- Esta data é exibida nos PDFs gerados a partir desse momento: recibo do sinal, PDF de multa e recibo de pagamento

#### Exibição do prazo e datas nos PDFs *(novo v6)*

**PDF do orçamento — seção Datas:**
```
DATAS
Emissão:           04/06/2026
Validade:          11/06/2026
Prazo de produção: 10 dias úteis
Início estimado:   Assim que aprovado
                   ou  20/06/2026
```

**Demais PDFs (recibo do sinal, PDF de multa, recibo de pagamento) — seção Datas:**
- Prazo de produção: X dias úteis
- Início estimado: [data ou "Assim que aprovado"]
- Data de aprovação: [data real, preenchida quando status avançou para Aprovado]

**PDFs gerados:**
- PDF do orçamento (método de pagamento combinado + prazo de produção + cláusula de cancelamento + seção de sinal se ativo)
- Recibo do sinal (método de pagamento recebido + prazo + data de aprovação)
- Recibo de estorno do sinal (ao cancelar com status Sinal Pago e optar por devolver)
- PDF de multa (ao cancelar Em Produção ou Finalizado com multa; inclui prazo + data de aprovação)
- Recibo de pagamento completo (ao marcar como Pago; inclui prazo + data de aprovação)

**Cancelamento por status *(v5 — sem alterações)*:**

| Status | Fluxo |
|--------|-------|
| Rascunho / Enviado / Aprovado | Confirmação simples |
| Aguardando Sinal | Confirmação simples (sinal não foi pago) |
| Sinal Pago | Wizard 2 passos: 1) Estornar sinal? (Sim/Não + data) → 2) Confirmar e gerar recibo de estorno |
| Em Produção / Finalizado | Wizard 3 passos: 1) Consumo de insumos → 2) Multa (sugestão 50%) → 3) Confirmar e gerar PDF de multa |
| Entregue / Pago | Justificativa obrigatória mín. 50 caracteres |

#### Listar e filtrar orçamentos *(v6 — paginação)*
- Lista paginada com "Carregar mais" (20 itens por página), reset ao aplicar filtro — ver RN-034

---

### Épico 8 — Dashboard *(sem alterações)*

---

## Listagens paginadas *(novo v6 — RN-034)*

A regra de paginação "Carregar mais" se aplica a todas as listagens do sistema:
- Lista de Insumos
- Lista de Produtos
- Lista de Clientes
- Lista de Orçamentos
- Lista de Registro de Produção
- Histórico de movimentações (Insumo e Produto)

Comportamento: 20 itens por página; botão "Carregar mais" ao final da lista enquanto houver itens adicionais; ao aplicar qualquer filtro ou busca, a lista reseta para a primeira página de resultados filtrados.

---

## Configurações *(sem alterações da v4)*

---

## Telas do sistema (21 no total — sem alterações na contagem)
1. Login | 2. Cadastro | 3. Onboarding | 4. Dashboard | 5. Clientes | 6. Criar Orçamento | 7. Preview PDF Orçamento | 8. Detalhe/Status Orçamento | 9. Lista Orçamentos | 10. Lista Insumos | 11. Cadastrar/Editar Insumo | 12. Detalhe Insumo | 13. Lista Produtos | 14. Cadastrar Produto | 15. Editar Produto | 16. Detalhe Produto | 17. Registro de Produção | 18. Configurações | 19. Preview PDF Multa | 20. Recibo do Sinal | 21. Recibo de Pagamento Completo

*(Recibo de Estorno é gerado dinamicamente a partir do Detalhe do Orçamento — não é uma tela separada. Modal de compra em lote e modal de cancelamento de produção são componentes das telas 10 e 17, respectivamente — não são telas separadas.)*

---

## Próximos passos
→ ✅ **Skill 4:** Design das telas — concluído (21 telas)
→ ✅ **Skill 5:** Estrutura técnica — concluída
→ 🔄 **Skill 6:** Desenvolvimento frontend — fechamento do MVP (C-000 a C-034)
→ **Skill 1:** Entrevistas para Módulo de Vendas / Mini PDV e refinamentos de Compras de Insumos (v2)
→ **Skill 7:** Backend — Spring Boot + PostgreSQL, implementar RN-027 a RN-037
