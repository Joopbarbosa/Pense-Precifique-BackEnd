# Roteiro de Validação — Épicos 3, 3.1 e 4
### Insumos + Compras em Lote + Produtos

> **Pré-requisito:** `docker compose up --build` rodando.
> **Credenciais:** `onboarding.epico2@teste.com` / `123456789`

---

## Conceitos desta validação

**Fluxo integrado testado:**
Cadastrar insumo → registrar compra em lote → ver custo recalculado → cadastrar produto com ficha técnica usando esse insumo → ver precoCusto calculado → dar baixa manual → ver histórico com movimentações

---

## PARTE A — Backend (curl)

### A1. Gerar token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"onboarding.epico2@teste.com","senha":"123456789"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo $TOKEN
```

**Esperado:** string longa começando com `eyJ...`

---

## ÉPICO 3 — Insumos

### A2. Cadastrar insumo ✅

```bash
curl -i -X POST http://localhost:8080/insumos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Papel Couché","marca":"Suzano","unidadeMedida":"folha","estoqueAtual":100,"estoqueMinimo":20}'
```

**Esperado:** `201` + JSON com `id`, `custoUnitario: 0`, `estoqueAtual: 100`

```bash
# Salvar o id para usar nos próximos testes
INSUMO_ID=$(curl -s -X POST http://localhost:8080/insumos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Cola Branca","marca":"Tenaz","unidadeMedida":"ml","estoqueAtual":500}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo $INSUMO_ID
```

**Esperado:** UUID do insumo "Cola Branca"

---

### A3. Cadastrar insumo duplicado (unicidade nome+marca) ❌

```bash
curl -i -X POST http://localhost:8080/insumos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Cola Branca","marca":"Tenaz","unidadeMedida":"ml"}'
```

**Esperado:** `400` + `"message":"Já existe um insumo com este nome e marca."`

---

### A4. Listar insumos paginado ✅

```bash
curl -i "http://localhost:8080/insumos?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + estrutura de página com `content`, `totalElements`, `hasNext`

---

### A5. Buscar insumo por id ✅

```bash
curl -i "http://localhost:8080/insumos/$INSUMO_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + dados do insumo "Cola Branca"

---

### A6. Editar insumo ✅

```bash
curl -i -X PUT "http://localhost:8080/insumos/$INSUMO_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Cola Branca","marca":"Tenaz","unidadeMedida":"ml","estoqueAtual":500,"estoqueMinimo":50}'
```

**Esperado:** `200` + `estoqueMinimo: 50`

---

### A7. Baixa manual sem observação ❌

```bash
curl -i -X POST "http://localhost:8080/insumos/$INSUMO_ID/baixa-manual" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantidade":10,"motivo":"BAIXA_MANUAL","observacao":"curta"}'
```

**Esperado:** `400` + erro de validação em `observacao` (mínimo 50 chars)

---

### A8. Baixa manual com observação válida ✅

```bash
curl -i -X POST "http://localhost:8080/insumos/$INSUMO_ID/baixa-manual" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantidade":10,"motivo":"BAIXA_MANUAL","observacao":"Baixa de teste para validação do épico 3 - observação com mais de cinquenta caracteres"}'
```

**Esperado:** `201` + movimentação com `tipo: SAIDA`, `motivo: BAIXA_MANUAL`, `estornada: false`

---

### A9. Histórico de movimentações paginado ✅

```bash
curl -i "http://localhost:8080/insumos/$INSUMO_ID/movimentacoes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + página com a movimentação do A8 (mais recente primeiro)

---

### A10. Inativar insumo ✅

```bash
# Criar insumo temporário para inativar
INSUMO_TEMP=$(curl -s -X POST http://localhost:8080/insumos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Insumo Temp","marca":"Temp","unidadeMedida":"un"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -i -X DELETE "http://localhost:8080/insumos/$INSUMO_TEMP" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `204 No Content`

```bash
# Confirmar que não aparece mais na listagem
curl -s "http://localhost:8080/insumos?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; data=json.load(sys.stdin); print([i['nome'] for i in data['content']])"
```

**Esperado:** "Insumo Temp" não aparece na lista

---

## ÉPICO 3.1 — Compras em Lote

### A11. Buscar id do insumo "Papel Couché" para o lote

```bash
INSUMO_PAPEL=$(curl -s "http://localhost:8080/insumos?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; data=json.load(sys.stdin); [print(i['id']) for i in data['content'] if 'Papel' in i['nome']]")

