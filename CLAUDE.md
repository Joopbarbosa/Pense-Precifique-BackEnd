# Pense & Precifique — Contexto do Back-End

> Lido automaticamente pelo Claude Code ao abrir `pense-precifique-backend/` (este arquivo
> está na raiz do projeto, mesmo nível do `pom.xml`).
> Atualizar a seção "Épicos implementados" a cada épico concluído.

---

## Stack

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3
- **Banco:** PostgreSQL 16 (Docker)
- **Autenticação:** JWT stateless
- **Migrations:** Flyway (`V1__`, `V2__`... em `resources/db/migration/`)
- **Documentação:** Springdoc OpenAPI (Swagger) — apenas em `dev`
- **Build:** Maven

---

## Ambientes

| Profile | Como subir | Swagger | Logs |
|---------|-----------|---------|------|
| `dev` | `docker-compose up` | ✅ `/swagger-ui.html` | DEBUG |
| `prod` | VPS (Skill 9) | ❌ desabilitado | INFO |

---

## Convenções obrigatórias

- **Chaves primárias:** UUID (`uuid_generate_v4()`)
- **Soft delete:** coluna `deleted_at` em todas as entidades — nunca `repository.delete()`
- **Tabelas/colunas:** `snake_case`
- **DTOs:** separados em `request/` e `response/` — nunca expor entidade diretamente
- **Service:** sempre interface + impl (`XxxService` + `XxxServiceImpl`)
- **Regras de negócio:** implementadas no `Service`, nunca no `Controller`
- **Mapper:** MapStruct — nunca converter manualmente dentro do Controller

---

## Estrutura de pacotes

```
com/penseprecifique/api/
├── config/          # SecurityConfig, CorsConfig, OpenApiConfig, JwtConfig
├── controller/      # um Controller por entidade
├── service/         # interface + impl/
├── repository/      # um Repository por entidade
├── domain/
│   ├── entity/      # entidades JPA
│   └── enums/       # TipoProduto, StatusOrcamento, TipoMovimentacao, etc.
├── dto/
│   ├── request/     # DTOs de entrada
│   └── response/    # DTOs de saída
├── mapper/          # MapStruct
├── security/        # JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── exception/       # GlobalExceptionHandler, exceções customizadas
```

---

## Modelo de Produto (v4 — unificado; erratas de UI na v6)

```
TipoProduto enum:
  PRODUTO       → tem preco_venda, aparece como item principal no orçamento
  PRODUTO_BASE  → sem preco_venda (constraint no banco), só componente em fichas técnicas
  CUSTOMIZACAO  → tem preco_venda, aparece como extra ao selecionar produto
```

**Constraint crítica no banco:**
```sql
CONSTRAINT chk_preco_venda_tipo
  CHECK (tipo = 'PRODUTO_BASE' OR preco_venda IS NOT NULL)
```

**Errata v6 — Cores de badge por tipo de produto** (estava invertida entre Produto e
Produto Base nas versões anteriores; usar helper compartilhado `src/utils/badges.ts` no front):

| Tipo | Cor do badge |
|------|-------------|
| Produto | **Azul** |
| Produto Base | **Cinza** |
| Customização | **Teal** |

---

## Status do Orçamento

```
RASCUNHO → ENVIADO → APROVADO
  → [AGUARDANDO_SINAL] → [SINAL_PAGO]   (só quando sinal_ativo = true)
  → EM_PRODUCAO → FINALIZADO → ENTREGUE → PAGO
  → [CANCELADO]  (disponível em todos os status, regras distintas por faixa)
```

**Novo v6 (RN-032, RN-033):**
- `prazo_producao_dias`, `inicio_assim_que_aprovado`, `data_inicio_estimada` são informados
  na criação do orçamento (Seção 3, Bloco B).
- `data_aprovacao` é preenchida automaticamente quando o status avança para `APROVADO`.

---

## PDFs gerados (PdfService)

| PDF | Trigger |
|-----|---------|
| Orçamento | Download manual na tela de preview |
| Recibo do sinal | Ao confirmar recebimento do sinal |
| PDF de multa | Ao cancelar em EM_PRODUCAO ou FINALIZADO com multa |
| Recibo de pagamento | Automaticamente ao avançar para PAGO |
| Recibo de estorno | Ao cancelar com status SINAL_PAGO e optar por devolver |

**Novo v6:** todos os PDFs (exceto o de orçamento, que sempre exibe prazo) passam a
exibir, na seção Datas: prazo de produção (dias úteis), início estimado (data ou
"Assim que aprovado") e data de aprovação (quando já preenchida).

---

