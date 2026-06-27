# Roteiro de Validação — Épico 2 (Configuração do Perfil)
### Versão didática — explicando o que cada teste faz e por quê

> Este roteiro testa o fluxo de configuração inicial do perfil: dados da empresa
> (Onboarding) e parâmetros de precificação (valor/hora, margem), tanto pelo backend
> (curl) quanto pelo frontend (navegador).
>
> **Pré-requisito:** `docker compose up --build` rodando (não feche esse terminal!),
> P-010 a P-014 concluídos.

---

## Conceitos novos nesta validação

**O que é "upsert"?**
É a junção de "update" + "insert" — um endpoint que cria o registro se ele não existir,
ou atualiza se já existir. Usamos isso para `Empresa` e `ConfiguracaoPrecificacao`
porque cada usuário tem exatamente UM registro de cada (relação 1:1), e não faz sentido
ter um endpoint `POST` separado só para a "primeira vez".

**Por que `GET /empresa` pode dar 404, mas `GET /configuracoes/precificacao` não?**
São duas decisões de design diferentes, de propósito:
- `Empresa` representa "a artesã configurou o perfil dela?" — se não, faz sentido dizer
  "não encontrado" (`404`), porque é uma informação que realmente não existe ainda.
- `ConfiguracaoPrecificacao` representa parâmetros usados em **cálculos** (fórmula de
  preço). Se retornasse `404`, o frontend teria que tratar esse erro só para descobrir
  que os valores são zero — é mais simples o backend já devolver "zero" como padrão.

Esse tipo de decisão (quando retornar erro vs. quando retornar um valor padrão) é algo
que todo backend dev pondera constantemente — não existe resposta "certa" universal,
depende do que é mais útil para quem consome a API.

---

## PARTE A — Testando o Backend (via terminal)

> Vamos reaproveitar o usuário criado no Épico 1
> (`validacao.epico1@teste.com`, senha atual `novaSenha123` após a troca do A13).

### A1. Gerar um token válido (pré-requisito para todos os testes seguintes)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"novaSenha123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo $TOKEN
```

**O que esperar:** o comando `echo $TOKEN` deve mostrar uma string longa começando com
`eyJ...`. Se vier vazio, o login falhou — confira a senha (lembre que trocamos para
`novaSenha123` no A13 do Épico 1).

> 💡 Esse `$TOKEN` vale para todos os comandos abaixo, **enquanto o terminal estiver
> aberto**. Se fechar e abrir de novo, rode esse comando A1 de novo primeiro.

---

### A2. Consultar perfil da empresa ANTES de configurar ❌ (deve dar 404)

**O que testamos:** um usuário recém-cadastrado (ou que nunca configurou o perfil) não
tem `Empresa` no banco — o endpoint informa isso corretamente.

```bash
curl -i http://localhost:8080/empresa -H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:** `HTTP/1.1 404` + `"message":"Perfil da empresa não configurado"`

> ⚠️ Se você já rodou os testes do P-012 anteriormente com esse mesmo usuário (durante
> o desenvolvimento), a `Empresa` já pode existir — nesse caso você verá `200` em vez de
> `404`. Não é um erro: significa que o "estado inicial" já passou. Anote isso e siga
> para A3 normalmente (A3 vai funcionar como "atualização" em vez de "criação", o que
> também é válido).

---

### A3. Criar o perfil da empresa (primeira configuração) ✅

**O que testamos:** o caminho feliz do `PUT /empresa` — cria o registro pela primeira
vez.

```bash
curl -i -X PUT http://localhost:8080/empresa \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Ateliê da Ana","email":"contato@atelierdaana.com","whatsapp":"5511999999999","endereco":"Rua das Flores, 123","logoUrl":""}'
```

**Resultado esperado:** `HTTP/1.1 200` + JSON com `id`, `nome: "Ateliê da Ana"`, `email`,
`whatsapp`, `endereco`, `logoUrl`, `createdAt`, `updatedAt`. **Sem** `usuarioId` no
corpo da resposta.

---

### A4. Consultar perfil da empresa APÓS configurar ✅

**O que testamos:** os dados persistidos no A3 são recuperados corretamente.

