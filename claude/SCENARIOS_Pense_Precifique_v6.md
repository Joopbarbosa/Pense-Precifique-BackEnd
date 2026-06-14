# Cenários BDD — Pense & Precifique

> **v6 — Revisado em 2026-06-14:** cenários 71–80 adicionados cobrindo prazo de produção e data de aprovação (do Adendum v5.1), paginação "Carregar mais", observação obrigatória para qualquer motivo de baixa manual, compras de insumos em lote e cancelamento de produção com reversão de estoque. Este documento substitui o SCENARIOS v5 e incorpora os cenários do ADDENDUM_v5_prazo_entrega.md.

*(Cenários 1 a 70 mantidos das versões anteriores)*

---

## Funcionalidade: Prazo de Produção e Data de Aprovação *(v6, do Adendum v5.1)*

**Regras relacionadas:** RN-032, RN-033

---

### Cenário 71 — Informar prazo de produção com início assim que aprovado ✅ Caminho feliz

```gherkin
Dado que a artesã está criando um novo orçamento
Quando ela chega no campo "Prazo de produção"
E informa "10" dias úteis
E mantém o checkbox "Início assim que aprovado" marcado
Então o PDF do orçamento exibe:
  "Prazo de produção: 10 dias úteis"
  "Início estimado: Assim que aprovado"
```

---

### Cenário 72 — Informar prazo de produção com data estimada de início ✅ Caminho feliz

```gherkin
Dado que a artesã está criando um novo orçamento
Quando ela informa "3" dias úteis no campo de prazo
E desmarca o checkbox "Início assim que aprovado"
Então o campo de data "Data estimada de início" aparece
Quando ela seleciona a data "20/06/2026"
Então o PDF exibe:
  "Prazo de produção: 3 dias úteis"
  "Início estimado: 20/06/2026"
```

---

### Cenário 73 — Campo de prazo obrigatório ❌ Exceção

```gherkin
Dado que a artesã está criando um orçamento
Quando ela não preenche o campo de prazo de produção
E tenta gerar o PDF
Então o sistema exibe erro "Informe o prazo de produção"
E o campo fica com borda vermelha
```

---

### Cenário 74 — Prazo e data de aprovação aparecem no recibo do sinal ✅ Caminho feliz

```gherkin
Dado que um orçamento com prazo "10 dias úteis, início assim que aprovado" foi aprovado em 05/06/2026
Quando a artesã confirma o recebimento do sinal
Então o recibo do sinal exibe na seção Datas:
  "Data de aprovação: 05/06/2026"
  "Prazo de produção: 10 dias úteis"
  "Início estimado: Assim que aprovado"
```

---

## Funcionalidade: Paginação "Carregar mais" *(nova v6)*

**Regras relacionadas:** RN-034

---

### Cenário 75 — Carregar mais itens na lista de orçamentos ✅ Caminho feliz

```gherkin
Dado que existem 45 orçamentos cadastrados
Quando a artesã abre a tela "Lista de Orçamentos"
Então o sistema exibe os primeiros 20 orçamentos
E exibe o botão "Carregar mais"
Quando ela clica em "Carregar mais"
Então o sistema exibe mais 20 orçamentos (total 40)
E o botão "Carregar mais" continua visível
Quando ela clica novamente em "Carregar mais"
Então o sistema exibe os 5 orçamentos restantes (total 45)
E o botão "Carregar mais" desaparece
```

---

### Cenário 76 — Aplicar filtro reseta a paginação ✅ Caminho feliz

```gherkin
Dado que a artesã já carregou 40 dos 45 orçamentos clicando em "Carregar mais"
Quando ela aplica o filtro de status "Pago"
Então a lista reseta e exibe os primeiros 20 resultados filtrados (ou menos, se houver menos de 20)
E o botão "Carregar mais" aparece apenas se houver mais resultados filtrados além dos exibidos
```

---

## Funcionalidade: Baixa Manual com Observação Obrigatória *(v6 — refina cenário 55)*

**Regras relacionadas:** RN-035

---

### Cenário 77 — Observação obrigatória para motivo "Perda" na baixa de insumo ❌ Exceção

```gherkin
Dado que a artesã está no modal de baixa manual do insumo "Papel Couché 250g"
Quando ela seleciona o motivo "Perda"
E informa a quantidade a subtrair
Então o campo "Observação" é exibido como obrigatório com contador de caracteres
E o botão "Registrar baixa" permanece desabilitado enquanto a observação tiver menos de 50 caracteres
Quando ela escreve uma observação com 50 caracteres ou mais
Então o botão "Registrar baixa" é habilitado
E, ao confirmar, a observação aparece na linha correspondente do histórico de movimentações
```

---

### Cenário 78 — Observação obrigatória para motivo "Avaria" na baixa de produto ❌ Exceção

```gherkin
Dado que a artesã está no modal de baixa manual do produto "Kit Convite Casamento"
Quando ela seleciona o motivo "Avaria"
Então o campo "Observação" é exibido como obrigatório, independentemente do motivo selecionado
E o botão "Registrar baixa" só é habilitado após a observação atingir 50 caracteres
```

---

## Funcionalidade: Compras de Insumos em Lote *(nova v6)*

**Regras relacionadas:** RN-036
**Casos de uso:** UC-019

---

### Cenário 79 — Registrar compra de múltiplos insumos em lote ✅ Caminho feliz