echo $INSUMO_PAPEL
```

---

### A12. Registrar compra em lote ✅

```bash
curl -i -X POST http://localhost:8080/lotes-compra \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"itens\": [
      {\"insumoId\": \"$INSUMO_PAPEL\", \"quantidadeComprada\": 500, \"precoTotalPago\": 250.00},
      {\"insumoId\": \"$INSUMO_ID\", \"quantidadeComprada\": 3, \"precoTotalPago\": 45.00}
    ]
  }"
```

**Esperado:** `201` + `ImpactoAgregadoResponse` com:
- `insumosAtualizados` contendo 2 itens
- "Papel Couché": `custoUnitarioNovo: 0.5` (250/500)
- "Cola Branca": `custoUnitarioNovo: 15.0` (45/3)

---

### A13. Confirmar que custo foi recalculado e estoque atualizado ✅

```bash
curl -s "http://localhost:8080/insumos/$INSUMO_ID" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'custoUnitario: {d[\"custoUnitario\"]}, estoqueAtual: {d[\"estoqueAtual\"]}')"
```

**Esperado:** `custoUnitario: 15.0`, `estoqueAtual: 493` (500 - 10 da baixa + 3 da compra)

---

### A14. Confirmar movimentação de COMPRA no histórico ✅

```bash
curl -s "http://localhost:8080/insumos/$INSUMO_ID/movimentacoes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; data=json.load(sys.stdin); [print(f'{m[\"motivo\"]} | {m[\"tipo\"]} | {m[\"quantidade\"]}') for m in data['content']]"
```

**Esperado:** duas linhas — `COMPRA | ENTRADA | 3.0` e `BAIXA_MANUAL | SAIDA | 10.0`

---

### A15. Lote com item inválido ❌

```bash
curl -i -X POST http://localhost:8080/lotes-compra \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"itens": [{"insumoId": "00000000-0000-0000-0000-000000000000", "quantidadeComprada": 10, "precoTotalPago": 50}]}'
```

**Esperado:** `404` com mensagem de insumo não encontrado

---

## ÉPICO 4 — Produtos

### A16. Cadastrar produto base (sem precoVenda) ✅

```bash
PRODUTO_BASE_ID=$(curl -s -X POST http://localhost:8080/produtos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"nome\": \"Envelope Kraft\",
    \"tipo\": \"PRODUTO_BASE\",
    \"tempoProducao\": 10,
    \"fichaTecnica\": [
      {\"insumoId\": \"$INSUMO_PAPEL\", \"quantidade\": 2}
    ]
  }" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['id'])")

echo $PRODUTO_BASE_ID
```

**Esperado:** UUID do produto base criado

---

### A17. Tentar cadastrar produto base COM precoVenda ❌

```bash
curl -i -X POST http://localhost:8080/produtos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste","tipo":"PRODUTO_BASE","tempoProducao":10,"precoVenda":50,"fichaTecnica":[]}'
```

**Esperado:** `400` + `"message":"Produto Base não pode ter preço de venda."`

---

### A18. Cadastrar produto final com ficha técnica ✅

```bash
PRODUTO_ID=$(curl -s -X POST http://localhost:8080/produtos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"nome\": \"Kit Convite Casamento\",
    \"tipo\": \"PRODUTO\",
    \"tempoProducao\": 120,
    \"precoVenda\": 150.00,
    \"fichaTecnica\": [
      {\"insumoId\": \"$INSUMO_ID\", \"quantidade\": 5},
      {\"produtoBaseId\": \"$PRODUTO_BASE_ID\", \"quantidade\": 1}
    ]
  }" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['id'])")

echo $PRODUTO_ID
```

**Esperado:** UUID do produto criado

---

### A19. Confirmar precoCusto calculado ✅

```bash
curl -s "http://localhost:8080/produtos/$PRODUTO_ID" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'precoCusto: {d[\"precoCusto\"]}, itens ficha: {len(d[\"fichaTecnica\"])}')"
```

**Esperado:**
- `precoCusto` = (5 × 15.0) + (1 × precoCusto do Envelope Kraft) — valor calculado automaticamente
- `itens ficha: 2`

---

### A20. Tentar cadastrar produto sem precoVenda ❌

```bash
curl -i -X POST http://localhost:8080/produtos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Sem Preco","tipo":"PRODUTO","tempoProducao":30,"fichaTecnica":[]}'
```

**Esperado:** `400` + `"message":"Preço de venda é obrigatório para Produto e Customização."`

---

### A21. Listar produtos com filtro por tipo ✅

```bash
curl -i "http://localhost:8080/produtos?tipo=PRODUTO_BASE&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + apenas produtos do tipo `PRODUTO_BASE`

---

### A22. Baixa manual de produto sem observação ❌

```bash
curl -i -X POST "http://localhost:8080/produtos/$PRODUTO_ID/baixa-manual" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantidade":1,"motivo":"AVARIA","observacao":"curta"}'
```