```bash
curl -i http://localhost:8080/empresa -H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:** `HTTP/1.1 200` (não mais `404`) + os mesmos dados do A3.

---

### A5. Atualizar o perfil da empresa (segunda chamada ao PUT) ✅

**O que testamos:** o "update" do upsert — não cria um segundo registro, apenas
modifica o existente.

```bash
curl -i -X PUT http://localhost:8080/empresa \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Ateliê da Ana - Atualizado","email":"contato@atelierdaana.com","whatsapp":"5511999999999","endereco":"Rua das Flores, 123","logoUrl":""}'
```

**Resultado esperado:** `HTTP/1.1 200` + `"nome":"Ateliê da Ana - Atualizado"`. O `id`
retornado deve ser **o mesmo** do A3/A4 (mesmo registro, apenas atualizado — não um novo
UUID).

> 💡 **Como confirmar que o `id` é o mesmo?** Compare visualmente o campo `"id":
> "..."` da resposta do A4 com a resposta deste A5 — devem ser idênticos.

---

### A6. Tentar atualizar sem informar o nome (campo obrigatório) ❌

**O que testamos:** a validação `@NotBlank` no campo `nome` do `EmpresaRequestDTO`.

```bash
curl -i -X PUT http://localhost:8080/empresa \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"contato@atelierdaana.com"}'
```

**Resultado esperado:** `HTTP/1.1 400` + `"message":"Erro de validação"` + `fieldErrors`
contendo `"nome": "O nome da empresa é obrigatório"`

---

### A7. Acessar `/empresa` sem autenticação ❌

**O que testamos:** mesma proteção de rotas do Épico 1, agora aplicada a um novo
endpoint — reforça que TODA rota nova automaticamente herda a proteção JWT (não
precisamos reconfigurar segurança a cada novo Controller).

```bash
curl -i http://localhost:8080/empresa
```

**Resultado esperado:** `HTTP/1.1 401`

---

### A8. Consultar configuração de precificação ANTES de configurar ✅ (retorna zeros, não 404)

**O que testamos:** a diferença de design explicada acima — aqui, mesmo sem nunca ter
salvo nada, o endpoint retorna `200` com valores padrão.

> ⚠️ Se você já testou o P-013 anteriormente com este usuário, talvez já existam
> valores salvos (ex: 25 e 40) em vez de zero — nesse caso, você verá os valores já
> configurados. Não é erro, apenas significa que o estado "nunca configurado" já passou
> para este usuário. Siga para A9 normalmente.

```bash
curl -i http://localhost:8080/configuracoes/precificacao -H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:** `HTTP/1.1 200` + `{"id":null,"valorHora":0,"margemPadrao":0,"updatedAt":null}`
(ou valores já configurados, conforme a observação acima)

---

### A9. Definir valor/hora e margem padrão ✅

**O que testamos:** o `PUT /configuracoes/precificacao` — caminho feliz.

```bash
curl -i -X PUT http://localhost:8080/configuracoes/precificacao \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"valorHora":25,"margemPadrao":40}'
```

**Resultado esperado:** `HTTP/1.1 200` + `{"id":"...","valorHora":25,"margemPadrao":40,"updatedAt":"..."}`
— agora `id` não é mais `null`.

---

### A10. Consultar configuração após salvar ✅

```bash
curl -i http://localhost:8080/configuracoes/precificacao -H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:** `HTTP/1.1 200` + os mesmos valores do A9 (`valorHora: 25,
margemPadrao: 40`)

---

### A11. Atualizar configuração (segunda chamada) ✅

```bash
curl -i -X PUT http://localhost:8080/configuracoes/precificacao \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"valorHora":30,"margemPadrao":50}'
```

**Resultado esperado:** `HTTP/1.1 200` + `{"id":"...(MESMO id do A9/A10)...","valorHora":30,"margemPadrao":50,...}`

---

### A12. Tentar definir valor/hora negativo ❌

**O que testamos:** a validação `@DecimalMin("0")` no `ConfiguracaoRequestDTO` —
impede valores que não fariam sentido no mundo real (não existe "hora negativa").

```bash
curl -i -X PUT http://localhost:8080/configuracoes/precificacao \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"valorHora":-5,"margemPadrao":40}'
```

**Resultado esperado:** `HTTP/1.1 400` + `fieldErrors` contendo
`"valorHora": "O valor da hora não pode ser negativo"`

---

## SMOKE TEST — Confirmar que o Épico 1 continua funcionando

Antes de ir para a Parte B, vamos rodar rapidamente os smoke checks definidos no
Épico 1, para garantir que nada que fizemos agora quebrou a autenticação:

```bash
# SMOKE-1: login retorna token válido
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"novaSenha123"}' | head -c 100
echo ""

# SMOKE-2: rota protegida sem token → 401
curl -i http://localhost:8080/usuarios/me | head -1

