# Pense & Precifique — Contexto do Back-End

> **v0** — Lido automaticamente pelo Claude Code ao abrir `pense-precifique-backend/`.
> Projeto pré-produção. Primeiro deploy estável com usuários reais = v1.
> Caminho do projeto: `/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend`

---

## Stack

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.3.5
- **Banco:** PostgreSQL 16 (Docker)
- **Autenticação:** JWT stateless (HS512)
- **Migrations:** Flyway — V1__, V2__ em `resources/db/migration/`
- **PDF:** OpenHTMLToPDF + Thymeleaf (PdfMapper pattern)
- **Documentação:** Springdoc OpenAPI (Swagger) — apenas em `dev`
- **Build:** Maven (`./mvnw`)

---

## Como subir

```bash
cd "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique"
docker compose up --build
```

| Profile | Swagger | Logs |
|---------|---------|------|
| `dev` | ✅ `/swagger-ui.html` | DEBUG |
| `prod` | ❌ desabilitado | INFO |

**Conta de teste:** `penseprecifique@admin.com` / `senha12345`

---

## Convenções obrigatórias

- **PKs:** UUID
- **Soft delete:** coluna `deleted_at` — nunca `repository.delete()`
- **Tabelas/colunas:** `snake_case`
- **DTOs:** `dto/request/` e `dto/response/` — nunca expor entidade
- **Service:** classe concreta com `@Service` (a partir do Épico 6 — sem interface+impl)
- **Regras de negócio:** no Service, nunca no Controller
- **Mapper:** classe concreta `@Component` com setters manuais — **não MapStruct**, apesar de versão anterior deste documento dizer o contrário. Confirmado por investigação em 2026-07-05 (execução do P-004/EP-04): nenhum `@Mapping`/`org.mapstruct` no projeto, todos os Mappers existentes seguem esse padrão manual. Corrigido aqui pra próximos prompts não inventarem convenção nova no meio do projeto.
- **usuarioId:** sempre via `SecurityContextHolder` — nunca receber no body
- Toda correção de bug ou tarefa de tech debt termina com commit + push antes de considerar o chat encerrado — mesmo sem fechamento de épico. Branch sempre `main`: `git push origin main`. Nunca deixar mudança sem commit entre chats.
---

## Estrutura de pacotes

```
com/penseprecifique/api/
├── config/           # SecurityConfig, CorsConfig, OpenApiConfig, JwtConfig
├── controller/       # um Controller por entidade
├── service/          # classes concretas @Service
├── repository/       # um Repository por entidade
├── domain/
│   ├── entity/       # entidades JPA
│   └── enums/        # TipoProduto, StatusOrcamento, MetodoPagamento, etc.
├── dto/
│   ├── request/      # DTOs de entrada
│   ├── response/     # DTOs de saída
│   └── pdf/          # OrcamentoPdfData, ItemPdfData, ReciboPdfData, ReciboPagamentoPdfData
├── mapper/           # MapStruct + PdfMapper
├── security/         # JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── exception/        # GlobalExceptionHandler, ResourceNotFoundException, BusinessException
```

---

## Modelo de Produto

```
TipoProduto enum:
  PRODUTO       → tem preco_venda, item principal no orçamento
  PRODUTO_BASE  → sem preco_venda, só componente em fichas técnicas
  CUSTOMIZACAO  → tem preco_venda, extra ao selecionar produto
```

**Constraint no banco:**
```sql
CONSTRAINT chk_preco_venda_tipo
  CHECK (tipo = 'PRODUTO_BASE' OR preco_venda IS NOT NULL)
```

---

## Status do Orçamento

```
RASCUNHO → ENVIADO → APROVADO
  → [AGUARDANDO_SINAL] → [SINAL_PAGO]   (só quando sinal_ativo = true)
  → EM_PRODUCAO → FINALIZADO → ENTREGUE → PAGO
  → [CANCELADO]  (disponível em todos os status)
```

`data_aprovacao` é preenchida automaticamente quando status → APROVADO (RN-033).

---

## PdfMapper Pattern (CRÍTICO)

Toda formatação de dados para PDF acontece no Java — templates são "burros".

```java
// PdfService chama:
ctx.setVariable("dados", pdfMapper.toOrcamentoPdfData(orc, empresa, cliente));

// Template só usa:
${dados.nomeCliente}
${dados.numeroFormatado}
${dados.total}
```