**Esperado:** `400` + erro de validação em `observacao` (mínimo 50 chars)

---

### A23. Baixa manual de produto com observação válida ✅

```bash
curl -i -X POST "http://localhost:8080/produtos/$PRODUTO_ID/baixa-manual" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantidade":1,"motivo":"AVARIA","observacao":"Produto danificado durante transporte - validação do épico 4 com observação suficiente"}'
```

**Esperado:** `201` + movimentação com `tipo: SAIDA`, `motivo: AVARIA`

---

### A24. Histórico de movimentações de produto ✅

```bash
curl -i "http://localhost:8080/produtos/$PRODUTO_ID/movimentacoes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + movimentação do A23

---

### SMOKE — regressão Épicos 1 e 2

```bash
# Épico 1: rota protegida sem token → 401
curl -i http://localhost:8080/insumos | head -1

# Épico 2: empresa ainda acessível
curl -i http://localhost:8080/empresa -H "Authorization: Bearer $TOKEN" | head -1
```

**Esperado:** `401` e `200`

---

## PARTE B — Frontend (navegador)

> Use `http://localhost:3000` com o usuário `onboarding.epico2@teste.com`

### B1. Lista de Insumos carrega dados reais ✅

Acesse `/insumos`.

**Verificar:**
- Insumos cadastrados nos testes A2 aparecem na lista
- "Cola Branca" exibe `custoUnitario: R$ 15,00` (atualizado pela compra em lote)
- Sem botão de compra individual (errata v6)

---

### B2. Cadastrar novo insumo pelo formulário ✅

Acesse `/insumos/novo` (ou botão "Novo Insumo").

- Preencha nome, unidade de medida
- Salve
- **Esperado:** redirecionamento para a lista, novo insumo aparece

---

### B3. Detalhe do insumo — histórico e baixa manual ✅

Acesse o detalhe da "Cola Branca".

**Verificar:**
- Dados reais (não mockados)
- Histórico exibe movimentações: `COMPRA` e `BAIXA_MANUAL`
- Tente registrar baixa com observação < 50 chars → botão desabilitado
- Registre baixa com observação válida → estoque atualizado, histórico recarregado

---

### B4. Modal de compra em lote ✅

Acesse `/insumos`, clique em "Registrar compra".

**Verificar:**
- Modal abre corretamente
- Busca de insumos funciona
- Sem opção "Cadastrar novo insumo" (Cenário 80)
- Adicione 2 insumos com quantidade e preço
- Confirme → modal de impacto aparece com custos anterior/novo

---

### B5. Lista de Produtos — badges por tipo ✅

Acesse `/produtos`.

**Verificar:**
- Badge `PRODUTO` = azul
- Badge `PRODUTO_BASE` = cinza
- Badge `CUSTOMIZACAO` = teal
- "Carregar mais" funciona (se houver mais de 20 produtos)

---

### B6. Cadastrar produto com ficha técnica ✅

Acesse o formulário de novo produto.

**Verificar:**
- Campo `precoVenda` desaparece ao selecionar tipo `PRODUTO_BASE`
- Adicione insumo na ficha técnica → `precoCusto` calculado em tempo real
- Salve → redireciona para lista com toast de sucesso

---

### B7. Detalhe do produto — ficha técnica e baixa manual ✅

Acesse o detalhe do "Kit Convite Casamento".

**Verificar:**
- Ficha técnica exibe `custoTotal` por item
- `precoCusto` total exibido
- Baixa manual: botão desabilitado com observação < 50 chars (Cenário 78)
- Após baixa: estoque atualizado, histórico recarregado
- Histórico exibe movimentação com estilo correto

---

## Resumo final

| Item | Resultado |
|------|-----------|
| A2–A10 (Insumos CRUD + baixa manual) | ⬜ OK / ⬜ Falhas: ___ |
| A11–A15 (Compras em lote) | ⬜ OK / ⬜ Falhas: ___ |
| A16–A24 (Produtos CRUD + ficha técnica + baixa) | ⬜ OK / ⬜ Falhas: ___ |
| SMOKE (regressão Épicos 1 e 2) | ⬜ OK / ⬜ Falhas: ___ |
| B1–B4 (Frontend Insumos) | ⬜ OK / ⬜ Falhas: ___ |
| B5–B7 (Frontend Produtos) | ⬜ OK / ⬜ Falhas: ___ |

Se **tudo OK** → Épicos 3, 3.1 e 4 validados! Atualize o BACKLOG.md e siga para o checkpoint de testes automatizados (Skill 8).

Se **algo não OK** → descreva o que viu e geramos um prompt de correção pontual.
