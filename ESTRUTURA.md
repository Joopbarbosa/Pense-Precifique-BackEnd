# Pense & Precifique — Estrutura do Back-End

## Repositório: `pense-precifique-backend`

```
pense-precifique-backend/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/penseprecifique/api/
│       │       │
│       │       ├── PensePrecifiqueApplication.java
│       │       │
│       │       ├── config/
│       │       │   ├── SecurityConfig.java          # JWT filter chain, CORS, permissões
│       │       │   ├── CorsConfig.java
│       │       │   ├── OpenApiConfig.java           # Swagger (ativo só em dev)
│       │       │   └── JwtConfig.java               # Bean de configuração JWT
│       │       │
│       │       ├── controller/
│       │       │   ├── AuthController.java          # POST /auth/login, /auth/register
│       │       │   ├── UsuarioController.java       # GET /usuarios/me, PUT /usuarios/me/senha
│       │       │   ├── EmpresaController.java       # GET/PUT /empresa (perfil da artesã)
│       │       │   ├── ConfiguracaoController.java  # GET/PUT /configuracoes/precificacao
│       │       │   ├── ClienteController.java       # CRUD /clientes
│       │       │   ├── InsumoController.java        # CRUD /insumos + movimentações
│       │       │   ├── ProdutoController.java       # CRUD /produtos + ficha técnica
│       │       │   ├── ProducaoController.java      # CRUD /producoes
│       │       │   └── OrcamentoController.java     # CRUD /orcamentos + status transitions + PDFs
│       │       │
│       │       ├── service/
│       │       │   ├── AuthService.java
│       │       │   ├── UsuarioService.java
│       │       │   ├── EmpresaService.java
│       │       │   ├── ConfiguracaoService.java
│       │       │   ├── ClienteService.java
│       │       │   ├── InsumoService.java
│       │       │   ├── ProdutoService.java
│       │       │   ├── FichaTecnicaService.java     # cálculo de custo da ficha
│       │       │   ├── ProducaoService.java         # baixa insumos + atualiza estoque
│       │       │   ├── OrcamentoService.java        # regras de transição de status
│       │       │   ├── PdfService.java              # geração de todos os PDFs
│       │       │   └── impl/
│       │       │       ├── AuthServiceImpl.java
│       │       │       ├── UsuarioServiceImpl.java
│       │       │       ├── EmpresaServiceImpl.java
│       │       │       ├── ConfiguracaoServiceImpl.java
│       │       │       ├── ClienteServiceImpl.java
│       │       │       ├── InsumoServiceImpl.java
│       │       │       ├── ProdutoServiceImpl.java
│       │       │       ├── FichaTecnicaServiceImpl.java
│       │       │       ├── ProducaoServiceImpl.java
│       │       │       ├── OrcamentoServiceImpl.java
│       │       │       └── PdfServiceImpl.java
│       │       │
│       │       ├── repository/
│       │       │   ├── UsuarioRepository.java
│       │       │   ├── EmpresaRepository.java
│       │       │   ├── ConfiguracaoPrecificacaoRepository.java
│       │       │   ├── ClienteRepository.java
│       │       │   ├── InsumoRepository.java
│       │       │   ├── MovimentacaoInsumoRepository.java
│       │       │   ├── ProdutoRepository.java
│       │       │   ├── FichaTecnicaItemRepository.java
│       │       │   ├── MovimentacaoProdutoRepository.java
│       │       │   ├── ProducaoRepository.java
│       │       │   ├── OrcamentoRepository.java
│       │       │   ├── OrcamentoItemRepository.java
│       │       │   └── ReciboPagamentoRepository.java
│       │       │
│       │       ├── domain/
│       │       │   ├── entity/
│       │       │   │   ├── Usuario.java
│       │       │   │   ├── Empresa.java
│       │       │   │   ├── ConfiguracaoPrecificacao.java
│       │       │   │   ├── Cliente.java
│       │       │   │   ├── Insumo.java
│       │       │   │   ├── MovimentacaoInsumo.java
│       │       │   │   ├── Produto.java
│       │       │   │   ├── FichaTecnicaItem.java
│       │       │   │   ├── MovimentacaoProduto.java
│       │       │   │   ├── Producao.java
│       │       │   │   ├── ProducaoInsumoConsumido.java
│       │       │   │   ├── Orcamento.java
│       │       │   │   ├── OrcamentoItem.java
│       │       │   │   ├── OrcamentoItemCustomizacao.java
│       │       │   │   └── ReciboPagamento.java
│       │       │   └── enums/
│       │       │       ├── TipoProduto.java          # PRODUTO, PRODUTO_BASE, CUSTOMIZACAO
│       │       │       ├── StatusOrcamento.java      # todos os 10 status
│       │       │       ├── TipoMovimentacao.java     # ENTRADA, SAIDA
│       │       │       ├── MotivoMovimentacao.java   # PRODUCAO, ORCAMENTO, PERDA, AVARIA, etc.
│       │       │       └── TipoComponente.java       # INSUMO, PRODUTO
│       │       │
│       │       ├── dto/
│       │       │   ├── request/
│       │       │   │   ├── LoginRequestDTO.java
│       │       │   │   ├── CadastroRequestDTO.java
│       │       │   │   ├── AlterarSenhaRequestDTO.java
│       │       │   │   ├── EmpresaRequestDTO.java
│       │       │   │   ├── ConfiguracaoRequestDTO.java
│       │       │   │   ├── ClienteRequestDTO.java
│       │       │   │   ├── InsumoRequestDTO.java
│       │       │   │   ├── BaixaManualInsumoRequestDTO.java
│       │       │   │   ├── ProdutoRequestDTO.java
│       │       │   │   ├── FichaTecnicaItemRequestDTO.java
│       │       │   │   ├── BaixaManualProdutoRequestDTO.java
│       │       │   │   ├── ProducaoRequestDTO.java
│       │       │   │   ├── OrcamentoRequestDTO.java
│       │       │   │   └── AvancaStatusRequestDTO.java
│       │       │   └── response/
│       │       │       ├── AuthResponseDTO.java      # token + dados do usuário
│       │       │       ├── EmpresaResponseDTO.java
│       │       │       ├── ConfiguracaoResponseDTO.java
│       │       │       ├── ClienteResponseDTO.java
│       │       │       ├── InsumoResponseDTO.java
│       │       │       ├── MovimentacaoResponseDTO.java
│       │       │       ├── ProdutoResponseDTO.java
│       │       │       ├── ProdutoDetalheResponseDTO.java  # card + histórico + ficha
│       │       │       ├── ProducaoResponseDTO.java
│       │       │       ├── ProducaoDetalheResponseDTO.java
│       │       │       ├── OrcamentoResponseDTO.java
│       │       │       ├── OrcamentoDetalheResponseDTO.java
│       │       │       └── ReciboPagamentoResponseDTO.java
│       │       │
│       │       ├── mapper/
│       │       │   ├── ClienteMapper.java
│       │       │   ├── InsumoMapper.java
│       │       │   ├── ProdutoMapper.java
│       │       │   ├── ProducaoMapper.java
│       │       │   └── OrcamentoMapper.java
│       │       │
│       │       ├── security/
│       │       │   ├── JwtTokenProvider.java        # gera e valida tokens
│       │       │   ├── JwtAuthenticationFilter.java # intercepta requests
│       │       │   └── UserDetailsServiceImpl.java  # carrega usuário do banco
│       │       │
│       │       └── exception/
│       │           ├── GlobalExceptionHandler.java  # @ControllerAdvice
│       │           ├── ResourceNotFoundException.java
│       │           ├── BusinessException.java       # violações de regras de negócio
│       │           └── UnauthorizedException.java
│       │
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/
│               └── migration/
│                   └── V1__initial_schema.sql       # cópia do schema.sql para Flyway
│
├── src/test/java/com/penseprecifique/api/
│   ├── controller/                                  # testes de integração (MockMvc)
│   ├── service/                                     # testes unitários (Mockito)
│   └── repository/                                  # testes com Testcontainers
│
├── Dockerfile
└── pom.xml
```

