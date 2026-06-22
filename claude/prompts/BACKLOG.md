# Backlog de Implementação — Pense & Precifique

> Gerado em: 2026-06-14 | Atualizado em: 2026-06-14 (renumeração após inserir scaffold inicial) | Total: 77 prompts | Concluídos: 2/77
> Baseado em: PRD v6, BUSINESS_RULES v6, SCENARIOS v6, schema_v6.sql

---

## Épico 0 — Setup do Projeto e Schema 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-001 | Scaffold do projeto Spring Boot (Maven, Java 21) + CLAUDE.md inicial | banco/back | ✅ concluído em 2026-06-14 |
| P-002 | Migration: V1__initial_schema (importar schema_v6.sql completo via Flyway) | banco | ✅ concluído em 2026-06-14 |

---

## Épico 1 — Autenticação 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-003 | Entity + Repository: Usuario | back | ⬜ pendente |
| P-004 | DTOs: Login/Cadastro/AlterarSenha Request, AuthResponse | back | ⬜ pendente |
| P-005 | Security: JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl | back | ⬜ pendente |
| P-006 | Service: AuthService (registro, login) | back | ⬜ pendente |
| P-007 | Controller: AuthController (POST /auth/login, /auth/register) | back | ⬜ pendente |
| P-008 | Service + Controller: UsuarioService/UsuarioController (GET /usuarios/me, PUT /usuarios/me/senha) | back | ⬜ pendente |
| P-009 | Front: conectar LoginPage e CadastroPage aos endpoints reais | front | ⬜ pendente |

---

## Épico 2 — Configuração do Perfil 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-010 | Entity + Repository: Empresa, ConfiguracaoPrecificacao | back | ✅ concluído |
| P-011 | DTOs: EmpresaRequest/Response, ConfiguracaoRequest/Response | back | ✅ concluído |
| P-012 | Service + Controller: EmpresaService/Controller (GET/PUT /empresa) | back | ✅ concluído |
| P-013 | Service + Controller: ConfiguracaoService/Controller (GET/PUT /configuracoes/precificacao) | back | ✅ concluído |
| P-014 | Front: conectar OnboardingPage e ConfiguracoesPage aos endpoints reais | front | ✅ concluído |

---

## Épico 3 — Insumos 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-015 | Entity + Repository: Insumo, MovimentacaoInsumo | back | ⬜ pendente |
| P-016 | DTOs: InsumoRequest/Response, MovimentacaoResponse | back | ⬜ pendente |
| P-017 | Mapper: InsumoMapper | back | ⬜ pendente |
| P-018 | Service: InsumoService — CRUD + RN de unicidade nome+marca | back | ⬜ pendente |
| P-019 | Controller: InsumoController — CRUD /insumos | back | ⬜ pendente |
| P-020 | Service + Controller: baixa manual de insumo (RN-035 — observação obrigatória mín. 50 chars, qualquer motivo) | back | ⬜ pendente |
| P-021 | Service + Controller: histórico de movimentações de insumo paginado (RN-034) | back | ⬜ pendente |
| P-022 | Front: conectar ListaInsumosPage (paginação "Carregar mais") | front | ⬜ pendente |
| P-023 | Front: conectar FormInsumoPage (cadastrar/editar) | front | ⬜ pendente |
| P-024 | Front: conectar DetalheInsumoPage + modal de baixa manual (RN-035, sem botão de compra individual — errata v6) | front | ⬜ pendente |

---

## Épico 3.1 — Compras de Insumos em Lote (RN-036, UC-019) 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-025 | Entity + Repository: LoteCompra | back | ⬜ pendente |
| P-026 | DTOs: RegistrarLoteCompraRequest, ImpactoAgregadoResponse | back | ⬜ pendente |
| P-027 | Service: LoteCompraService — recalcula custo unitário, atualiza estoque, registra movimentações por insumo, calcula impacto agregado (RN-036) | back | ⬜ pendente |
| P-028 | Controller: LoteCompraController (POST /lotes-compra) | back | ⬜ pendente |
| P-029 | Front: modal "Carrinho de compras" na ListaInsumosPage — adicionar múltiplos insumos, confirmar lote, exibir modal de impacto agregado (Cenário 79, 80) | front | ⬜ pendente |

---

