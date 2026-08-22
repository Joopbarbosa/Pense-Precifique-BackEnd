# Pense & Precifique — Contexto do Back-End

> **V0.8** — Lido automaticamente pelo Claude Code ao abrir `pense-precifique-backend/`.
> Projeto pré-produção. Primeiro deploy estável com usuários reais = v1.
> Caminho do projeto: `/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend`
> Atualizado em: 2026-07-20 — Retomada de fechamento V0.6: varredura de resíduos do fluxo antigo de Produção (nenhum encontrado em código, exceto coluna/campo órfão `data_producao`/`dataProducao` — ver Bugs conhecidos), ciclo de vida completo documentado (6 estados, transições, agrupamento/divisão), contrato de `consumoReal`, RN-069 (PDC-010), race condition conhecida do número sequencial, RN-037 (PDC-021)/RN-060 (PDC-001) marcadas obsoletas.
> Atualizado em: 2026-07-31 — Retomada de fechamento V0.6.1.1 (pocket de limpeza): endpoints novos/alterados documentados (`simular-alertas` de Produção e Orçamento, `contagens`/`inativar`/`reativar` de Produto, filtro de data em Orçamento), RN-051 (PDC-005) corrigida no endpoint de Produção (conceito de `lotes` removido), decisão de fluxo sem branch por tarefa, aprendizado sobre confirmar payload real antes de confiar em nota de backlog "decisão registrada".
> Atualizado em: 2026-08-05 — Reorganização estrutural pura de `shared/dto/request/` e `shared/dto/response/` por módulo de domínio (achado do usuário: 30+ arquivos numa pasta linear só alfabética). Sem mudança de payload/endpoint/comportamento — ver seção "Estrutura de pacotes".
> Atualizado em: 2026-08-10 — Retomada de fechamento V0.7: `PRODUTO_BASE` eliminado (migration V28, PDT-001), múltiplos do rendimento em Produção (PDC-027, ex-PDC-005), Catálogo perde margem própria e herda `preco_venda` do Produto (migration V29, CAT-003), inativação reversível de Insumo (INS-010/011) e padrão `resolver-vinculos` por blocos independentes em Produto/Insumo (PDT-013/014, INS-012). GitFlow por versão retomado (ver "Padrão de commits"/regra abaixo). `docs-pense-precifique/` passou a ser um repositório git próprio nesta versão — ver "Documentação externa".
> Atualizado em: 2026-08-14 — Retomada de fechamento V0.8: épico #89 consolidado — `PdfService`
> delega geração de PDF/preview de Orçamento ao microsserviço externo `pense-precifique-pdf` via
> `PdfMicroservicoClient` (novo endpoint `GET /orcamentos/{id}/preview-html`), arquitetura padrão
> de geração de documento a partir de agora (os outros 4 tipos continuam locais até migração
> própria — ver "Stack"). Corrigido no caminho: `PdfMapper.mapearItens()` não resolvia produto
> avulso/customizações reais (bug urgente, achado no Passo 0 do épico). #217 (busca de item de
> catálogo server-side, `?busca=` em `GET /orcamentos/itens-catalogo`) e #218 (bloqueio de
> `POST /orcamentos` por estoque insuficiente + DTO dedicado `SimulacaoEstoqueProdutoResponse`)
> fecham o pocket. Dependência nova de infraestrutura (backend → serviço externo via HTTP)
> registrada em `docs-pense-precifique/MAPA_INTERDEPENDENCIAS.md`.

---

