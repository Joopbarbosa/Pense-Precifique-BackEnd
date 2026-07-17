# Contrato da API — Pense & Precifique

> Documentação de contrato de endpoints (request/response). Complementa o Swagger (`/swagger-ui.html`, apenas em `dev`) com o comportamento de negócio de cada filtro/parâmetro que não é óbvio a partir do schema OpenAPI.

---

## GET /orcamentos

**Autenticação:** Bearer JWT obrigatório

**Query params:**
| Parâmetro | Tipo   | Obrigatório | Descrição |
|-----------|--------|-------------|-----------|
| busca     | string | não         | Filtra por `cliente.nome` contendo o termo (case-insensitive) |
| status    | enum   | não         | Filtra por `StatusOrcamento` (RASCUNHO, ENVIADO, APROVADO, AGUARDANDO_SINAL, SINAL_PAGO, EM_PRODUCAO, FINALIZADO, ENTREGUE, PAGO, CANCELADO) |
| page      | int    | não         | Página (default 0) |
| size      | int    | não         | Itens por página (default 20) |

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "numero": 1,
      "identificador": "ORC-1",
      "nomeCliente": "Mariana Costa",
      "status": "RASCUNHO",
      "total": 300.00,
      "dataValidade": "2026-08-01T00:00:00",
      "createdAt": "2026-07-16T22:00:00",
      "updatedAt": "2026-07-16T22:00:00"
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "last": false
}
```

**Comportamento:**
- `busca` e `status` são independentes e combináveis (ambos aplicados juntos quando informados).
- Sem parâmetros: retorna todos os orçamentos do usuário autenticado, paginados.
- `identificador` no formato `ORC-N` (sem zero-padding, sem cedilha) — RN-053, nunca aparece em PDF.
- `numero` (Integer cru) é mantido ao lado de `identificador` para uso interno/ordenação, seguindo o mesmo padrão de `InsumoResponseDTO`.
- Diferente de outros módulos (ex: `GET /produtos?busca=`, que filtra pelo nome do próprio recurso): aqui `busca` filtra por `cliente.nome`, não pelo nome do orçamento (que não existe como campo).

---

## GET /producoes

**Autenticação:** Bearer JWT obrigatório

**Query params:**
| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| page      | int  | não         | Página (default 0) |
| size      | int  | não         | Itens por página (default 20) |

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "numero": 3,
      "identificador": "PRD-3",
      "produtoId": "uuid",
      "nomeProduto": "Bolo de Cenoura",
      "tipoProduto": "PRODUTO",
      "quantidade": 10.0000,
      "dataProducao": "2026-07-16T22:00:00",
      "status": "ATIVA"
    }
  ],
  "totalElements": 28,
  "totalPages": 2,
  "last": false
}
```

**Comportamento:**
- Ordenação: `numero DESC` (maior número primeiro) — corrigido de `dataProducao DESC` (bug #99), pois `dataProducao` é campo mutável e não garante ordem de lançamento.
- Diferente de outros módulos, Produção não aceita parâmetro `?busca=` nesta versão.