## Épico 4 — Produtos 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-030 | Entity + Repository: Produto, FichaTecnicaItem, MovimentacaoProduto | back | ⬜ pendente |
| P-031 | DTOs: ProdutoRequest/Response/Detalhe, FichaTecnicaItemRequest | back | ⬜ pendente |
| P-032 | Mapper: ProdutoMapper | back | ⬜ pendente |
| P-033 | Service: FichaTecnicaService — cálculo de preco_custo a partir dos componentes | back | ⬜ pendente |
| P-034 | Service: ProdutoService — CRUD + constraint chk_preco_venda_tipo (PRODUTO_BASE sem preço) | back | ⬜ pendente |
| P-035 | Controller: ProdutoController — CRUD /produtos | back | ⬜ pendente |
| P-036 | Service + Controller: baixa manual de produto (RN-035 — observação obrigatória mín. 50 chars, qualquer motivo) | back | ⬜ pendente |
| P-037 | Service + Controller: histórico de movimentações de produto paginado (RN-034) | back | ⬜ pendente |
| P-038 | Front: conectar ListaProdutosPage (badges por tipo — cores corrigidas: Produto=Azul, Produto Base=Cinza, Customização=Teal — errata v6, usar helper badges.ts) | front | ⬜ pendente |
| P-039 | Front: conectar CadastrarProdutoPage e EditarProdutoPage (incl. ficha técnica) | front | ⬜ pendente |
| P-040 | Front: conectar DetalheProdutoPage + modal de baixa manual (RN-035) + histórico paginado | front | ⬜ pendente |

---

## Épico 5 — Clientes 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-041 | Entity + Repository: Cliente | back | ⬜ pendente |
| P-042 | DTOs: ClienteRequest/Response | back | ⬜ pendente |
| P-043 | Mapper: ClienteMapper | back | ⬜ pendente |
| P-044 | Service: ClienteService — CRUD + soft delete (inativar) | back | ⬜ pendente |
| P-045 | Controller: ClienteController — CRUD /clientes (com paginação RN-034) | back | ⬜ pendente |
| P-046 | Front: conectar ClientesPage (lista paginada + form de cadastro/edição/inativação) | front | ⬜ pendente |

---

## Épico 6 — Registro de Produção 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-047 | Entity + Repository: Producao, ProducaoInsumoConsumido | back | ⬜ pendente |
| P-048 | DTOs: ProducaoRequest/Response/Detalhe | back | ⬜ pendente |
| P-049 | Mapper: ProducaoMapper | back | ⬜ pendente |
| P-050 | Service: ProducaoService — lançar produção (baixa insumos da ficha técnica + soma estoque do item produzido) | back | ⬜ pendente |
| P-051 | Controller: ProducaoController — POST/GET /producoes, GET /producoes/{id} (com paginação RN-034) | back | ⬜ pendente |
| P-052 | Service: cancelamento de produção (RN-037, UC-020) — reverte estoque produto+insumos, gera movimentações ESTORNO_PRODUCAO, marca originais como estornada=true, seta status CANCELADA | back | ⬜ pendente |
| P-053 | Controller: POST /producoes/{id}/cancelar (RN-037) | back | ⬜ pendente |
| P-054 | Front: conectar RegistroProducaoPage — modal lançar produção (toggle Produto/Produto Base/Customização, indicador de suficiência de insumos) | front | ⬜ pendente |
| P-055 | Front: ActionMenu ⋮ na lista de produções com "Cancelar produção" (modal com resumo + observação obrigatória mín. 50 chars — Cenário 81-84; menu disponível apenas na lista, não no detalhe) | front | ⬜ pendente |

---