## Stack

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.3.5
- **Banco:** PostgreSQL 16 (Docker)
- **Autenticação:** JWT stateless (HS512)
- **Migrations:** Flyway — `resources/db/migration/`, até V16 (tabela completa abaixo, todos os nomes confirmados via `ls`)
- **PDF:** híbrido desde V0.8 (épico #89) — **Orçamento** delega ao microsserviço externo
  `pense-precifique-pdf` (Node/Express/React SSR/Puppeteer, `PdfMicroservicoClient`, ver
  `docs-pense-precifique/modulos/PDF/`), arquitetura padrão para geração de documento daqui pra
  frente. Os outros 4 tipos (recibo-sinal, recibo-pagamento, pdf-multa, recibo-estorno) continuam
  em OpenHTMLToPDF + Thymeleaf local (`PdfMapper` pattern) até migração própria, pós-gate de
  aprovação do MVP.
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

## Como rodar os testes

Testes sempre rodam dentro de container Docker, nunca no host direto — `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` só existem como env var dentro dos containers do `docker-compose.yml`, então `./mvnw test` no host falha ao subir o `ApplicationContext` com `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${SPRING_DATASOURCE_URL}`.

O container `backend` em execução (`docker compose up`) é a imagem final de produção do Dockerfile multi-stage (`FROM eclipse-temurin:21-jre`, só tem `app.jar`) — não tem Maven nem código-fonte, então `docker compose exec backend ./mvnw test` **não funciona**. É preciso buildar o estágio `build` (`FROM eclipse-temurin:21-jdk AS build`) à parte e rodar os testes num container temporário, na mesma rede do `db`:

```bash
cd "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend"
docker build --target build -t pense-backend-build .

# garantir que o serviço "db" do docker-compose está de pé antes (docker compose up -d db)
source "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/.env"
docker run --rm --network penseprecifique_default \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/pense_precifique_db" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e JWT_EXPIRATION_MS="$JWT_EXPIRATION_MS" \
  pense-backend-build ./mvnw test
```

Rede confirmada via `docker network ls` (`penseprecifique_default`, nome derivado do diretório raiz do `docker-compose.yml`). Suíte usa `**/*IT.java` (convenção do projeto, Surefire configurado para isso desde #132/Onda 3) — nunca rodar por classe isolada (`-Dtest=Nome`) como validação final, sempre `./mvnw test` completo.

---

## Convenções obrigatórias

- **PKs:** UUID
- **Soft delete:** coluna `deleted_at` — nunca `repository.delete()`
- **Tabelas/colunas:** `snake_case`
- **DTOs:** `dto/request/` e `dto/response/` — nunca expor entidade
- **Service:** classe concreta com `@Service` (a partir do Épico 6 — sem interface+impl)
- **Regras de negócio:** no Service, nunca no Controller
- **Mapper:** classe concreta `@Component` com setters manuais — **não MapStruct**. Confirmado por investigação em 2026-07-05 (P-004/EP-04) e reconfirmado no bloco Catálogo inteiro (P-010, P-011): nenhum `@Mapping`/`org.mapstruct` no projeto.
- **usuarioId:** sempre via `SecurityContextHolder` — nunca receber no body
- **FK entre entidades:** `@ManyToOne` + `@JoinColumn` (objeto de relação), nunca UUID cru — confirmado em `Produto`/`Producao`/`ItemCatalogo` (bloco Catálogo).
- **Numero sequencial:** sempre `INTEGER` + lógica de Service `ORDER BY numero DESC`/`findTopByUsuarioIdOrderByNumeroDesc` — nunca `SERIAL`, nunca ordenar por campo de data mutável (bug corrigido em `ProducaoService`, P-024.1). `UNIQUE(usuario_id, numero)` sempre que a tabela for tocada.
- **Padrão calculado + override:** campo calculado nunca persistido (`precoSugerido`, `custoTotalLote`), campo persistido + flag `override` booleana quando há edição manual que trava recálculo automático — mas mudança de **custo** nunca recalcula o preço automaticamente, mesmo sem override, só o campo "sugerido" exibido como referência. Usado em: Produto (RN-038a (PDT-005) — **desde V0.7 (PDT-001), vale para os 2 tipos `PRODUTO`/`CUSTOMIZACAO`, não só `CUSTOMIZACAO`**), ItemCatalogo (CAT-003 — **reescrita em V0.7/#239: sem margem própria de Catálogo, herda `precoVenda` já calculado do Produto/customizações anexadas**). O item avulso do Orçamento (RN-054 (ORC-020)) usa uma versão simplificada — sem margem viva, é snapshot único no momento da adição, não relação contínua.
- **Padrão "resolver vínculos" por blocos independentes (V0.7, Produto e Insumo):** quando exclusão/inativação é bloqueada por vínculo em uso, `POST /{id}/resolver-vinculos` resolve por bloco de tipo de vínculo (Produto: `catalogo`/`componente`; Insumo: só ficha técnica), cada bloco com a própria `acao` (`REMOVER_VINCULOS`/`SUBSTITUIR`), numa única transação atômica que já executa a operação original (inativar/excluir) na mesma chamada. Ver `ProdutoService#resolverVinculos`/`InsumoService#resolverVinculos`. Candidato a padrão de referência para módulos futuros com bloqueio de exclusão/inativação por vínculo.
- **XOR entre duas origens/campos:** modelar como CHECK constraint no banco + validação explícita no Service com `BusinessException` clara distinguindo "os dois preenchidos" de "nenhum preenchido" — nunca uma mensagem genérica única pros dois casos. Usado em: `LancarProducaoRequest` (quantidade XOR lotes, RN-051 (PDC-005)), `OrcamentoItem` (item_catalogo_id XOR produto_id, RN-054 (ORC-020)).
- **Exceção:** `BusinessException` genérica com mensagem — não criar tipos novos.
- Toda correção de bug ou tarefa de tech debt termina com commit + push antes de considerar o chat encerrado — mesmo sem fechamento de épico. Nunca deixar mudança sem commit entre chats.
- **GitFlow por versão, retomado em V0.7 (2026-08-06/07):** trabalho de uma versão (`V[X.Y]`) vai para a branch `feature/V[X.Y]`, criada no início da fase de Backend/Frontend da versão — não mais commit direto em `main`. PR de merge para `main` só no fechamento formal da versão (após a Retomada). **Substitui a decisão de fluxo de 2026-07-29** (linha abaixo, V0.6.1.1) — essa prática vigorou entre V0.6.1.1 e V0.6.3, revertida a partir de V0.7. Branches de feature para trabalho maior já existiam antes (ex. módulos novos), mas agora toda versão usa branch própria, mesmo correções/débitos pontuais dentro dela.
- **Decisão de fluxo (2026-07-29):** esta leva (V0.6.1.1) não usa branch por tarefa — todo trabalho vai direto em `main`, sem staging separado. Branches de feature seguem existindo para trabalho maior (ex. módulos novos da V0.7), mas correções/débitos técnicos pontuais não abrem branch própria.
- **Rastreamento migrou de ClickUp para OpenProject.** Se a tarefa tem número no OpenProject (ex. `#93`), a mensagem de commit começa com o número. Padrão real confirmado em `git log --oneline -15`: `#N tipo(escopo): descrição` (ex. `#93 feat: adicionar busca por cliente em GET /orcamentos e prefixo ORC-N no mapper`) — número como **prefixo**, sem a palavra "OpenProject" no corpo. Commits antigos com `ClickUp <código> / <task-id>` são histórico, não o padrão atual.
- **Todo prompt segue a estrutura fixa de `PADRAO_PROMPTS.md`** (ambiente, comando de commit pronto, conta de teste, checklist de validação) — decidido em 2026-07-09. Arquivo confirmado em `/home/joaobarbosa/Documentos/Projetos/Pense Software/Skills/PADRAO_PROMPTS.md` (fora deste projeto, pasta irmã "Pense Software").

---

## Estrutura de pacotes (refactor V0.5 — #56 a #67)

Pacote flat antigo (`controller/`, `service/`, `repository/`, `config/`, `domain/`, `dto/`, `mapper/`, `security/`, `exception/` na raiz de `api/`) foi **extinto**. Estrutura atual é por módulo de domínio, Controller+Service+Repository juntos na mesma pasta:

```
com/penseprecifique/api/
├── auth/             # AuthController, AuthService(+Impl), UsuarioController, UsuarioService(+Impl), UsuarioRepository
├── catalogo/          # CatalogoController, ItemCatalogoController, CatalogoService, ItemCatalogoService, repositories
├── cliente/           # ClienteController, ClienteService(+Impl), ClienteRepository
├── dashboard/         # DashboardController, DashboardService
├── empresa/           # EmpresaController, ConfiguracaoController, services(+Impl), repositories
├── insumo/            # InsumoController, LoteCompraController, InsumoService, LoteCompraService (classe única, migrado no #134/Onda 3), repositories (inclui MovimentacaoInsumoRepository)
├── orcamento/         # OrcamentoController, OrcamentoService, OrcamentoRepository + repositories de item/recibo
├── producao/          # ProducaoController, ProducaoService, ProducaoRepository, ProducaoInsumoConsumidoRepository
├── produto/           # ProdutoController, ProdutoService, FichaTecnicaService, repositories
├── pdf/               # PdfService, PdfMapper (pattern inalterado, ver seção própria)
├── shared/
│   ├── domain/
│   │   ├── entity/       # entidades JPA
│   │   ├── enums/        # TipoProduto, StatusOrcamento, MetodoPagamento, etc.
│   │   └── converter/
│   ├── dto/
│   │   ├── request/      # DTOs de entrada, por módulo de domínio (reorg 2026-08-05)
│   │   │   ├── auth/         # login, cadastro, alterar senha
│   │   │   ├── config/       # empresa, configuração de precificação (nome de pasta não espelha o pacote `empresa/`)
│   │   │   ├── catalogo/ cliente/ insumo/ orcamento/ producao/ produto/  # espelham os módulos de domínio abaixo
│   │   ├── response/     # DTOs de saída, mesma divisão por módulo de domínio, mais:
│   │   │   └── (raiz, sem subpasta)  # ErrorResponseDTO, AvisoEstoqueNegativoResponse, ConfirmacaoEstoqueNegativoResponse
│   │   │                             # — cross-cutting: ErrorResponseDTO é infra de exceção (GlobalExceptionHandler);
│   │   │                             # os outros dois são usados de forma simétrica por orcamento/ e producao/ (RN-059, PDC-023/ORC-027),
│   │   │                             # sem mapper "dono" que justifique escolher um módulo
│   │   └── pdf/          # OrcamentoPdfData, ItemPdfData, ReciboPdfData, ReciboPagamentoPdfData
│   ├── mapper/           # @Component manual (NÃO MapStruct, apesar do nome do pacote)
│   └── exception/        # GlobalExceptionHandler, ResourceNotFoundException, BusinessException, UnauthorizedException
├── infra/
│   ├── config/           # SecurityConfig
│   └── security/         # JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── util/              # IdentificadorFormatter (RN-053 (ver `DECISOES_GLOBAIS.md`), ORC-N/INS-N/etc.), NumeroSequencialUtil — utilitários globais sem módulo dono
```

`service/impl/`, `controller/` e `repository/` na raiz de `api/` estão vazios, sobra local do refactor, não versionados no git. `service/PdfHelper.java` (única exceção intencional documentada até então) foi removido no #132 (V0.6.3, Onda 3) — confirmado órfão de uso, sem consumidor no código.

---

## Modelo de Produto (atualizado — bloco Catálogo)

```
TipoProduto enum:
  PRODUTO       → NÃO tem mais preco_venda próprio (vem do Catálogo, ou de margem_aplicada
                  ad-hoc quando vendido avulso no orçamento — RN-054 (ORC-020)). Tem rendimento,
                  custoTotalLote, custoUnitario (calculados, RN-039 (PDT-006)).
  PRODUTO_BASE  → sem preco_venda, só componente em fichas técnicas
  CUSTOMIZACAO  → tem preco_venda + margem_lucro (recriada nesta versão), com padrão
                  calculado+override (RN-038a (PDT-005)). Extra ao selecionar produto/item de catálogo.
```

**Constraint atualizada (bloco Catálogo):**
```sql
CHECK (tipo <> 'CUSTOMIZACAO' OR preco_venda IS NOT NULL)
-- PRODUTO e PRODUTO_BASE não exigem mais preco_venda no banco
```

**Novos campos em Produto:** `rendimento` (obrigatório se tem ficha técnica), `algum_insumo_nao_fracionavel` (calculado, exposto pra decidir UI de Produção).

---

## Status do Orçamento

```
RASCUNHO → ENVIADO → APROVADO
  → [AGUARDANDO_SINAL] → [SINAL_PAGO]   (só quando sinal_ativo = true)
  → EM_PRODUCAO → FINALIZADO → ENTREGUE → PAGO
  → [CANCELADO]  (disponível em todos os status)
```

`data_aprovacao` é preenchida automaticamente quando status → APROVADO (RN-033 (ORC-019)).

**Baixa de estoque acontece na transição EM_PRODUCAO → FINALIZADO**, não antes — se for testar via curl, avançar status até chegar lá antes de checar movimentação.

---

## OrcamentoItem (atualizado — bloco Catálogo + RN-054 (ORC-020))

`OrcamentoItem` aceita duas origens, **XOR** (constraint `chk_orcamento_item_origem_xor`):
- `item_catalogo_id` (FK → itens_catalogo) — fluxo padrão, preço vem do Catálogo (RN-048 (CAT-009))
- `produto_id` + `margem_aplicada` (RN-054 (ORC-020), venda avulsa) — preço calculado via `GET /produtos/{id}/preco-sugerido?margem=X`, snapshot gravado em `preco_unitario`, sem margem viva depois

`MovimentacaoProduto.catalogo_referencia` (quando `motivo = ORCAMENTO`): `CTG-N` na origem Catálogo, `"{PRO-N} - Venda sem catálogo"` na origem avulsa — nunca fica nulo.

---

## Módulo de Produção (V0.6)

Modelo novo: uma `Producao` agrupa **N produtos** (`ProducaoProduto`, um por produto do lote — nunca mais 1 produção = 1 produto). Toda transição de estado grava uma linha em `HistoricoStatusProducao` (`estado`, `origem` `USUARIO`/`SISTEMA`, `justificativa`, `dataTransicao`), via `transicionar()` (produção existente) ou `registrarNascimento()` (produção nova, criada já em determinado estado por `dividir()`/`agrupar()`).

### Enums corretos
- `MotivoMovimentacaoInsumo`: `COMPRA, BAIXA_MANUAL, PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO, PRODUCAO, ORCAMENTO, ESTORNO_PRODUCAO` — alinhado com `MotivoMovimentacaoProduto` desde #148 (V0.6); `InsumoService.baixaManual()` grava `request.motivo()` (antes hardcoded para `BAIXA_MANUAL`, ignorando o motivo enviado); CHECK constraint `chk_mov_insumo_motivo` atualizado na migration V22
- `MotivoMovimentacaoProduto`: `PRODUCAO, ORCAMENTO, PERDA, AVARIA, USO_EXTRA, CORRECAO, OUTRO, ESTORNO_PRODUCAO`
- `EstadoProducao` (6 estados): `AGUARDANDO_INICIO, EM_ANDAMENTO, TRAVADA, FINALIZADA, CANCELADA, NAO_REALIZADA`
- `TipoOrigemProducao`: `DIVISAO, AGRUPAMENTO`
- `OrigemHistoricoStatus`: `SISTEMA, USUARIO`

### Ciclo de vida — transições válidas (todas em `ProducaoService`)
```
AGUARDANDO_INICIO ──iniciar() sem bloqueante──────────────→ EM_ANDAMENTO   (USUARIO)
AGUARDANDO_INICIO ──iniciar() com bloqueante, sem dividir─→ TRAVADA        (SISTEMA, RN-067 (PDC-008))
AGUARDANDO_INICIO ──iniciar() com bloqueante, dividir=true→ NAO_REALIZADA  (a original — SISTEMA)
                                                              + 2 produções filhas nascem já
                                                              em EM_ANDAMENTO/TRAVADA (dividir(), RN-065 (PDC-006))
AGUARDANDO_INICIO ──cancelar() Fluxo A, sem consumoReal───→ CANCELADA      (USUARIO, RN-071 (PDC-012))

EM_ANDAMENTO ──travar() manual──────────────────────────────→ TRAVADA     (USUARIO, RN-068 (PDC-009))
EM_ANDAMENTO ──finalizar()──────────────────────────────────→ FINALIZADA  (USUARIO, RN-070 (PDC-011))
EM_ANDAMENTO ──cancelar() Fluxo B, com consumoReal──────────→ CANCELADA   (USUARIO, RN-072 (PDC-013))

TRAVADA ──retomar(), já havia ProducaoInsumoConsumido───────→ EM_ANDAMENTO (USUARIO, RN-069 (PDC-010))
TRAVADA ──retomar(), nada consumido, reverificação libera───→ EM_ANDAMENTO (USUARIO, RN-069 (PDC-010))
TRAVADA ──retomar(), ainda bloqueada, sem dividir────────────→ permanece TRAVADA (sem histórico novo)
TRAVADA ──retomar(), ainda bloqueada, dividir=true───────────→ NAO_REALIZADA (original) + 2 filhas
TRAVADA ──cancelar() Fluxo B, com consumoReal────────────────→ CANCELADA   (USUARIO, RN-072 (PDC-013))

{AGUARDANDO_INICIO, EM_ANDAMENTO, TRAVADA}* ──agrupar()──────→ NAO_REALIZADA (todas as originais)
                                                                 + 1 produção nova em AGUARDANDO_INICIO,
                                                                   EM_ANDAMENTO ou TRAVADA (RN-074 (PDC-015), conforme
                                                                   `estadoDestino` do request)

FINALIZADA, CANCELADA, NAO_REALIZADA — estados terminais, nunca saem (checado em `agrupar()`, ProducaoService.java:576-579)
```
`iniciar()`/`retomar()`/`agrupar()` (destino `EM_ANDAMENTO`) replicam o mesmo par de passos — `verificarComponentes()` seguido de bloquear-ou-baixar — como código copiado em vez de método privado compartilhado (débito de extração, ver OP).

### Contrato de `consumoReal` (declaração de consumo real — Fluxo B, `cancelar()`/`agrupar()`)
- Lista de `ConsumoRealRequest { insumoId ou produtoBaseId, quantidadeConsumida }` — **só os itens cujo consumo real diverge** do que foi baixado originalmente entram na lista.
- Item **ausente** da lista = consumo total assumido (nenhum estorno) — mesmo comportamento do fluxo antigo, que sempre estornava tudo quando não havia como saber o consumo real (`aplicarConsumoReal()`, ProducaoService.java:256-258).
- `estornada=true` em `MovimentacaoInsumo`/`MovimentacaoProduto` **só é marcado em estorno total** (`consumidoReal == 0`) — o campo é booleano simples, sem noção de "parcialmente estornada"; estorno parcial gera só a nova movimentação `ESTORNO_PRODUCAO`, sem tocar a movimentação original (`estornarComponente()`, ProducaoService.java:273-276).
- `ProducaoInsumoConsumido` não tem campo `estornada` — o booleano vive em `MovimentacaoInsumo`/`MovimentacaoProduto`.
- Em `agrupar()`, `consumoRealPorProducao` é `Map<producaoId, List<ConsumoRealRequest>>` — só é aplicado às produções de origem `EM_ANDAMENTO`/`TRAVADA` (as que já baixaram insumo).

### RN-069 (PDC-010) — Retomada de TRAVADA (discriminação de tipo de trava)
`retomar()` distingue as duas origens possíveis de TRAVADA verificando se **já existe `ProducaoInsumoConsumido` para a produção** (`jaConsumido`, ProducaoService.java:393-397): se existir, a trava veio de `travar()` manual após `iniciar()` já ter baixado insumo — não reverifica bloqueio nem baixa de novo (dobraria o consumo), só volta o estado. Se não existir, a trava veio do próprio `iniciar()` bloqueando antes de baixar qualquer coisa — reverifica e, se liberado, baixa pela primeira vez.

### Padrões de implementação consolidados
- `uuid_generate_v4()` — nunca `gen_random_uuid()` (projeto usa extensão `uuid-ossp` desde V1; confirmado ausência de `gen_random_uuid()` em toda `db/migration/`)
- `BusinessException` só tem `message` — sem campo de tipo. `GlobalExceptionHandler` serializa para `ErrorResponseDTO { message, status, timestamp, fieldErrors }`
- `calcularAlertasAoVivo()` é tolerante a produto com `rendimento` nulo/≤0 — pula o produto (`continue`) em vez de lançar exceção
- Lógica de negócio compartilhada entre fluxos vive em método privado desde a primeira implementação (ex.: `aplicarConsumoReal()`, `estornarComponente()`) — evita duplicação quando o mesmo cálculo é chamado por rotas diferentes (finalizar direto vs. consumo real declarado). Exceção conhecida: ver bloco de duplicação de `verificarComponentes()`/baixa acima.

### Coluna `estado` vs `status` em `producoes`
- `status` (VARCHAR, `ATIVA`/`CANCELADA`): removido na migration V21 (fluxo legado de 1 produto) — não existe mais
- `estado` (VARCHAR, `EstadoProducao`): coluna do ciclo de vida novo — sempre usar esta

### Race condition conhecida — número sequencial de Produção
`proximoNumero()` (ProducaoService.java:1021-1025) é um `MAX(numero)+1` simples via `findTopByUsuarioIdOrderByNumeroDesc`, sem lock — duas requisições concorrentes do mesmo usuário podem ler o mesmo `numero` antes de qualquer uma salvar. A constraint `UNIQUE(usuario_id, numero)` (`uq_producao_usuario_numero`, migration V8) impede duplicata no banco, mas a segunda requisição falha com erro de constraint em vez de retry automático. **Não criar produções concorrentemente (`Promise.all`/paralelo) em testes** — sempre sequencial, para não colidir com esse comportamento conhecido e não corrigido.

### Justificativas — mínimo uniformizado
- Todos os campos de justificativa/observação (baixa manual de insumo/produto, avançar status): mínimo **30 caracteres** — uniformizado no #127 (Produção já usava 30 desde RN-078 (PDC-019); baixa manual e `AvancaStatusRequest` usavam 50 até então, único ponto fora de linha)
- Não criar campo novo de justificativa/observação com mínimo diferente de 30 sem decisão explícita

### `/producoes/lote` removido — RN-037 (PDC-021)/RN-060 (PDC-001) obsoletas
O endpoint `POST /producoes/lote` (lançamento múltiplo em uma sessão, fluxo antigo de 1 produto por produção) não existe mais — substituído pelo modelo de N produtos por produção (`POST /producoes` já aceita múltiplos itens). `RN-037` (cancelamento incondicional/total do fluxo antigo — obsoleta, modelo de Produção atômica removido na V21; ver histórico em `PDC-021`) e `RN-060` (validação de estoque combinada do lançamento múltiplo — obsoleta, substituída por `PDC-001`; nota: o mesmo número RN-060 foi reaproveitado em outra época do projeto para o campo `confirmarEstoqueNegativo`, hoje `PDC-007` — colisão histórica de numeração, ver achado em P-MIGRACAO-022.5) estão marcadas obsoletas desde V0.6 em `legado/BUSINESS_RULES.md` — substituídas por RN-071 (PDC-012)/RN-072 (PDC-013) (cancelamento por estado) e RN-061 (PDC-001) (consolidação de N produtos), respectivamente. Não citar RN-037 (PDC-021)/RN-060 (PDC-001) como regra vigente em código ou commit novo.

---

## PdfMapper Pattern (CRÍTICO — atualizado V0.8.1, migração para microsserviço)

Toda formatação de dados para PDF acontece no Java — o microsserviço (`pense-precifique-pdf`) é
"burro" quanto a regra de negócio, só renderiza o payload que recebe. **Thymeleaf foi removido por
completo em #262 (V0.8.1)** — não existe mais `ctx.setVariable`/templates `.html` no backend;
qualquer menção a Thymeleaf em documentação antiga é legado, não replicar.

Padrão atual: `PdfMapper` monta um DTO achatado por tipo de documento (`OrcamentoPdfData`,
`ReciboPdfData` — reaproveitado por Sinal/Multa/Estorno, `ReciboPagamentoPdfData`), depois um
segundo método (`toXxxMicroservicoPayload`) converte esse DTO para o payload JSON que o
microsserviço espera (schema Zod do lado de lá, `contrato-pdf.md` seção 8):

```java
// PdfService chama:
ReciboPdfData dados = pdfMapper.toReciboPdfDataMulta(orc, empresa, itens, customizacoes);
PdfMicroservicoDocumentoReciboMultaPayload payload = pdfMapper.toReciboSinalMicroservicoPayload(dados);
// payload vira o corpo JSON de POST ao microsserviço — nenhuma lógica no lado de lá.
```

**Paridade entre documentos do mesmo "formato" (P-B004, V0.8.1):** Multa e Estorno reusam a mesma
assinatura de método (`Orcamento, Empresa, List<OrcamentoItem>, Map<UUID,List<OrcamentoItemCustomizacao>>`)
e a mesma estratégia de busca de itens/customizações (`ReciboPdfPayloadService.montarPayloadXxx`) —
ao adicionar um campo novo a um dos dois, replicar a mesma mudança no irmão em vez de resolver só
o caso que motivou a tarefa, a menos que exista razão de negócio para divergir (documentar se for
o caso).

**Nunca** usar lógica de formatação/regra de negócio dentro do microsserviço `pense-precifique-pdf`
— toda decisão (valor final, texto condicional, o que mostrar/ocultar) vem pronta no payload.

**Identificadores sequenciais (`INS-N`/`PRO-N`/`CLI-N`/`CTG-N`) nunca aparecem em PDF** — regra
explícita do bloco Catálogo (RN-053 (ver `DECISOES_GLOBAIS.md`)). `PdfMapper` não deve expô-los em
nenhum DTO de PDF.

---

## Endpoints implementados

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /auth/login | Login |
| POST | /auth/register | Cadastro |
| GET | /usuarios/me | Dados do usuário |
| PUT | /usuarios/me/senha | Altera senha (`senhaAtual`, `novaSenha`, `confirmarNovaSenha`) — implementado desde o Épico 1, conectado ao frontend na V0.5 (#111) |
| GET/PUT | /empresa | Perfil da empresa |
| GET/PUT | /configuracoes/precificacao | Configurações de precificação |
| GET/POST | /clientes | Lista paginada e cria |
| GET/PUT/DELETE | /clientes/{id} | Detalhe, edita, inativa |
| GET/POST | /insumos | Lista paginada e cria |
| POST | /insumos/{id}/baixa-manual | Baixa manual (obs mín. 30 chars — RN-035 (INS-007), uniformizado no #127) |
| GET | /insumos/{id}/movimentacoes | Histórico paginado |
| GET | /insumos/{id}/produtos-relacionados | Lista produtos cuja ficha técnica usa o insumo (expõe `produtoId`+`identificador`) |
| POST | /lotes-compra | Registra compra em lote (RN-036 (COM-001)) |
| GET/POST | /produtos | Lista paginada e cria — `busca` corrigido em 2026-07-09 (commit `222b939`); listagem também expõe `algumInsumoNaoFracionavel` e custo recalculado ao vivo desde V0.6.1.1 (#187/#135, mesma semântica do detalhe) |
| GET | /produtos/contagens | **Novo (V0.6.1.1)** — `{ total, inativos, porTipo: { produto, produtoBase, customizacao } }`, ignora `busca` (badges de categoria são navegação global) |
| GET/PUT | /produtos/{id} | Detalhe e edita — inclui `rendimento`, `custoTotalLote`, `custoUnitario`, `algumInsumoNaoFracionavel` |
| GET | /produtos/{id}/preco-sugerido?margem=X | **Novo (RN-054 (ORC-020))** — preço sugerido de venda avulsa: `{ custoUnitario, margem, precoSugerido }` |
| POST | /produtos/{id}/baixa-manual | Baixa manual (obs mín. 30 chars — RN-035 (PDT-009), uniformizado no #127) |
| POST | /produtos/{id}/inativar , /produtos/{id}/reativar | **Novo (V0.6.1.1)** — inativação reversível de verdade, distinta do soft-delete (`DELETE`, `ProdutoService.excluir()`, comportamento inalterado); idempotente, 204, 404 se já excluído |
| GET | /produtos/{id}/movimentacoes | Histórico paginado — inclui `catalogoReferencia`/`precoVendido` quando `motivo=ORCAMENTO` |
| GET/POST | /catalogos | **Novo (EP-09)** — lista e cria catálogo |
| GET/PUT | /catalogos/{id} | **Novo (EP-09)** — detalhe (com itens) e edita |
| POST | /catalogos/{id}/duplicar | **Novo (EP-09)** — duplica com overrides preservados (confirmar path exato) |
| PUT | /catalogos/{id}/ativar-desativar | **Novo (EP-09)** — toggle `ativo` (confirmar path exato) |
| POST/PUT/DELETE | /catalogos/{id}/itens | **Novo (EP-09)** — CRUD de ItemCatalogo (confirmar path exato) |
| POST | /catalogos/{catalogoId}/itens/preview-preco | **Novo (V0.6.1 Onda 4, RN-NOVA-8)** — preview ao vivo do preço sugerido, sem persistir |
| GET/POST | /producoes | Lista paginada e lança produção — aceita só `quantidade` (RN-051 (PDC-005) Atualizada, V0.6.1.1: conceito de `lotes`/XOR removido, produto com insumo não-fracionável trava `quantidade` em exatamente 1× o rendimento, bloqueio 400 se divergir); listagem ordenada por `numero DESC` por padrão, 4 colunas ordenáveis por clique (#158) |
| POST | /producoes/simular-alertas | **Novo (V0.6.1.1, #153, RN-NOVA-7)** — simula alertas de insumo para um array de produtos sem persistir, reaproveita `validarEResolverProdutos`+`calcularAlertas` |
| GET | /producoes/{id} | Detalhe |
| GET | /producoes/preview | **Novo (bloco Catálogo)** — preview de estoque insuficiente antes de confirmar |
| POST | /producoes/{id}/cancelar | Cancela — Fluxo A (`AGUARDANDO_INICIO`, sem movimentação, RN-071 (PDC-012)) ou Fluxo B (`EM_ANDAMENTO`/`TRAVADA`, com `consumoReal` e estorno da diferença, RN-072 (PDC-013)); RN-037 (PDC-021) obsoleta, ver Módulo de Produção |
| GET/POST | /orcamentos | Lista paginada e cria — item aceita `itemCatalogoId` OU `produtoId`+`margemAplicada`+`precoUnitario` (RN-054 (ORC-020)). `GET` aceita `?busca=` opcional (case-insensitive, filtra por `cliente.nome`), combinável com `?status=` (#93) e, desde V0.6.1.1, com `?dataCriacaoDe=`/`?dataCriacaoAte=` (RN-082 (ORC-023)) |
| POST | /orcamentos/simular-alertas | **Novo (V0.6.1.1, RN-081 (ORC-022))** — simula `avisosEstoque` de Produto (não Insumo) sem persistir, mesmo padrão do endpoint equivalente de Produção |
| GET | /orcamentos/{id} | Detalhe — item de catálogo expõe `catalogoIdentificador` (`CTG-N`) e `catalogoNome`, ambos preenchidos em `OrcamentoMapper` a partir de `item.getItemCatalogo().getCatalogo()` (desde v0.2.1, commits `1ab552d`/`03a68be`) |
| GET | /orcamentos/itens/busca | Busca de item de catálogo com filtro opcional por catálogo (EP-07, confirmar path exato) |
| POST | /orcamentos/{id}/avancar-status | Transição de status |
| POST | /orcamentos/{id}/cancelar | Cancela (wizard por status) |
| GET | /orcamentos/{id}/pdf | PDF do orçamento — delega ao microsserviço `pense-precifique-pdf` (épico #89, V0.8), via `PdfMicroservicoClient`. Outros 4 tipos abaixo continuam via OpenHTMLToPDF/Thymeleaf local até migração própria |
| GET | /orcamentos/{id}/preview-html | **Novo (épico #89, V0.8)** — proxy para o preview HTML do microsserviço (`text/html;charset=UTF-8`), consumido pelo frontend via `<iframe srcDoc>` |
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
- [x] Épico 3.1 — Compras em Lote (RN-036 (COM-001))
- [x] Épico 4 — Produtos (v0 + ajuste V0.2D0: custo puro + rendimento)
- [x] Épico 5 — Clientes
- [x] Épico 6 — Registro de Produção (v0 + ajuste V0.2D0: rendimento + insumo não-fracionável)
- [x] Épico 7 — Orçamentos (v0 + ajuste V0.2D0: consome Catálogo + venda avulsa RN-054 (ORC-020))
- [x] Épico 8 — Dashboard
- [x] Épico 9 — Catálogo (novo, V0.2D0)
- [x] Épico 10 — Identificadores sequenciais (V0.2D0)

---

## Migrations ativas

| Versão | Arquivo | Descrição |
|--------|---------|-----------|
| V1 | V1__initial_schema.sql | Schema completo (v0) |
| V2 | V2__allow_null_insumo_id_producao_consumidos.sql | Permite insumo_id null |
| V3 | V3__add_fracionavel_to_insumos.sql | Coluna `fracionavel` em `insumos` |
| V4 | V4__ajusta_produtos_rendimento_preco_venda.sql | `rendimento`, ajustes em `preco_venda` de `produtos` |
| V5 | V5__add_margem_lucro_override_produtos.sql | `margem_lucro` + flag `override` em `produtos` |
| V6 | V6__cria_catalogos_itens_catalogo.sql | Tabelas `catalogos`/`itens_catalogo` |
| V7 | V7__orcamento_itens_item_catalogo_id_movimentacoes_campos.sql | `orcamento_itens.item_catalogo_id`, campos de movimentação |
| V8 | V8__numero_sequencial_insumos_produtos_clientes.sql | Identificadores sequenciais (`numero`) em insumos/produtos/clientes |
| V9 | V9__orcamento_item_produto_avulso.sql | `produto_id`+`margem_aplicada` em `orcamento_itens`, CHECK XOR (RN-054 (ORC-020)) |
| V10 | V10__insumo_permitir_estoque_negativo.sql | Coluna `permitir_estoque_negativo` (BOOLEAN, default true) em `insumos` — RN-059 (INS-009) |
| V11 | V11__produto_permitir_estoque_negativo.sql | Coluna `permitir_estoque_negativo` (BOOLEAN, default true) em `produtos` — RN-059 (PDT-011) |
| V12 | V12__indices_fk_orcamento_item_customizacoes.sql | Índices de apoio às FKs de `orcamento_item_customizacoes` (#56) |
| V13 | V13__indices_fk_producao_insumos_consumidos.sql | Índices de apoio às FKs de `producao_insumos_consumidos` (#56) |
| V14 | V14__indice_fk_producoes_produto_id.sql | Índice de apoio à FK `producoes.produto_id` (#56) |
| V15 | V15__indice_fk_itens_catalogo_customizacao_produto_id.sql | Índice de apoio à FK `itens_catalogo_customizacao.produto_id` (#56) |
| V16 | V16__indices_usuario_id_sem_indice.sql | Índices em colunas `usuario_id` que ainda não tinham índice (#56) |
| V17–V23 | — | Ciclo de vida novo de Produção (ver seção "Módulo de Produção" acima) — tabela não atualizada por Ondas 1/2, consultar `ls db/migration/` para os nomes exatos |
| V24 | V24__unique_usuario_id_empresa_configuracao_precificacao.sql | `UNIQUE(usuario_id)` em `empresas` (índice parcial, `WHERE deleted_at IS NULL`) e `configuracoes_precificacao` (constraint direta) — #142 |
| V25 | V25__remove_coluna_data_producao_producoes.sql | Remove coluna órfã `producoes.data_producao` — #179 |

---

## Aprendizados críticos

| Regra | Contexto |
|-------|----------|
| Serialização JSON é camelCase (default Spring/Jackson) — nunca configurar snake_case | Confirmado via investigação: todos os DTOs já saem consistentes em camelCase sem `@JsonNaming`. |
| DTO de resposta precisa expor todos os campos persistidos | `OrcamentoDetalheResponse` já teve campo persistido no banco mas ausente na API (`percentualMulta`), causando falha silenciosa no frontend. Ao adicionar campo à entidade JPA, verificar DTO de resposta e mapper imediatamente. |
| Regra de negócio replicada em múltiplos fluxos precisa cobertura explícita em cada um | RN-006 (INS-002) (insumo fracionável) estava coberta em baixa manual e compra em lote, mas não em `FichaTecnicaItem` — regra existir em um lugar não garante que foi aplicada em todos os pontos de entrada. |
| "Compila limpo" nunca é validação suficiente para Service | Todo bloco Catálogo foi validado via curl com números reais que descartam coincidência (não valores redondos), inclusive casos de rejeição (XOR duplo/vazio). Regra fixa a partir daqui. |
| Campo calculado exposto em DTO pode ficar "esquecido" se o dev assumir que existe sem checar | `precoSugerido`/`custoUnitario` só têm valor real se o Service que os calcula for de fato chamado no fluxo certo — confirmar sempre via curl, não assumir pela leitura do código. |
| **A API não tem prefixo `/api`** — base é `http://localhost:8080/auth/login`, nunca `http://localhost:8080/api/auth/login` | Armadilha de validação via curl: o backend não libera `/error` no `SecurityConfig`, então uma rota inexistente como `/api/auth/login` retorna **401** em vez de 404 — parece erro de autenticação mas é só rota errada. Confirmado: `/api/...` → 401 (mascarado); `/auth/login` → 400 (rota real, corpo inválido). Sempre conferir a rota sem `/api` antes de investigar autenticação. |
| **Nota de backlog "decisão registrada"/"implementado" não é o mesmo que confirmado no código** — sempre conferir o payload real (curl) ou o código-fonte antes de escrever prompt/implementação em cima de uma anotação assim | Furou 3 vezes na V0.6.1.1: `identificador` ausente em `OrcamentoDetalheResponse` apesar de "registrado"; `algumInsumoNaoFracionavel` ausente na listagem de Produtos apesar de "expor... registrado 2026-07-20"; RN-051 (PDC-005) nunca implementada de verdade no backend apesar de o item aparecer marcado como backend fechado. Todos os 3 só foram pegos porque alguém validou ao vivo antes de seguir em frente. |
| **Desconto sobre valor já pago: sempre piso zero (`BigDecimal.max(ZERO, ...)` ou equivalente), nunca permitir negativo** | Padrão de `P-B002` (V0.8.1) — `OrcamentoService.calcularValorFinalMulta()`: multa final = `max(multaBruta − sinalPago, ZERO)`. Se o valor descontado supera o valor bruto, o resultado correto é zero (nem cobrança nem devolução), nunca um número negativo persistido/exibido. Reaproveitar este padrão para qualquer cálculo futuro de "desconta X de Y" (ex.: o achado ainda pendente de decisão de negócio "sinal pago > valor da multa" — mini-estorno — registrado em CSV, não implementado). |
| **Campo `LocalDateTime` em request vindo do frontend: o frontend manda no formato certo, ou o Jackson rejeita com 400 antes do Service** | Causa raiz de `P-B003` (RECONCILIA-005) — `AvancaStatusRequest.dataEstornoSinal` é `LocalDateTime`, mas o frontend enviava só a data (`"2026-08-19"`, de um `<input type="date">`), sem componente de hora. Jackson rejeita a desserialização com 400 antes de qualquer validação do Service — o erro nem chega no código de negócio, então debugar pelo Service sozinho não mostra nada. Ao expor um campo `LocalDateTime` num request DTO alimentado por um input HTML `date` (não `datetime-local`), documentar explicitamente o formato esperado (`contrato-orcamento.md`) e decidir de quem é a responsabilidade de completar a hora (frontend, como decidido aqui — hora fixa meio-dia, evita ambiguidade de fuso). |

---

## Bugs conhecidos

_Nenhum bug funcional de backend em aberto registrado neste documento no momento (`BUG-BUSCA-PRODUTO` corrigido em 2026-07-09, commit `222b939`; `BUG-BUSCA-ORCAMENTO` corrigido em #93, V0.5 — ver git log para bugs anteriores)._

_Débito de `data_producao`/`Producao.dataProducao` (coluna órfã, levantado na retomada V0.6 de 2026-07-20) removido no #179 (V0.6.3, Onda 3) — migration V25, coluna e campo não existem mais._

---

## Padrão de commits

Rastreamento de tarefas migrou de ClickUp para OpenProject. Formato real confirmado em `git log --oneline -15` — número da issue como **prefixo**, sem a palavra "OpenProject":

```
#N feat: nova funcionalidade
#N fix: correção de bug
#N refactor: refatoração
#N chore: configuração/infra
```

Escopo entre parênteses é opcional e aparece quando ajuda a localizar o módulo (`fix(escopo): ...`). Sem número de issue (ex. doc solto), usar só `tipo: descrição`.

**Nota:** o frontend (`pense-precifique-frontend/CLAUDE.md`) documenta um padrão diferente (`tipo(escopo): descrição — OpenProject #N`, número como sufixo) — os dois repositórios convergiram para OpenProject mas com formatos de commit distintos; cada um segue o próprio `git log`, não o do outro.

---

## Documentação externa

Documentação funcional (regras de negócio, cenários BDD, contrato de API, decisões técnicas) não vive em `pense-precifique-backend/` — está em `Docs-PensePrecifique/` (pasta local `docs-pense-precifique/`), na **raiz do projeto** (`/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/docs-pense-precifique/`, um nível acima deste repositório). `pense-precifique-backend/`, `pense-precifique-frontend/` e `docs-pense-precifique/` são 3 repositórios git independentes dentro da raiz do projeto. **Desde V0.7 (2026-08-08), `docs-pense-precifique/` também é versionado** — mas com ciclo de commit próprio, separado dos outros dois: mudanças em `DECISOES_GLOBAIS.md`/`MAPA_INTERDEPENDENCIAS.md`/`modulos/*/{regras,cenarios,contrato,decisoes}-*.md` acumulam no working tree ao longo de toda a versão, commitadas de uma vez só na Retomada (fechamento da versão), não por tarefa individual — editar o arquivo diretamente durante o desenvolvimento normal, sem parar para `git add`/commit a cada mudança.

Estrutura atual:
- `modulos/[MODULO]/{regras,cenarios,contrato,decisoes}-[modulo].md` — **fonte de verdade** de regras de negócio, cenários BDD, contrato de API e decisões técnicas, por módulo (ex.: `modulos/ORCAMENTO/regras-orcamento.md`, `modulos/ORCAMENTO/contrato-orcamento.md`)
- `legado/BUSINESS_RULES.md`, `legado/SCENARIOS.md`, `legado/CONTRATO_API.md` — consolidado monolítico antigo, pré-migração modular. **Histórico — não consultar para desenvolvimento novo**, só como registro de origem quando uma RN cita um número antigo (`RN-NNN`)
- `DECISOES_GLOBAIS.md`, `MAPA_INTERDEPENDENCIAS.md`, `ARCHITECTURE.md`, `PRD.md`, `DEPLOY.md` — transversais, na raiz de `docs-pense-precifique/`

**Regras de negócio de uma tarefa:** consultar sempre `modulos/[MODULO]/regras-[modulo].md` e `cenarios-[modulo].md` do módulo relevante à tarefa — nunca os arquivos legado (`legado/BUSINESS_RULES.md`/`SCENARIOS.md`), que são histórico, não fonte de verdade.

**Convenção de numeração:** cada módulo tem seu próprio código de RN (ex.: `INS-001` em Insumo, `ORC-028` em Orçamento) e de cenário (`INS-CEN-001`), sequenciais dentro do módulo — sem relação com os números antigos do legado (`RN-NNN`/cenário numérico). Uma RN pode citar seu número de origem no legado como referência histórica, mas o código do módulo é o identificador vigente.