## Endpoints principais

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /auth/login | Login |
| POST | /auth/register | Cadastro |
| GET/PUT | /usuarios/me | Dados do usuário logado / alterar senha |
| GET/PUT | /empresa | Perfil da empresa |
| GET/PUT | /configuracoes/precificacao | Configurações de preço |
| GET/POST | /clientes | Lista (paginada) e cria clientes |
| GET/PUT/DELETE | /clientes/{id} | Detalhe, edita, inativa |
| GET/POST | /insumos | Lista (paginada) e cria insumos |
| POST | /insumos/{id}/baixa-manual | Baixa manual de insumo (observação obrigatória mín. 50 chars — RN-035) |
| GET | /insumos/{id}/movimentacoes | Histórico do insumo (paginado) |
| POST | /lotes-compra | Registra compra de insumos em lote (RN-036) |
| GET/POST | /produtos | Lista (paginada) e cria produtos |
| GET/PUT | /produtos/{id} | Detalhe e edita |
| POST | /produtos/{id}/baixa-manual | Baixa manual de produto (observação obrigatória mín. 50 chars — RN-035) |
| GET | /produtos/{id}/movimentacoes | Histórico do produto (paginado) |
| GET/POST | /producoes | Lista (paginada) e lança produção |
| GET | /producoes/{id} | Detalhe da produção |
| POST | /producoes/{id}/cancelar | Cancela produção e reverte estoque (RN-037) |
| GET/POST | /orcamentos | Lista (paginada) e cria orçamentos |
| GET | /orcamentos/{id} | Detalhe do orçamento |
| POST | /orcamentos/{id}/avancar-status | Transição de status (registra data_aprovacao em APROVADO — RN-033) |
| POST | /orcamentos/{id}/cancelar | Cancela orçamento (wizard varia por status) |
| GET | /orcamentos/{id}/pdf | Download PDF do orçamento |
| GET | /orcamentos/{id}/recibo-sinal | Download recibo do sinal |
| GET | /orcamentos/{id}/recibo-pagamento | Download recibo completo |
| GET | /orcamentos/{id}/pdf-multa | Download PDF de multa |
| GET | /orcamentos/{id}/recibo-estorno | Download recibo de estorno do sinal |

---

## Paginação "Carregar mais" (RN-034)

Todas as listagens (Insumos, Produtos, Clientes, Orçamentos, Registro de Produção,
históricos de movimentação) retornam 20 itens por página, com cursor/offset para
"Carregar mais". Qualquer filtro, busca ou ordenação reseta para a primeira página.

---

## Padrão de commits

```
feat(escopo): descrição    → nova funcionalidade
fix(escopo): descrição     → correção de bug
test(escopo): descrição    → testes
chore(escopo): descrição   → configuração e infra

Exemplos:
feat(produto): adiciona ProdutoService com RN-021 e RN-022
feat(orcamento): implementa transição de status RASCUNHO → ENVIADO
test(producao): cenários BDD 56 e 57 — produção por tipo
```

---

## Documentos de referência

| Documento | Onde | Finalidade |
|-----------|------|-----------|
| `PRD_Pense_Precifique_v6.md` | `claude/` | Escopo, épicos, fluxos |
| `BUSINESS_RULES_Pense_Precifique_v6.md` | `claude/` | Entidades, RN-001 a RN-037 |
| `SCENARIOS_Pense_Precifique_v6.md` | `claude/` | 84 cenários BDD |
| `src/main/resources/db/migration/V1__initial_schema.sql` | raiz do projeto | Schema validado do banco |
| `claude/prompts/BACKLOG.md` | `claude/prompts/` | Índice vivo da implementação (Skill 7) |

---

## Épicos implementados

*(Atualizado pela Skill 7 a cada épico concluído)*

- [ ] Épico 0 — Setup do Projeto e Schema
- [ ] Épico 1 — Autenticação
- [ ] Épico 2 — Configurações
- [ ] Épico 3 — Insumos
- [ ] Épico 3.1 — Compras de Insumos em Lote (RN-036)
- [ ] Épico 4 — Produtos
- [ ] Épico 5 — Clientes
- [ ] Épico 6 — Registro de Produção (incl. cancelamento RN-037)
- [ ] Épico 7 — Orçamentos
- [ ] Épico 8 — Dashboard

---

## Decisões técnicas registradas

| Data | Decisão | Motivo |
|------|---------|--------|
| 2026-06-10 | JWT stateless | SaaS single-tenant, sem necessidade de OAuth social |
| 2026-06-10 | Organização por camada | Projeto de porte médio, preferência do time |
| 2026-06-10 | Produto unificado com enum tipo | Elimina entidade Customização separada |
| 2026-06-10 | Soft delete universal | Rastreabilidade de dados para o negócio da artesã |
| 2026-06-14 | Observação obrigatória (mín. 50 chars) para qualquer motivo de baixa manual (RN-035) | Substitui RN-024; rastreabilidade de ajustes manuais de estoque |
| 2026-06-14 | Compras de insumos centralizadas em lote via carrinho (RN-036) | Simplicidade de UX; recalcula custo unitário automaticamente |
| 2026-06-14 | Cancelamento de produção com reversão de estoque e estorno no histórico (RN-037) | Permite corrigir lançamentos errados sem editar histórico original |
| 2026-06-14 | Paginação "Carregar mais" (20 itens) em todas as listagens (RN-034) | Performance e consistência de UX |
| 2026-06-14 | Prazo de produção e data de aprovação automática no orçamento (RN-032, RN-033) | Visibilidade de prazos para a artesã e o cliente nos PDFs |