---

## Dockerfile (back-end)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Estrutura do Front-End

### Repositório: `pense-precifique-frontend`

```
pense-precifique-frontend/
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── routes/
│   │   └── index.tsx                  # React Router — todas as rotas
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── LoginPage.tsx          # Tela 1
│   │   │   ├── CadastroPage.tsx       # Tela 2
│   │   │   └── OnboardingPage.tsx     # Tela 3
│   │   ├── dashboard/
│   │   │   └── DashboardPage.tsx      # Tela 4
│   │   ├── clientes/
│   │   │   └── ClientesPage.tsx       # Tela 5
│   │   ├── orcamentos/
│   │   │   ├── CriarOrcamentoPage.tsx # Tela 6
│   │   │   ├── PreviewPdfPage.tsx     # Tela 7
│   │   │   ├── DetalheOrcamentoPage.tsx  # Tela 8
│   │   │   ├── ListaOrcamentosPage.tsx   # Tela 9
│   │   │   ├── PreviewMultaPage.tsx      # Tela 19
│   │   │   ├── ReciboSinalPage.tsx       # Tela 20
│   │   │   └── ReciboPagamentoPage.tsx   # Tela 21
│   │   ├── insumos/
│   │   │   ├── ListaInsumosPage.tsx   # Tela 10
│   │   │   ├── FormInsumoPage.tsx     # Tela 11 (cadastrar/editar)
│   │   │   └── DetalheInsumoPage.tsx  # Tela 12
│   │   ├── produtos/
│   │   │   ├── ListaProdutosPage.tsx  # Tela 13
│   │   │   ├── CadastrarProdutoPage.tsx  # Tela 14
│   │   │   ├── EditarProdutoPage.tsx     # Tela 15
│   │   │   └── DetalheProdutoPage.tsx    # Tela 16
│   │   ├── producao/
│   │   │   └── RegistroProducaoPage.tsx  # Tela 17
│   │   └── configuracoes/
│   │       └── ConfiguracoesPage.tsx     # Tela 18
│   ├── components/
│   │   ├── ui/                        # shadcn/ui ou componentes base
│   │   ├── layout/
│   │   │   ├── Sidebar.tsx
│   │   │   └── TopBar.tsx
│   │   └── shared/
│   │       ├── StatusBadge.tsx        # badge de status do orçamento
│   │       ├── TipoProdutoBadge.tsx   # badge PRODUTO / PRODUTO_BASE / CUSTOMIZACAO
│   │       └── ConfirmModal.tsx
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   └── useToast.ts
│   ├── services/
│   │   ├── api.ts                     # axios instance com interceptor JWT
│   │   ├── authService.ts
│   │   ├── clienteService.ts
│   │   ├── insumoService.ts
│   │   ├── produtoService.ts
│   │   ├── producaoService.ts
│   │   └── orcamentoService.ts
│   ├── store/
│   │   └── authStore.ts               # Zustand — token + usuário logado
│   └── types/
│       ├── auth.ts
│       ├── cliente.ts
│       ├── insumo.ts
│       ├── produto.ts
│       ├── producao.ts
│       └── orcamento.ts
├── Dockerfile
├── nginx.conf
├── tailwind.config.ts
├── tsconfig.json
├── vite.config.ts
└── package.json
```