# SMOKE-3: rota protegida com token válido → 200
curl -i http://localhost:8080/usuarios/me -H "Authorization: Bearer $TOKEN" | head -1
```

**Resultado esperado:** primeira linha com token, depois `HTTP/1.1 401`, depois
`HTTP/1.1 200`.

---

## PARTE B — Testando o Frontend (navegador)

> Use uma aba anônima (`Ctrl+Shift+P`) para garantir estado limpo, OU use a mesma aba
> já logada do Épico 1 — ambos funcionam, mas para testar o **Onboarding** (B2) é mais
> fácil com um usuário **novo**, que nunca passou por ele.

### B1. Acessar `/configuracoes` já logado, com perfil configurado ✅

Se você já fez A3-A11 (perfil e configuração já existem no banco para o usuário
`validacao.epico1@teste.com`), faça login com esse usuário e acesse `/configuracoes`.

**Resultado esperado:**
- Aba "Perfil da empresa" mostra os dados reais salvos no A5 ("Ateliê da Ana -
  Atualizado", e-mail, whatsapp, endereço) — **não** mais os dados mockados ("Ateliê da
  Ana" sem "- Atualizado")
- Aba "Precificação" mostra `valorHora: 30` e `margemPadrao: 50` (valores do A11) — não
  mais `25` e `40` (que eram os mockados)

> 💡 Esse é o teste mais importante desta parte: confirma que a tela **parou de usar
> dados mockados** e está de fato lendo do backend.

**Anote:** os valores que apareceram batem com o que você salvou via curl (A5/A11)?

---

### B2. Onboarding com usuário novo ✅

**O que testamos:** o fluxo completo de um usuário que acabou de se cadastrar — ainda
não tem `Empresa` configurada, e deve passar pela tela de Onboarding.

**Passos:**
1. Crie um usuário novo (pode ser via curl, reaproveitando o A1 do Épico 1, com um
   e-mail diferente, ex: `onboarding.epico2@teste.com`) — ou cadastre direto pelo
   frontend (`/cadastro`)
2. Após o cadastro, você deve ser redirecionado para `/onboarding` (se o P-009 já
   implementou esse redirecionamento) ou `/dashboard` (se ainda estiver com o `// TODO`
   pendente — nesse caso, navegue manualmente para `http://localhost:3000/onboarding`)
3. Preencha os dados da empresa (nome, e-mail, whatsapp, endereço — e valor/hora/margem,
   se a tela tiver esses campos)
4. Submeta

**Resultado esperado:** redirecionamento para `/dashboard` após sucesso.

**Verificação extra (via curl):** confirme que os dados foram salvos para esse novo
usuário:
```bash
TOKEN_NOVO=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"onboarding.epico2@teste.com","senha":"SUASENHA"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -i http://localhost:8080/empresa -H "Authorization: Bearer $TOKEN_NOVO"
```
**Resultado esperado:** `200` (não `404`) com os dados preenchidos no B2.

**Anote:** o onboarding funcionou e os dados foram salvos de fato no backend?

---

### B3. Editar e salvar na tela de Configurações ✅

**O que testamos:** o caminho de edição (não apenas criação inicial) — alterar um valor
na tela e confirmar que persiste.

**Passos:**
1. Na aba "Precificação" de `/configuracoes`, altere o "Valor da sua hora de trabalho"
   para um novo valor (ex: `35`)
2. Clique em "Salvar alterações"
3. Observe o indicador de status (ex: "Tudo salvo")
4. Recarregue a página (F5)

**Resultado esperado:** após o F5, o valor `35` continua aparecendo — confirma que foi
persistido no backend (não apenas no estado local do React, que se perderia no F5).

**Anote:** o valor persistiu após o F5?

---

### B4. Tentar salvar com nome vazio (validação) ❌

**Passos:**
1. Na aba "Perfil da empresa", apague o campo "Nome" (deixe vazio)
2. Tente salvar

**Resultado esperado:** mensagem de erro visível (vinda do backend: "O nome da empresa
é obrigatório", ou validação local equivalente) — o salvamento não deve ser bem
sucedido com nome vazio.

**Anote:** o erro apareceu? O salvamento foi bloqueado?

---

## Resumo final — preencha ao terminar

| Item | Resultado |
|------|-----------|
| A1-A12 (backend) | ⬜ Todos OK / ⬜ Falhas: ___ |
| SMOKE-1/2/3 (regressão Épico 1) | ⬜ OK / ⬜ Falhas: ___ |
| B1 — Configurações mostra dados reais (não mockados) | ⬜ OK / ⬜ Falhas: ___ |
| B2 — Onboarding cria perfil para usuário novo | ⬜ OK / ⬜ Falhas: ___ |
| B3 — edição em Configurações persiste após F5 | ⬜ OK / ⬜ Falhas: ___ |
| B4 — validação de nome vazio | ⬜ OK / ⬜ Falhas: ___ |

Se **tudo OK** → Épico 2 validado! Marcamos no BACKLOG.md e seguimos para o **P-015**
(início do Épico 3 — Insumos).

Se **algo não OK** → descreva o que viu (printscreen ajuda) e geramos um prompt de
correção pontual antes de avançar.