**Nunca** usar SpEL complexo, `T(String)`, `#dates`, `padStart` ou navegação em relacionamentos nos templates Thymeleaf. Qualquer lógica vai no PdfMapper.

---

## Endpoints implementados

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /auth/login | Login |
| POST | /auth/register | Cadastro |
| GET/PUT | /usuarios/me | Dados do usuário / alterar senha |
| GET/PUT | /empresa | Perfil da empresa |
| GET/PUT | /configuracoes/precificacao | Configurações de precificação |
| GET/POST | /clientes | Lista paginada e cria |
| GET/PUT/DELETE | /clientes/{id} | Detalhe, edita, inativa |
| GET/POST | /insumos | Lista paginada e cria |
| POST | /insumos/{id}/baixa-manual | Baixa manual (obs mín. 50 chars — RN-035) |
| GET | /insumos/{id}/movimentacoes | Histórico paginado |
| GET | /insumos/{id}/produtos-relacionados | Lista produtos cuja ficha técnica usa o insumo |
| POST | /lotes-compra | Registra compra em lote (RN-036) |
| GET/POST | /produtos | Lista paginada e cria |
| GET/PUT | /produtos/{id} | Detalhe e edita |
| POST | /produtos/{id}/baixa-manual | Baixa manual (obs mín. 50 chars — RN-035) |
| GET | /produtos/{id}/movimentacoes | Histórico paginado |
| GET/POST | /producoes | Lista paginada e lança produção |
| GET | /producoes/{id} | Detalhe |
| POST | /producoes/{id}/cancelar | Cancela + reverte estoque (RN-037) |
| GET/POST | /orcamentos | Lista paginada e cria |
| GET | /orcamentos/{id} | Detalhe |
| POST | /orcamentos/{id}/avancar-status | Transição de status |
| POST | /orcamentos/{id}/cancelar | Cancela (wizard por status) |
| GET | /orcamentos/{id}/pdf | PDF do orçamento |
| GET | /orcamentos/{id}/recibo-sinal | Recibo do sinal |
| GET | /orcamentos/{id}/recibo-pagamento | Recibo de pagamento |
| GET | /orcamentos/{id}/pdf-multa | PDF de multa |
| GET | /orcamentos/{id}/recibo-estorno | Recibo de estorno do sinal |
| GET | /dashboard | Resumo consolidado da conta |

---

## Épicos implementados ✅

- [x] Épico 0 — Setup do Projeto e Schema
- [x] Épico 1 — Autenticação
- [x] Épico 2 — Configurações
- [x] Épico 3 — Insumos
- [x] Épico 3.1 — Compras em Lote (RN-036)
- [x] Épico 4 — Produtos
- [x] Épico 5 — Clientes
- [x] Épico 6 — Registro de Produção (incl. cancelamento RN-037)
- [x] Épico 7 — Orçamentos (incl. todos os PDFs)
- [x] Épico 8 — Dashboard

---

## Migrations ativas

| Versão | Arquivo | Descrição |
|--------|---------|-----------|
| V1 | V1__initial_schema.sql | Schema completo |
| V2 | V2__allow_null_insumo_id_producao_consumidos.sql | Permite insumo_id null |

---

## Aprendizados críticos

| Regra | Contexto |
|-------|----------|
| Serialização JSON é camelCase (default Spring/Jackson) — nunca configurar snake_case | Confirmado via investigação: todos os DTOs (Dashboard, Produtos, Insumos, Clientes) já saem consistentes em camelCase sem `@JsonNaming`. Manter assim — qualquer mudança para snake_case quebra o frontend inteiro silenciosamente. |
| DTO de resposta precisa expor todos os campos persistidos | `OrcamentoDetalheResponse` não incluía `percentualMulta`, `cancelamentoTipo`, `estornoSinal`, `dataEstornoSinal` — campos persistiam no banco mas a API retornava `null`, causando falha silenciosa no frontend (botão "PDF de multa" nunca aparecia). Ao adicionar campo a entidade JPA, verificar DTO de resposta e mapper imediatamente. |
| Regra de negócio replicada em múltiplos fluxos precisa cobertura explícita em cada um | RN-006 (insumo fracionável) estava coberta em baixa manual e compra em lote, mas não em `FichaTecnicaItem` — regra existir em um lugar não garante que foi aplicada em todos os pontos de entrada. |

---

## Padrão de commits

```
feat(escopo): nova funcionalidade
fix(escopo): correção de bug
refactor(escopo): refatoração
chore(escopo): configuração/infra
```

