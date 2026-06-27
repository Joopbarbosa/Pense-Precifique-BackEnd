# Roteiro de Validação — Épico 5
### Clientes

> **Pré-requisito:** `docker compose up --build` rodando com o fix do Épico 5.
> **Credenciais:** `penseprecifique@admin.com` / `senha12345`

---

## PARTE A — Backend (curl)

### A1. Gerar token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"penseprecifique@admin.com","senha":"senha12345"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo $TOKEN
```

---

### A2. Cadastrar cliente ✅

```bash
CLIENTE_ID=$(curl -s -X POST http://localhost:8080/clientes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Maria Silva","email":"maria@email.com","whatsapp":"5511999999999","endereco":"Rua das Flores, 10"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo $CLIENTE_ID
```

**Esperado:** UUID do cliente criado

---

### A3. Cadastrar cliente sem nome ❌

```bash
curl -i -X POST http://localhost:8080/clientes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@email.com"}'
```

**Esperado:** `400` + `fieldErrors: {"nome": "O nome do cliente é obrigatório"}`

---

### A4. Listar clientes paginado ✅

```bash
curl -i "http://localhost:8080/clientes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + lista com o cliente do A2

---

### A5. Filtrar por nome ✅

```bash
curl -i "http://localhost:8080/clientes?nome=Maria&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + apenas clientes com "Maria" no nome

---

### A6. Buscar cliente por id ✅

```bash
curl -i "http://localhost:8080/clientes/$CLIENTE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `200` + dados do cliente Maria Silva

---

### A7. Editar cliente ✅

```bash
curl -i -X PUT "http://localhost:8080/clientes/$CLIENTE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Maria Silva Atualizada","email":"maria@email.com","whatsapp":"5511999999999"}'
```

**Esperado:** `200` + `"nome":"Maria Silva Atualizada"`

---

### A8. Inativar cliente ✅

```bash
curl -i -X DELETE "http://localhost:8080/clientes/$CLIENTE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `204 No Content`

---

### A9. Confirmar que cliente inativo não aparece na listagem ✅

```bash
curl -s "http://localhost:8080/clientes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'total: {d[\"totalElements\"]}')"
```

**Esperado:** `total: 0` (cliente foi inativado)

---

### A10. Buscar cliente inativo por id ❌

```bash
curl -i "http://localhost:8080/clientes/$CLIENTE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:** `404` (cliente inativo não é encontrado)

---

### SMOKE — regressão

```bash
# Épicos anteriores ainda funcionam
curl -i "http://localhost:8080/insumos?page=0&size=20" -H "Authorization: Bearer $TOKEN" | head -1
curl -i "http://localhost:8080/produtos?page=0&size=20" -H "Authorization: Bearer $TOKEN" | head -1
```

**Esperado:** dois `HTTP/1.1 200`

---

## PARTE B — Frontend (navegador)

> Use `http://localhost:3000` com `penseprecifique@admin.com` / `senha12345`

### B1. Lista de clientes carrega dados reais ✅

Acesse `/clientes`.

**Verificar:**
- Lista carrega do backend (não dados mockados)
- Campo de busca por nome funciona

---

### B2. Cadastrar cliente pelo formulário ✅

Clique em "Novo cliente" (ou equivalente).

- Preencha nome, e-mail, whatsapp
- Salve

**Esperado:** cliente aparece na lista após salvar

---

### B3. Tentar salvar sem nome ❌

- Deixe o campo nome vazio
- Tente salvar

**Esperado:** mensagem de erro "O nome do cliente é obrigatório" visível na tela — **não** "Erro de validação" genérico

---

### B4. Editar cliente ✅

- Clique em editar no cliente cadastrado no B2
- Altere o nome
- Salve
- **Esperado:** nome atualizado na lista, dados persistem após F5

---

### B5. Inativar cliente ✅

- Menu ⋮ ou botão "Inativar" no cliente
- Confirme a inativação
- **Esperado:** cliente some da lista

---

### B6. Paginação "Carregar mais" ✅

> Se tiver menos de 20 clientes, cadastre mais 20 via curl para testar:
```bash
for i in {1..21}; do
  curl -s -X POST http://localhost:8080/clientes \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nome\":\"Cliente Teste $i\"}" > /dev/null
done
```

- Acesse `/clientes`
- **Esperado:** 20 clientes exibidos + botão "Carregar mais"
- Clique → carrega mais 20 acumulando (não substituindo)
- Busque por nome → lista reseta para página 0

---

## Resumo final

| Item | Resultado |
|------|-----------|
| A2–A10 (backend CRUD + inativação) | ⬜ OK / ⬜ Falhas: ___ |
| SMOKE (regressão Épicos anteriores) | ⬜ OK / ⬜ Falhas: ___ |
| B1 — lista carrega dados reais | ⬜ OK / ⬜ Falhas: ___ |
| B2 — cadastro funciona | ⬜ OK / ⬜ Falhas: ___ |
| B3 — erro de campo exibido corretamente | ⬜ OK / ⬜ Falhas: ___ |
| B4 — edição persiste após F5 | ⬜ OK / ⬜ Falhas: ___ |
| B5 — inativação remove da lista | ⬜ OK / ⬜ Falhas: ___ |
| B6 — paginação "Carregar mais" | ⬜ OK / ⬜ Falhas: ___ |

Se **tudo OK** → Épico 5 validado. Atualiza BACKLOG e segue para Épico 6.
Se **algo não OK** → descreva e corrigimos antes de avançar.
