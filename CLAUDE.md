# Pense & Precifique — Contexto do Back-End

> Lido automaticamente pelo Claude Code ao abrir `pense-precifique-backend/`. Projeto pré-produção
> (primeiro deploy estável com usuários reais = v1). Caminho:
> `/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend`
> Última atualização: 24/09/2026 (Retomada V0.14.0) · Branch padrão atual: `feature/V0.14.0`
> Se este arquivo e o prompt da sessão divergirem, este arquivo vence.
>
> Histórico de versões (V0.5 a V0.8.2) migrado para os `regras-*.md`/`decisoes-*.md` de cada
> módulo em `docs-pense-precifique/` — não vive mais aqui. Ver seção 2.

**Stack:** Java 21 · Spring Boot 3.3.5 · PostgreSQL 16 (Docker) · JWT stateless (HS512) ·
Flyway (`resources/db/migration/`, número mais alto sempre via `ls`, não copiar aqui) · Maven
(`./mvnw`) · Springdoc/Swagger só em `dev`.

**PDF: 100% via microsserviço externo `pense-precifique-pdf`** (Node/Express/React SSR/Puppeteer,
desde #262/V0.8.1) para os 6 tipos de documento (Orçamento, recibo-sinal, recibo-pagamento,
pdf-multa, recibo-estorno, catalogo — V0.13.0/#519, 1º que não deriva de Orçamento) — não existe
mais geração local (Thymeleaf/OpenHTMLToPDF foi removido por completo). Ver "PdfMapper Pattern"
abaixo e `docs-pense-precifique/modulos/PDF/`.

---

## 1. Ambiente

```bash
cd "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique"
docker compose up --build
```

| Profile | Swagger | Logs |
|---------|---------|------|
| `dev` | ✅ `/swagger-ui.html` | DEBUG |
| `prod` | ❌ desabilitado | INFO |

**Schema OpenAPI (`api-docs`) fica em `/api-docs`, não no default do springdoc (`/v3/api-docs`)**
— `application.yml` customiza `springdoc.api-docs.path`. Armadilha confirmada 2x (rodadas
`seguranca-resiliencia` de V0.12.0 e V0.13.0): consultar `/v3/api-docs` retorna 500 (rota
inexistente cai em `NoResourceFoundException` sem handler dedicado, mapeada pro catch-all — ver
achado #525-adjacente) e já foi lido incorretamente como "Springdoc não registrado no classpath".
Sempre confirmar `/api-docs` antes de rodar Schemathesis ou qualquer fuzzing de schema.

**Conta de teste:** `penseprecifique@admin.com` / `senha12345`. **A API não tem prefixo `/api`**
— base é `http://localhost:8080/auth/login`. Armadilha: `SecurityConfig` não libera `/error`,
então uma rota inexistente como `/api/auth/login` retorna **401** (parece erro de autenticação),
não 404. Sempre conferir a rota sem `/api` antes de investigar autenticação.

**Testes sempre em container, nunca no host** — `SPRING_DATASOURCE_*` só existe como env var
dentro do `docker-compose.yml`; `./mvnw test` no host falha ao subir o `ApplicationContext`. O
container `backend` em execução é a imagem final (`FROM eclipse-temurin:21-jre`, só `app.jar`,
sem Maven) — não dá para rodar teste nele. É preciso buildar o estágio `build` à parte:

```bash
cd "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend"
docker build --target build -t pense-backend-build .
# garantir "db" de pé antes: docker compose up -d db
source "/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/.env"
docker run --rm --network penseprecifique_default \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/pense_precifique_db" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USER" -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" -e JWT_EXPIRATION_MS="$JWT_EXPIRATION_MS" \
  -e R2_ACCOUNT_ID="$R2_ACCOUNT_ID" -e R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
  -e R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" -e R2_BUCKET_NAME="$R2_BUCKET_NAME" \
  -e R2_PUBLIC_URL="$R2_PUBLIC_URL" -e R2_ENDPOINT="$R2_ENDPOINT" \
  pense-backend-build ./mvnw test
```

**Todas as 6 `R2_*` são obrigatórias no `docker run`** (desde V0.13.0/#518) — `R2StorageClient` é
injetado em vários Services, então faltar uma só (achado V0.14.0: `R2_ENDPOINT`) derruba o
`ApplicationContext` e aparece como centenas de `UnsatisfiedDependencyException` em testes sem
relação com upload. A causa real só aparece no `Caused by: ... Could not resolve placeholder` —
nunca ler só o fim da saída (`tail`) para diagnosticar.

Suíte usa `**/*IT.java` (Surefire configurado assim desde #132) — nunca `-Dtest=Nome` isolado como
validação final, sempre `./mvnw test` completo. "Compila limpo" nunca é validação suficiente —
validar via curl com valores reais (não redondos), inclusive casos de rejeição.

---

## 2. Onde cada coisa vai

Pacote por módulo de domínio, Controller+Service+Repository juntos na mesma pasta (pacote flat
antigo extinto desde o refactor V0.5):

```
com/penseprecifique/api/
├── auth/ caixa/ catalogo/ cliente/ dashboard/ empresa/ insumo/ orcamento/ producao/ produto/
│   unidademedida/
│   → *Controller, *Service(+Impl quando houver), *Repository de cada módulo
│   (`unidademedida/`, V0.14.0/#298: entidade `UnidadeMedida` referenciada por FK em `Insumo` —
│   regras documentadas no módulo INSUMO, não num módulo próprio)
│   (`caixa/`, V0.12.0: VendaCaixa/VendaCaixaItem/VendaCaixaPagamento, CaixaTurno/CaixaMovimento —
│   `empresa/` também ganhou `MetodoPagamentoConfiguravel`, ver seção 3 sobre a colisão de nome)
├── pdf/               # PdfService, PdfMapper (ver seção própria)
├── shared/
│   ├── domain/{entity,enums,converter}/   # entidades JPA, enums, converters
│   ├── dto/{request,response}/[modulo]/   # DTOs por módulo de domínio (request/response na raiz
│   │                                       # só para cross-cutting: ErrorResponseDTO, avisos de
│   │                                       # estoque negativo compartilhados Orçamento/Produção)
│   ├── dto/pdf/       # DTOs achatados de payload de PDF (OrcamentoPdfData, ReciboPdfData, etc.)
│   ├── mapper/        # @Component manual — apesar do nome do pacote, NÃO é MapStruct
│   ├── validation/    # ValidadorArquivoImagem (V0.14.0, DT-NOVA-5) — validação única de upload
│   │                  # de imagem (JPG/PNG, máx. 5MB) para Catálogo, Produto e Empresa
│   └── exception/     # GlobalExceptionHandler, ResourceNotFoundException, BusinessException
├── infra/{config,security,storage}/   # SecurityConfig, JwtTokenProvider/Filter,
│                                       # UserDetailsServiceImpl, R2StorageClient (V0.13.0,
│                                       # upload de imagem — ver seção 5)
└── util/              # IdentificadorFormatter (ORC-N/INS-N/PRO-N/etc., RN-053), NumeroSequencialUtil
```

**Novo módulo de domínio?** Segue o padrão acima — Controller+Service+Repository na mesma pasta
do módulo, DTOs em `shared/dto/{request,response}/[modulo]/`, entidades em `shared/domain/entity/`.

**Documentação funcional não vive aqui.** Regras de negócio, cenários BDD, contrato de API e
decisões técnicas ficam em `docs-pense-precifique/modulos/[MODULO]/{regras,cenarios,contrato,
decisoes}-[modulo].md` — **fonte de verdade**, repositório git próprio (`../docs-pense-precifique/`,
ciclo de commit próprio, ver seção 4), nunca duplicar aqui. Cada módulo tem seu próprio código de
RN (`INS-001`, `ORC-028`) e de cenário, sequenciais dentro do módulo, sem relação com os números
antigos do legado. `legado/{BUSINESS_RULES,SCENARIOS,CONTRATO_API}.md` é o consolidado
pré-migração modular — histórico, não consultar para desenvolvimento novo. `DECISOES_GLOBAIS.md`/
`MAPA_INTERDEPENDENCIAS.md`/`ARCHITECTURE.md` são transversais, na raiz de `docs-pense-precifique/`.

---

## 3. Verificar antes de criar

- **Identificador sequencial legível novo** (`XXX-N`)? Usar `util/IdentificadorFormatter` +
  `NumeroSequencialUtil` — não reimplementar. Padrão: `INTEGER` + `ORDER BY numero DESC`
  (`findTopByUsuarioIdOrderByNumeroDesc`), nunca `SERIAL` nem ordenar por campo de data mutável.
  `UNIQUE(usuario_id, numero)` sempre que a tabela for tocada.
- **Bloqueio de exclusão/inativação por vínculo em uso**? Ver `ProdutoService#resolverVinculos`/
  `InsumoService#resolverVinculos` (padrão "resolver vínculos por blocos independentes",
  `POST /{id}/resolver-vinculos`) antes de inventar um mecanismo novo.
- **Regra de negócio já existe em algum módulo?** Checar `docs-pense-precifique/modulos/[MODULO]/
  regras-[modulo].md` antes de assumir que não existe — regras de outros módulos costumam já ter
  resolvido o mesmo problema (ex.: XOR de origem, padrão calculado+override).
- **Entidade nova cujo nome "óbvio" já existe como enum/classe em outro módulo?** Conferir
  `shared/domain/enums/`/`shared/domain/entity/` inteiro antes de nomear — achado real (V0.12.0):
  a entidade nova de método de pagamento configurável não pôde se chamar `MetodoPagamento` porque
  já existia `shared.domain.enums.MetodoPagamento` (enum fixo, usado por `Orcamento`); como as duas
  vivem no mesmo package (`shared.domain.entity`), o nome simples vencia o import wildcard do enum
  em 4 arquivos de Orçamento/PDF — só descoberto ao compilar de verdade (`mvn clean`), não no
  incremental do host. Renomeada para `MetodoPagamentoConfiguravel`.

---

## 4. Convenções da stack

- **PKs:** UUID, sempre `uuid_generate_v4()` (extensão `uuid-ossp`) — nunca `gen_random_uuid()`.
- **Soft delete:** coluna `deleted_at` — nunca `repository.delete()`. **Tabelas/colunas:** `snake_case`.
- **DTOs:** `dto/request/`/`dto/response/` — nunca expor entidade. Serialização é camelCase
  (default Jackson) — nunca configurar snake_case.
- **Service:** classe concreta `@Service`, sem interface+impl (desde o Épico 6).
- **Regras de negócio:** no Service, nunca no Controller. **usuarioId:** sempre via
  `SecurityContextHolder`, nunca no body.
- **FK entre entidades:** `@ManyToOne`+`@JoinColumn` (objeto de relação), nunca UUID cru.
- **Mapper:** classe concreta `@Component`, setters manuais — **nunca MapStruct** (confirmado:
  nenhum `@Mapping`/`org.mapstruct` no projeto).
- **Padrão calculado + override:** campo calculado nunca persistido (`precoSugerido`); campo
  persistido + flag `override` booleana quando há edição manual que trava recálculo — mudança de
  custo nunca recalcula o preço automaticamente, só atualiza o "sugerido" exibido como referência.
- **XOR entre duas origens/campos:** CHECK constraint no banco + validação explícita no Service
  com `BusinessException` distinguindo "os dois preenchidos" de "nenhum preenchido" — nunca
  mensagem genérica única.
- **Exceção:** `BusinessException` genérica, só `message` — não criar tipos novos.
- Toda correção/tech debt termina com commit + push antes de encerrar o chat, mesmo sem
  fechamento de épico.
- **GitFlow por versão:** trabalho de uma versão vai para `feature/V[X.Y]`, criada no início da
  fase Backend/Frontend da versão. PR para `main` só no fechamento formal (após a Retomada).
- **Commit:** `tipo(escopo): descrição — OpenProject #N` (padrão canônico do projeto, alinhado ao
  do `pense-precifique-pdf`; decisão de 05/09/2026 — este repositório documentava antes `#N tipo:
  descrição`, número como prefixo; **não retroativo**, só commits novos).
- **Todo prompt segue `PADRAO_PROMPTS.md`** (`Pense Software/Skills/`).
- **Editar `docs-pense-precifique/` não gera commit a cada mudança** — mudanças em
  `modulos/*/{regras,cenarios,contrato,decisoes}-*.md`/`DECISOES_GLOBAIS.md` acumulam no working
  tree ao longo da versão, commitadas de uma vez na Retomada (repositório git próprio, ciclo
  separado dos outros dois).

---

## 5. Padrões consolidados

- **PdfMapper Pattern** (canônico: `pdf/PdfMapper.java`) — toda formatação para PDF acontece no
  Java; o microsserviço só renderiza o payload recebido, nunca decide nada. `PdfMapper` monta um
  DTO achatado por tipo (`toXxxData`), depois converte pro payload JSON do microsserviço
  (`toXxxMicroservicoPayload`). Documentos do mesmo "formato" (Multa/Estorno) reusam
  assinatura/estratégia de busca — replicar mudança nos dois, a menos que haja razão de negócio
  pra divergir. Identificadores sequenciais (`INS-N`/`PRO-N`/etc.) nunca aparecem em PDF (RN-053)
  — **exceção deliberada:** `catalogo` (`CTG-N`) aparece sim, no cabeçalho (`#numeroFormatado`),
  mesmo padrão visual que os outros 5 já usam pro próprio identificador (ex.: `#123` de Orçamento)
  — RN-053 nunca teve essa intenção pra Catálogo, é o padrão de cabeçalho compartilhado entre
  todos os 6 tipos que se aplica igual. Bean colaborador de um tipo que não deriva de Orçamento
  (como `catalogo`) não precisa herdar de `OrcamentoPdfPayloadService` nem usar
  `OrcamentoRepository` — lê direto do próprio módulo (`CatalogoPdfPayloadService` lê
  `CatalogoRepository`/`ItemCatalogoRepository`).
- **Endpoint de simulação `simular-*`** (canônico: `ProducaoService`/`OrcamentoService`) — quando
  o Frontend precisa de preview sem persistir, endpoint dedicado com prefixo `simular-` no mesmo
  path do real, reaproveitando os métodos/validações do endpoint real por chamada direta, nunca
  duplicando lógica e nunca chamando o método que persiste. Seguir este padrão para qualquer
  preview novo em vez de inventar mecanismo diferente.
- **Propagar vínculo/histórico para N origens** (canônico: `ProducaoService.agrupar()` chamando
  `propagarOrigemParaFilha()` 1x por origem em loop, V0.8.3/RN-NOVA-21) — quando um método já
  aceita 1 origem e precisa passar a aceitar N, preferir chamar em loop a generalizar a assinatura
  (preserva o cálculo por-origem já testado). Sempre checar `UNIQUE` composta na tabela de destino
  antes de assumir que múltiplas chamadas são seguras — aqui exigiu checar existência
  (`findByOrcamentoIdAndProducaoId`) antes de cada `save()`, porque origens diferentes podem
  compartilhar o mesmo vínculo (`UNIQUE(orcamento_id, producao_id)`).
- **Navegação de associação a partir de `LEFT JOIN` explícito precisa de `LEFT JOIN` também no
  próximo salto** (achado de bug real, V0.8.3, `buscarIdsOrdenados()`) — em JPQL, `pp.produto`
  vindo de uma linha `LEFT JOIN pp` volta a virar `INNER JOIN` implícito no Hibernate se não
  declarado explicitamente; produção sem produto nenhum some silenciosamente do resultado. Ao
  navegar uma associação a partir de um `LEFT JOIN`, declarar `LEFT JOIN` no segundo salto também,
  nunca confiar em inferência.
- **Mapeamento de exceção para status HTTP correto** (canônico: `shared/exception/
  GlobalExceptionHandler.java`, RN-085 em `DECISOES_GLOBAIS.md`, V0.9.0/#421) — todo
  `@ExceptionHandler` novo segue o contrato: erro de validação de payload/parâmetro → 400,
  recurso não encontrado → 404, qualquer coisa fora desses casos e genuinamente inesperada → 500
  com mensagem genérica (nunca vazar stack trace). Achado do gate `seguranca-resiliencia`
  (fuzzing): parâmetro de path/query com tipo incompatível (`MethodArgumentTypeMismatchException`)
  não estava coberto e caía no handler genérico — ao adicionar handler novo, sempre checar contra
  esse contrato antes de deixar algo cair no `Exception.class` por omissão.
- **Campo condicional por tipo/discriminador é sempre coluna nullable + validação em `Service`,
  nunca `@Inheritance`** (reforçado em V0.12.0, `MetodoPagamentoConfiguravel.taxaMaquininha`/
  `maxParcelas`, só aceitos para tipos de cartão) — o projeto não usa herança JPA em nenhuma
  entidade; mesmo princípio já usado por `FichaTecnicaItem.componenteTipo`.
- **Extensão de enum compartilhado entre módulos exige `mvn clean` antes de considerar "sem
  impacto"** (achado V0.12.0, `#490` — `ReferenciaMovimentacaoTipo` ganhando `CAIXA` quebrou um
  switch exaustivo em `InsumoService`, módulo não relacionado, só visível em build limpo, nunca no
  incremental do host). Rodar `mvn clean package` sempre que um enum usado por switch exaustivo em
  mais de um módulo ganhar um valor novo.
- **Upload de arquivo (imagem) — validar no Service antes de subir pro storage externo**
  (canônico: `infra/storage/R2StorageClient` + `shared/validation/ValidadorArquivoImagem`, V0.13.0/
  #518, promovido a `shared/` em V0.14.0 quando Produto (#531) e Empresa (#532) ganharam upload — o
  antigo `ItemCatalogoService#validarArquivoFoto` não existe mais; nunca reimplementar a validação
  local num Service novo) — formato/tamanho são validados no Service **antes** de
  qualquer chamada ao storage (Cloudflare R2, S3-compatible), nunca confiando só na validação do
  Frontend (DT-NOVA-4). Cliente S3 usa `software.amazon.awssdk:s3` com `url-connection-client`
  explícito — **nunca o `apache-client` default do módulo**, que traz uma versão de `httpclient5`
  incompatível com a já resolvida no classpath do projeto (`NoClassDefFoundError:
  TlsSocketStrategy`, quebra o `ApplicationContext` de toda a suíte de testes, não só do módulo
  tocado — só descoberto rodando `./mvnw test` completo, nunca no `mvn compile`). Trocar um
  arquivo remove o anterior do storage (melhor esforço, nunca falha a troca); remover a entidade
  dona do arquivo também remove o arquivo do storage (nunca deixa órfão cobrando armazenamento
  sem uso). Aplicado hoje em 3 pontos: foto de item de catálogo, foto de produto, logo da empresa.
  Débito conhecido: `MultipartException` (request não-multipart) cai no handler genérico e vira 500
  em vez de 400 nos 3 — OpenProject #553.

---

## 6. Legado e exceções

- **Coluna `status` em `producoes` não existe mais** (removida na migration V21, fluxo legado de
  1 produto/produção) — a coluna vigente do ciclo de vida é `estado`. Não referenciar `status`
  em código novo de Produção.
- **Pacote flat antigo** (`controller/`/`service/`/`repository/` soltos na raiz de `api/`) foi
  extinto no refactor V0.5 — não replicar, seguir a estrutura por módulo da seção 2.
- **`docker-compose.yml` fixa `TZ=America/Sao_Paulo` nos serviços `db`/`backend`** (V0.12.0,
  achado do teste manual — container rodava em UTC puro por padrão, todo `LocalDateTime.now()` do
  projeto ficava 3h à frente do horário real). Não remover essa env var nem assumir fuso do
  container sem checar — é a correção da raiz, não um workaround local num módulo específico.

---

## 7. Anti-padrões do projeto

- **Regra de negócio replicada em múltiplos fluxos sem cobertura em cada um** — já aconteceu (RN
  de insumo fracionável coberta em baixa manual e compra em lote, mas não em `FichaTecnicaItem`).
  Regra existir em um lugar não garante que foi aplicada em todos os pontos de entrada.
- **Campo persistido ausente no DTO de resposta ou "esquecido" apesar de calculado** — já
  aconteceu 2x (`percentualMulta` ausente em `OrcamentoDetalheResponse`; `precoSugerido`/
  `custoUnitario` só têm valor real se o Service que os calcula for de fato chamado no fluxo
  certo). Ao adicionar campo à entidade, conferir DTO de resposta e mapper imediatamente —
  confirmar sempre via curl, não assumir pela leitura do código.
- **Nota de backlog "decisão registrada"/"implementado" não é confirmação de código** — furou 3x
  na V0.6.1.1. Sempre conferir o payload real (curl) ou o código-fonte antes de escrever
  prompt/implementação em cima de uma anotação assim.
- **Trocar o tipo de um campo de entidade (ex.: `String` → FK `@ManyToOne` lazy) quebra testes de
  outros módulos** — V0.14.0/#298: `Insumo.unidadeMedida` virar FK quebrou a compilação de ~43
  arquivos de teste que montavam `Insumo` com o texto direto, e o finder por id+usuário passou a
  precisar de `@EntityGraph` para não dar `LazyInitializationException` fora da transação. Ao mudar
  tipo de campo, rodar a suíte completa e varrer os builders de teste antes de fechar, não só o
  módulo dono.
- **Débito conhecido, não corrigido:** `iniciar()`/`retomar()`/`agrupar()` (`ProducaoService`)
  replicam o mesmo par de passos (`verificarComponentes()` + bloquear-ou-baixar) como código
  copiado em vez de método privado compartilhado — não confundir com decisão deliberada. Ver
  OpenProject antes de decidir se já há tarefa aberta.