```gherkin
Dado que a artesã está na Lista de Insumos
Quando ela clica em "Registrar compra"
Então o sistema abre o modal "Carrinho de compras"
Quando ela busca e adiciona "Papel Couché 250g", informando quantidade 500 e preço total R$ 250,00
E busca e adiciona "Cola Branca 1L", informando quantidade 3 e preço total R$ 45,00
E confirma o lote
Então o sistema recalcula o custo unitário de "Papel Couché 250g" para R$ 0,50/folha
E recalcula o custo unitário de "Cola Branca 1L" para R$ 15,00/litro
E adiciona 500 folhas e 3 litros aos respectivos estoques
E registra duas movimentações de entrada com motivo "Compra", vinculadas ao mesmo lote
E exibe modal de impacto agregado listando os produtos afetados e seus novos preços sugeridos
```

---

### Cenário 80 — Modal de compra em lote não permite cadastrar novo insumo ❌ Errata de UI

```gherkin
Dado que a artesã está no modal "Carrinho de compras"
Então não há opção "Cadastrar novo insumo" disponível neste modal
E não há botão de compra individual na tela de Detalhe do Insumo
E, para cadastrar um insumo novo, a artesã deve acessar Insumos → Novo Insumo antes de iniciar a compra em lote
```

---

## Funcionalidade: Cancelamento de Produção *(nova v6)*

**Regras relacionadas:** RN-037
**Casos de uso:** UC-020

---

### Cenário 81 — Cancelar produção reverte estoque do produto e dos insumos ✅ Caminho feliz

```gherkin
Dado que a produção #18 (10 unidades de "Kit Convite Casamento") está com status "Ativa"
E consumiu 30 folhas de "Papel Couché 250g" e 0,5L de "Cola Branca 1L"
Quando a artesã abre o menu ⋮ da produção #18 na lista
E seleciona "Cancelar produção"
Então o sistema exibe modal com resumo da produção e campo de observação obrigatório (mín. 50 caracteres)
Quando ela informa uma observação com 60 caracteres e confirma
Então o estoque de "Kit Convite Casamento" diminui em 10 unidades
E o estoque de "Papel Couché 250g" aumenta em 30 folhas
E o estoque de "Cola Branca 1L" aumenta em 0,5L
E a produção #18 passa para status "Cancelada"
E no histórico de "Kit Convite Casamento" a linha original de entrada aparece riscada, seguida por uma linha de estorno
E no histórico de cada insumo a linha original de saída aparece riscada, seguida por uma linha de estorno
```

---

### Cenário 82 — Observação obrigatória ao cancelar produção ❌ Exceção

```gherkin
Dado que a artesã abriu o modal "Cancelar produção" para a produção #18
Quando ela tenta confirmar sem preencher a observação
Então o botão "Confirmar cancelamento" permanece desabilitado
E o sistema exibe a mensagem "Informe uma observação com no mínimo 50 caracteres"
```

---

### Cenário 83 — Produção cancelada não pode ser reativada ou cancelada novamente ❌ Exceção

```gherkin
Dado que a produção #18 está com status "Cancelada"
Quando a artesã abre o menu ⋮ da produção #18
Então a opção "Cancelar produção" não está disponível
E não existe opção para reativar a produção
E a lista exibe a produção #18 com indicador visual de status "Cancelada"
```

---

### Cenário 84 — Menu ⋮ disponível apenas na lista, não no detalhe ✅ Errata de UI

```gherkin
Dado que a artesã está na tela de Detalhe da Produção (/producao/:numero)
Então não há menu de ações (⋮) nesta tela
E a ação "Cancelar produção" está disponível apenas no menu ⋮ da Lista de Registro de Produção
```

---

## Índice de Rastreabilidade — Adições v6

| Cenário | Regra | Caso de Uso | Prioridade |
|---------|-------|-------------|------------|
| 71 — Prazo com início assim que aprovado | RN-032 | — | Alta |
| 72 — Prazo com data estimada de início | RN-032 | — | Alta |
| 73 — Campo de prazo obrigatório | RN-032 | — | Alta |
| 74 — Prazo e data de aprovação no recibo do sinal | RN-032, RN-033 | — | Alta |
| 75 — Carregar mais na lista de orçamentos | RN-034 | — | Média |
| 76 — Filtro reseta paginação | RN-034 | — | Média |
| 77 — Observação obrigatória — baixa de insumo | RN-035 | — | Alta |
| 78 — Observação obrigatória — baixa de produto | RN-035 | — | Alta |
| 79 — Compra de insumos em lote | RN-036 | UC-019 | Alta |
| 80 — Modal de lote sem cadastro de insumo | RN-036 | UC-019 | Média |
| 81 — Cancelar produção reverte estoque | RN-037 | UC-020 | Alta |
| 82 — Observação obrigatória ao cancelar produção | RN-037 | UC-020 | Alta |
| 83 — Produção cancelada não pode ser reativada | RN-037 | UC-020 | Alta |
| 84 — Menu ⋮ apenas na lista | RN-037 | UC-020 | Média |

---

## Próximos passos
→ ✅ **Skill 4:** Design concluído — 21 telas
→ ✅ **Skill 5:** Estrutura técnica concluída
→ ✅ **Skill 6:** Frontend — fechamento do MVP
→ **Skill 7:** Backend — cenários 71-84 como base para implementação e testes
→ **Skill 8/Test Plan:** usa todos os cenários como base para testes automatizados