## Épico 7 — Orçamentos 🔴 fluxo crítico

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-056 | Entity + Repository: Orcamento, OrcamentoItem, OrcamentoItemCustomizacao | back | ⬜ pendente |
| P-057 | Entity + Repository: ReciboPagamento, ReciboEstorno | back | ⬜ pendente |
| P-058 | DTOs: OrcamentoRequest/Response/Detalhe, AvancaStatusRequest, ReciboPagamentoResponse | back | ⬜ pendente |
| P-059 | Mapper: OrcamentoMapper | back | ⬜ pendente |
| P-060 | Service: OrcamentoService — criar orçamento (itens, customizações, prazo de produção RN-032, sinal) | back | ⬜ pendente |
| P-061 | Controller: OrcamentoController — POST/GET /orcamentos, GET /orcamentos/{id} (com paginação RN-034) | back | ⬜ pendente |
| P-062 | Service: transição de status (RASCUNHO→ENVIADO→APROVADO, registra data_aprovacao automaticamente RN-033) | back | ⬜ pendente |
| P-063 | Service: transição para AGUARDANDO_SINAL/SINAL_PAGO — confirmação de recebimento do sinal | back | ⬜ pendente |
| P-064 | Service: transição EM_PRODUCAO→FINALIZADO (baixa estoque dos produtos do orçamento) → ENTREGUE → PAGO (gera recibo de pagamento) | back | ⬜ pendente |
| P-065 | Controller: POST /orcamentos/{id}/avancar-status | back | ⬜ pendente |
| P-066 | Service: cancelamento de orçamento — tabela completa por status (Rascunho/Enviado/Aprovado/Aguardando Sinal: simples; Sinal Pago: wizard 2 passos + estorno; Em Produção/Finalizado: wizard 3 passos + multa; Entregue/Pago: justificativa mín. 50 chars) | back | ⬜ pendente |
| P-067 | Controller: POST /orcamentos/{id}/cancelar | back | ⬜ pendente |
| P-068 | Service + Controller: PdfService — PDF do orçamento (método pagamento + prazo de produção + datas + cláusula cancelamento + sinal) | back | ⬜ pendente |
| P-069 | Service + Controller: PdfService — recibo do sinal (método recebido + prazo + data de aprovação) | back | ⬜ pendente |
| P-070 | Service + Controller: PdfService — PDF de multa e recibo de estorno do sinal | back | ⬜ pendente |
| P-071 | Service + Controller: PdfService — recibo de pagamento completo (prazo + data de aprovação) | back | ⬜ pendente |
| P-072 | Front: conectar CriarOrcamentoPage — Seção 3 (Bloco A pagamento, Bloco B prazo RN-032/Cenário 71-73, Bloco C sinal) + customizações | front | ⬜ pendente |
| P-073 | Front: conectar PreviewPdfPage (download PDF do orçamento) | front | ⬜ pendente |
| P-074 | Front: conectar DetalheOrcamentoPage — transições de status + wizards de cancelamento por status | front | ⬜ pendente |
| P-075 | Front: conectar ListaOrcamentosPage (paginação "Carregar mais" RN-034, reset ao filtrar — Cenário 75-76) | front | ⬜ pendente |
| P-076 | Front: conectar PreviewMultaPage, ReciboSinalPage, ReciboPagamentoPage | front | ⬜ pendente |

---

## Épico 8 — Dashboard 🟡 secundário

| ID | Tarefa | Camada | Status |
|----|--------|--------|--------|
| P-077 | Service + Controller + Front: DashboardPage — indicadores gerais (orçamentos por status, alertas de estoque mínimo, produção recente) | back+front | ⬜ pendente |

---

## Resumo

- 🔴 Épico 0 — Setup do Projeto e Schema: 2/2 concluídos
- 🔴 Épico 1 — Autenticação: 7/7 concluídos ✅ (validado em 2026-06-14 — ver VALIDACAO_EPICO_1.md)
- 🔴 Épico 2 — Configuração do Perfil: 5/5 concluídos ✅
- 🔴 Épico 3 — Insumos: 0/10 concluídos
- 🔴 Épico 3.1 — Compras em Lote (RN-036): 0/5 concluídos
- 🔴 Épico 4 — Produtos: 0/11 concluídos
- 🔴 Épico 5 — Clientes: 0/6 concluídos
- 🔴 Épico 6 — Registro de Produção: 0/9 concluídos
- 🔴 Épico 7 — Orçamentos: 0/21 concluídos
- 🟡 Épico 8 — Dashboard: 0/1 concluído

---

## Observações de escopo (v6)

- RN-035 (observação obrigatória mín. 50 chars para qualquer motivo de baixa manual) deve ser aplicada em P-020 (insumo) e P-036 (produto), substituindo o comportamento anterior da RN-024.
- RN-034 (paginação "Carregar mais", 20 itens, reset ao filtrar) é transversal — aplicada em P-021, P-022, P-037, P-040, P-045, P-046, P-051, P-054, P-061, P-075.
- RN-036 (compras em lote) substitui qualquer fluxo de compra individual — não recriar botão de compra individual no Detalhe do Insumo (P-024).
- RN-037 (cancelamento de produção) é um épico funcional novo (6.1), mas mantido dentro do Épico 6 no backlog por dependência direta de Producao/MovimentacaoInsumo/MovimentacaoProduto.
- Errata de cores de badge (Produto=Azul, Produto Base=Cinza, Customização=Teal) deve usar helper compartilhado `src/utils/badges.ts` (P-038).

---

## Histórico de renumeração

- 2026-06-14: inserido P-001 (scaffold do projeto Spring Boot + CLAUDE.md), pois a pasta `pense-precifique-backend/` estava vazia (sem `pom.xml`/`src/`). Todos os IDs do backlog original foram deslocados em +1 (antigo P-001 → P-002, antigo P-002 → P-003, e assim por diante até antigo P-076 → P-077).
