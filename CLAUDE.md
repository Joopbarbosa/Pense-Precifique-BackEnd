# Pense & Precifique — Contexto do Back-End

> Lido automaticamente pelo Claude Code ao abrir `pense-precifique-backend/`. Projeto pré-produção
> (primeiro deploy estável com usuários reais = v1). Caminho:
> `/home/joaobarbosa/Documentos/Projetos/Pense & Precifique/pense-precifique-backend`
> Última atualização: 05/09/2026 (P-DIETA-002, Retomada V0.8.3) · Branch padrão atual: `feature/V0.8.3`
> Se este arquivo e o prompt da sessão divergirem, este arquivo vence.
>
> Histórico de versões (V0.5 a V0.8.2) migrado para os `regras-*.md`/`decisoes-*.md` de cada
> módulo em `docs-pense-precifique/` — não vive mais aqui. Ver seção 2.

**Stack:** Java 21 · Spring Boot 3.3.5 · PostgreSQL 16 (Docker) · JWT stateless (HS512) ·
Flyway (`resources/db/migration/`, número mais alto sempre via `ls`, não copiar aqui) · Maven
(`./mvnw`) · Springdoc/Swagger só em `dev`.

**PDF: 100% via microsserviço externo `pense-precifique-pdf`** (Node/Express/React SSR/Puppeteer,
desde #262/V0.8.1) para os 5 tipos de documento (Orçamento, recibo-sinal, recibo-pagamento,
pdf-multa, recibo-estorno) — não existe mais geração local (Thymeleaf/OpenHTMLToPDF foi removido
por completo). Ver "PdfMapper Pattern" abaixo e `docs-pense-precifique/modulos/PDF/`.

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
  pense-backend-build ./mvnw test
```

Suíte usa `**/*IT.java` (Surefire configurado assim desde #132) — nunca `-Dtest=Nome` isolado como
validação final, sempre `./mvnw test` completo. "Compila limpo" nunca é validação suficiente —
validar via curl com valores reais (não redondos), inclusive casos de rejeição.

---

## 2. Onde cada coisa vai

Pacote por módulo de domínio, Controller+Service+Repository juntos na mesma pasta (pacote flat
antigo extinto desde o refactor V0.5):

```
com/penseprecifique/api/
├── auth/ catalogo/ cliente/ dashboard/ empresa/ insumo/ orcamento/ producao/ produto/
│   → *Controller, *Service(+Impl quando houver), *Repository de cada módulo
├── pdf/               # PdfService, PdfMapper (ver seção própria)
├── shared/
│   ├── domain/{entity,enums,converter}/   # entidades JPA, enums, converters
│   ├── dto/{request,response}/[modulo]/   # DTOs por módulo de domínio (request/response na raiz
│   │                                       # só para cross-cutting: ErrorResponseDTO, avisos de
│   │                                       # estoque negativo compartilhados Orçamento/Produção)
│   ├── dto/pdf/       # DTOs achatados de payload de PDF (OrcamentoPdfData, ReciboPdfData, etc.)
│   ├── mapper/        # @Component manual — apesar do nome do pacote, NÃO é MapStruct
│   └── exception/     # GlobalExceptionHandler, ResourceNotFoundException, BusinessException
├── infra/{config,security}/   # SecurityConfig, JwtTokenProvider/Filter, UserDetailsServiceImpl
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
  pra divergir. Identificadores sequenciais (`INS-N`/`PRO-N`/etc.) nunca aparecem em PDF (RN-053).
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

---

## 6. Legado e exceções

- **Coluna `status` em `producoes` não existe mais** (removida na migration V21, fluxo legado de
  1 produto/produção) — a coluna vigente do ciclo de vida é `estado`. Não referenciar `status`
  em código novo de Produção.
- **Pacote flat antigo** (`controller/`/`service/`/`repository/` soltos na raiz de `api/`) foi
  extinto no refactor V0.5 — não replicar, seguir a estrutura por módulo da seção 2.

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
- **Débito conhecido, não corrigido:** `iniciar()`/`retomar()`/`agrupar()` (`ProducaoService`)
  replicam o mesmo par de passos (`verificarComponentes()` + bloquear-ou-baixar) como código
  copiado em vez de método privado compartilhado — não confundir com decisão deliberada. Ver
  OpenProject antes de decidir se já há tarefa aberta.