---

## Endpoints principais (esboço)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /auth/login | Login |
| POST | /auth/register | Cadastro |
| GET/PUT | /empresa | Perfil da empresa |
| GET/PUT | /configuracoes/precificacao | Configurações de preço |
| GET/POST | /clientes | Lista e cria clientes |
| GET/PUT/DELETE | /clientes/{id} | Detalhe, edita, inativa |
| GET/POST | /insumos | Lista e cria insumos |
| POST | /insumos/{id}/baixa-manual | Baixa manual de insumo |
| GET | /insumos/{id}/movimentacoes | Histórico do insumo |
| GET/POST | /produtos | Lista e cria produtos |
| GET/PUT | /produtos/{id} | Detalhe e edita |
| POST | /produtos/{id}/baixa-manual | Baixa manual de produto |
| GET | /produtos/{id}/movimentacoes | Histórico do produto |
| GET/POST | /producoes | Lista e lança produção |
| GET | /producoes/{id} | Detalhe da produção |
| GET/POST | /orcamentos | Lista e cria orçamentos |
| GET/PUT | /orcamentos/{id} | Detalhe e edita |
| POST | /orcamentos/{id}/avancar-status | Transição de status |
| POST | /orcamentos/{id}/cancelar | Cancela orçamento |
| GET | /orcamentos/{id}/pdf | Download PDF do orçamento |
| GET | /orcamentos/{id}/recibo-sinal | Download recibo do sinal |
| GET | /orcamentos/{id}/recibo-pagamento | Download recibo completo |
| GET | /orcamentos/{id}/pdf-multa | Download PDF de multa |
