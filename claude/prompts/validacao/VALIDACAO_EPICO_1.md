# Roteiro de Validação — Épico 1 (Autenticação)
### Versão didática — explicando o que cada teste faz e por quê

> Este roteiro testa o fluxo completo de autenticação do sistema: cadastro de usuário,
> login, consulta de perfil, troca de senha, e proteção de rotas (ninguém acessa dados
> sem estar logado).
>
> **Pré-requisito:** `docker compose up --build` já rodando (você confirmou que os 3
> containers estão `Up`). Mantenha esse terminal aberto e use **outro terminal** (ou
> outra aba) para os comandos abaixo.

---

## Conceitos rápidos antes de começar

**O que é `curl`?**
É um programa de linha de comando que faz requisições HTTP — a mesma coisa que seu
navegador faz quando você acessa um site, mas sem interface visual. Usamos ele para
"conversar" diretamente com o backend (`http://localhost:8080`), sem precisar do
frontend.

**O que é "status code"?**
É um número que o servidor devolve junto com a resposta, indicando o resultado:
- `200` = OK (sucesso)
- `201` = Created (algo foi criado com sucesso, ex: um novo usuário)
- `400` = Bad Request (erro do cliente — dados inválidos, regra de negócio violada)
- `401` = Unauthorized (você não está autenticado / token inválido ou ausente)
- `500` = erro interno do servidor (bug — não deveria aparecer nos testes abaixo)

**O que é o `-i` no curl?**
Faz o curl mostrar os *headers* da resposta (incluindo a primeira linha, que tem o
status code), além do corpo (JSON). Sem `-i`, você só veria o JSON, sem saber se foi
`200` ou `400`.

**O que é JWT / token?**
Quando você faz login com sucesso, o backend gera um "crachá digital" (o token JWT) —
uma string longa e criptografada que prova quem você é. Em vez de logar a cada
requisição, você manda esse crachá no header `Authorization: Bearer <token>` e o
backend confia nele (até ele expirar, em 24h neste projeto).

---

## PARTE A — Testando o Backend (via terminal)

### A1. Cadastrar um novo usuário ✅ (já feito)

**O que testamos:** criar uma conta nova funciona e retorna um token.

```bash
curl -i -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senha1234","confirmarSenha":"senha1234"}'
```

**Quebrando o comando:**
- `-X POST` → o tipo de requisição é POST (estamos *enviando* dados para criar algo)
- `-H "Content-Type: application/json"` → avisa o servidor que estamos enviando JSON
- `-d '{...}'` → o corpo da requisição (os dados do cadastro)

**Resultado esperado:** `HTTP/1.1 201` + JSON com `token`, `tipo: "Bearer"`, `usuarioId`,
`email`, `expiresIn: 86400000`

**Resultado obtido:** ✅ `201`, token presente, `expiresIn: 86400000` — passou.

---

### A2. Tentar cadastrar o MESMO e-mail de novo ❌ (deve dar erro)

**O que testamos:** o sistema não permite dois usuários com o mesmo e-mail (regra de
negócio + constraint `UNIQUE` no banco).

```bash
curl -i -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senha1234","confirmarSenha":"senha1234"}'
```

Note que é **o mesmo comando do A1**, exatamente o mesmo e-mail — só estamos repetindo
de propósito.

**Resultado esperado:** `HTTP/1.1 400` + JSON contendo `"message":"E-mail já cadastrado"`

**O que fazer:** rode o comando, copie aqui o resultado (status + JSON).

---

### A3. Cadastrar com senhas que não coincidem ❌

**O que testamos:** se você digita a senha errado na confirmação, o sistema avisa em vez
de criar a conta com dados inconsistentes.

```bash
curl -i -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"outro@teste.com","senha":"senha1234","confirmarSenha":"diferente123"}'
```

Note: aqui o e-mail é diferente (`outro@teste.com`) — não tem relação com o A2, é só
para isolar o teste de "senhas diferentes".

**Resultado esperado:** `HTTP/1.1 400` + `"message":"As senhas não coincidem"`

---

### A4. Cadastrar com e-mail inválido e senha curta ❌

**O que testamos:** validações de formato — antes mesmo de checar regras de negócio, o
sistema rejeita dados malformados (e-mail sem `@`, senha menor que 8 caracteres).

```bash
curl -i -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"email-invalido","senha":"123","confirmarSenha":"123"}'
```

**Resultado esperado:** `HTTP/1.1 400` + JSON com `"message":"Erro de validação"` e um
campo `fieldErrors` mostrando o problema de cada campo, por exemplo:
```json
"fieldErrors": {
  "email": "E-mail inválido",
  "senha": "A senha deve ter no mínimo 8 caracteres"
}
```

---

### A5. Fazer login com o usuário criado no A1 ✅

**O que testamos:** login funciona com as credenciais corretas e retorna um token novo.

```bash
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senha1234"}'
```

**Resultado esperado:** `HTTP/1.1 200` + mesmo formato de JSON do cadastro (token, etc.)

> 💡 Note a diferença: cadastro retorna `201` (criou algo novo), login retorna `200`
> (apenas confirmou e devolveu um token — nada novo foi criado).

---

### A6. Login com senha errada ❌

**O que testamos:** senha incorreta é rejeitada.

```bash
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senhaErrada"}'
```

**Resultado esperado:** `HTTP/1.1 400` + `"message":"E-mail ou senha inválidos"`

---

### A7. Login com e-mail que não existe ❌

**O que testamos:** uma medida de segurança importante. O sistema **não diz** "esse
e-mail não existe" — ele dá a **mesma mensagem genérica** do A6. Isso evita que alguém
malicioso descubra quais e-mails têm conta só testando logins (esse tipo de ataque se
chama "enumeração de usuários").

```bash
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"naoexiste@teste.com","senha":"qualquercoisa"}'
```

**Resultado esperado:** `HTTP/1.1 400` + `"message":"E-mail ou senha inválidos"` —
**a mesma mensagem exata do A6**, mesmo o motivo sendo diferente (aqui o e-mail nem
existe; no A6 o e-mail existe mas a senha está errada).

---

### A8. Tentar acessar dados protegidos SEM fazer login ❌

**O que testamos:** o endpoint `/usuarios/me` (que mostra seus dados de perfil) exige
autenticação. Sem token, o acesso é bloqueado.

```bash
curl -i http://localhost:8080/usuarios/me
```

Note que aqui não tem `-X POST` nem `-d` — é uma requisição `GET` simples (o padrão do
curl quando você não especifica), e sem nenhum header de autenticação.

**Resultado esperado:** `HTTP/1.1 401`

> 💡 Esse é o teste mais importante de segurança do épico: garante que ninguém vê dados
> de ninguém sem estar autenticado.

---

### A9. Acessar dados protegidos COM login válido ✅

**O que testamos:** com um token válido no header, o acesso funciona e retorna os dados
do usuário correto (e nada sensível, como a senha criptografada).

Este comando é um pouco diferente — ele faz **duas coisas em sequência**:
1. Faz login e guarda o token recebido na variável `TOKEN`
2. Usa esse `$TOKEN` para acessar `/usuarios/me`

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senha1234"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -i http://localhost:8080/usuarios/me -H "Authorization: Bearer $TOKEN"
```

**Quebrando o primeiro comando:**
- `TOKEN=$(...)` → tudo dentro de `$(...)` é executado, e o **resultado** (texto que
  sairia na tela) é guardado na variável `TOKEN`
- `-s` → modo "silencioso" do curl (não mostra informações extras, só o JSON puro — isso
  é necessário para o `python3` conseguir interpretar a resposta)
- `| python3 -c "..."` → o `|` (pipe) pega a saída do curl e "entrega" para o Python,
  que extrai apenas o campo `token` do JSON

**Quebrando o segundo comando:**
- `-H "Authorization: Bearer $TOKEN"` → manda o crachá digital no header. O `$TOKEN`
  é substituído pelo valor guardado acima.

**Resultado esperado:** `HTTP/1.1 200` + JSON:
```json
{
  "id": "...",
  "email": "validacao.epico1@teste.com",
  "ativo": true,
  "createdAt": "..."
}
```
**Importante:** o campo `senhaHash` (a senha criptografada) **NUNCA** deve aparecer
aqui — se aparecer, é uma falha de segurança grave.

---

### A10. Acessar dados protegidos com token INVÁLIDO ❌

**O que testamos:** um token "forjado" (que não foi gerado pelo sistema) é rejeitado.

```bash
curl -i http://localhost:8080/usuarios/me -H "Authorization: Bearer token.invalido.123"
```

**Resultado esperado:** `HTTP/1.1 401`

---

### A11. Trocar senha informando a senha ATUAL errada ❌

**O que testamos:** para trocar a senha, você precisa confirmar que conhece a senha
atual (segurança extra — se alguém pegar seu celular desbloqueado, não consegue trocar
sua senha sem saber a atual).

```bash
curl -i -X PUT http://localhost:8080/usuarios/me/senha \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"senhaAtual":"senhaErrada","novaSenha":"novaSenha123","confirmarNovaSenha":"novaSenha123"}'
```

Note: `-X PUT` (em vez de POST) — convenção HTTP para "atualizar algo que já existe"
(estamos atualizando a senha de um usuário existente).

O `$TOKEN` aqui é o mesmo guardado no A9 — se você fechou o terminal entre um teste e
outro, precisa gerar de novo (rode o bloco do A9 de novo antes).

**Resultado esperado:** `HTTP/1.1 400` + `"message":"Senha atual incorreta"`

---

### A12. Trocar senha com confirmação que não coincide ❌

**O que testamos:** mesma lógica do A3, mas para troca de senha em vez de cadastro.

```bash
curl -i -X PUT http://localhost:8080/usuarios/me/senha \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"senhaAtual":"senha1234","novaSenha":"novaSenha123","confirmarNovaSenha":"diferente456"}'
```

**Resultado esperado:** `HTTP/1.1 400` + `"message":"As senhas não coincidem"`

---

### A13. Trocar senha com sucesso e confirmar que a senha mudou de fato ✅

**O que testamos:** o caminho feliz completo — e confirmamos que, depois da troca, a
senha ANTIGA não funciona mais e a NOVA funciona. Isso prova que o sistema realmente
salvou a alteração no banco (não só retornou "ok" sem fazer nada).

Este bloco roda **3 comandos em sequência**:

```bash
# 1. Troca a senha (de "senha1234" para "novaSenha123")
curl -i -X PUT http://localhost:8080/usuarios/me/senha \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"senhaAtual":"senha1234","novaSenha":"novaSenha123","confirmarNovaSenha":"novaSenha123"}'

# 2. Tenta logar com a senha ANTIGA — deve FALHAR agora
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"senha1234"}'

# 3. Tenta logar com a senha NOVA — deve FUNCIONAR
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"validacao.epico1@teste.com","senha":"novaSenha123"}'
```

**Resultado esperado:**
- Comando 1 → `HTTP/1.1 200`
- Comando 2 → `HTTP/1.1 400` (`"E-mail ou senha inválidos"`)
- Comando 3 → `HTTP/1.1 200` (com token novo)

---

## Como você vai rodar isso

Sugestão: vá **um teste por vez** (A2, depois A3, depois A4...), colando o resultado
aqui antes de ir para o próximo. Assim, se algo der errado no meio, identificamos
exatamente onde — em vez de rodar tudo e depois tentar achar a agulha no palheiro.

Não tem problema nenhum em perguntar "por que esse resultado veio assim?" a qualquer
momento — esse roteiro existe tanto para validar o sistema quanto para você ir
entendendo como as peças se conectam.

---

## PARTE B — Testando o Frontend (navegador)

> Status da Parte A: ✅ todos os 13 testes (A1-A13) passaram. Backend do Épico 1 validado.

Agora vamos testar a mesma coisa, mas pela interface visual real — as telas que o P-009
conectou à API. A ideia é confirmar que tudo que funciona "por baixo dos panos" (via
curl) também funciona através da tela que a artesã vai usar de fato.

**Abra o navegador em:** `http://localhost:3000`

---

### Antes de começar: abra o DevTools

No navegador, aperte `F12` (ou `Ctrl+Shift+I`) para abrir as Ferramentas do
Desenvolvedor. Vá na aba **"Network"** (Rede). Deixe essa aba aberta durante os testes —
você vai ver, em tempo real, cada requisição que o frontend faz para o backend (igual os
`curl` que fizemos, mas agora disparados pelos cliques na tela).

> 💡 Isso é uma ferramenta que todo desenvolvedor frontend usa o tempo todo — vale a
> pena se acostumar com ela desde já.

---

### B1. Acessar uma rota protegida sem estar logado

**O que testamos:** o equivalente "visual" do A8 — se você não está logado, não deve
conseguir ver páginas internas do sistema (dashboard, etc.).

**Passos:**
1. Se o navegador já tiver algum login salvo de testes anteriores, limpe primeiro:
   - No DevTools, vá em **Application** (ou "Armazenamento") → **Local Storage** →
     `http://localhost:3000` → clique com o botão direito → **Clear** (ou delete a
     chave `auth-storage`, se existir)
2. Digite na barra de endereço: `http://localhost:3000/dashboard` (ou qualquer rota
   interna — ajuste se o nome da rota for diferente)
3. Observe o que acontece

**Resultado esperado:** você deve ser redirecionado para a tela de **Login**, não deve
conseguir ver o conteúdo do dashboard.

**Anote:** o que apareceu na tela? Foi redirecionado para login automaticamente, ou
ficou alguma tela em branco/erro?

---

### B2. Tela de Cadastro — criar uma conta nova

**O que testamos:** o equivalente do A1, mas pela tela.

**Passos:**
1. Acesse a tela de Cadastro (link "Criar conta"/"Cadastre-se" a partir da tela de
   Login, ou acesse a URL direto, ex: `http://localhost:3000/cadastro`)
2. Preencha:
   - E-mail: `front.teste@teste.com` (um e-mail **novo**, diferente dos que já usamos
     via curl)
   - Senha: `senha1234`
   - Confirmar senha: `senha1234`
3. Clique no botão de submeter/cadastrar
4. **Observe na aba Network do DevTools:** deve aparecer uma requisição `POST` para
   `/auth/register` com status `201`

**Resultado esperado:** a tela deve reagir ao sucesso — geralmente redirecionando para
outra página (dashboard, onboarding, ou o que estiver configurado). Nenhuma mensagem de
erro deve aparecer.

**Anote:** para onde você foi redirecionado? A transição foi suave (sem tela branca,
sem erro no console)?

---

### B3. Tela de Cadastro — tentar cadastrar o mesmo e-mail de novo

**O que testamos:** o equivalente do A2 — o erro do backend deve aparecer de forma
visível na tela, não como uma tela branca ou erro técnico no console.

**Passos:**
1. Volte para a tela de Cadastro (se foi redirecionado, navegue de volta — ou abra
   `http://localhost:3000/cadastro` em uma nova aba)
2. Preencha de novo com o **mesmo e-mail** do B2 (`front.teste@teste.com`)
3. Submeta

**Resultado esperado:** uma mensagem de erro visível na tela (toast, texto vermelho,
alerta, etc.) com algo como "E-mail já cadastrado" — a mesma mensagem que vimos no A2,
agora exibida para o usuário.

**Anote:** a mensagem apareceu? Em que formato (toast no canto da tela? texto abaixo do
campo? um alerta no topo)?

---

### B4. Tela de Cadastro — validação de campos

**O que testamos:** o equivalente do A4 — campos com formato inválido devem mostrar erro
antes mesmo de tentar enviar, ou ao receber a resposta do backend.

**Passos:**
1. Na tela de Cadastro, tente preencher:
   - E-mail: `email-sem-arroba` (sem `@`)
   - Senha: `123` (menor que 8 caracteres)
   - Confirmar senha: `123`
2. Tente submeter

**Resultado esperado:** mensagens de erro nos campos específicos (e-mail inválido, senha
muito curta) — seja por validação do próprio formulário (antes de enviar) ou pela
resposta do backend (`fieldErrors` do A4).

**Anote:** os erros apareceram por campo, ou só uma mensagem genérica?

---

### B5. Logout (ou limpar sessão) e fazer Login

**O que testamos:** o equivalente do A5 — login funcionando pela tela, e a sessão
"lembrando" que você está logado.

**Passos:**
1. Se houver um botão de **Logout** visível em algum menu, clique nele. Se não houver,
   limpe o Local Storage de novo (como no B1, passo 1)
2. Você deve ser levado/redirecionado para a tela de Login
3. Faça login com o e-mail/senha do B2 (`front.teste@teste.com` / `senha1234`)
4. **Observe na aba Network:** requisição `POST /auth/login` com status `200`

**Resultado esperado:** login bem-sucedido, redirecionamento para a área logada
(dashboard ou equivalente)

**Verificação extra — o token está sendo enviado?**
5. Ainda na aba Network, clique em qualquer requisição feita **depois** do login (ex: se
   o dashboard carrega algum dado automaticamente)
6. Procure pela seção **"Headers"** (ou "Request Headers") dessa requisição
7. Deve existir um header `Authorization: Bearer eyJ...` (o token)

**Anote:** o login funcionou? O header `Authorization` apareceu nas requisições
seguintes?

---

### B6. Login com senha errada

**O que testamos:** o equivalente do A6 — erro de credenciais exibido na tela.

**Passos:**
1. Faça logout (ou limpe a sessão)
2. Na tela de Login, tente entrar com o e-mail do B2 mas senha errada
   (`front.teste@teste.com` / `senhaErrada`)

**Resultado esperado:** mensagem de erro visível ("E-mail ou senha inválidos"), sem
travar a tela, sem tela branca.

**Anote:** o erro apareceu corretamente?

---

## Resumo final — preenchido

| Item | O que observou | OK? |
|------|-----------------|-----|
| B1 — rota protegida sem login redireciona | Redirecionou para /login (após P-FIX-002 + rebuild Docker) | ✅ |
| B2 — cadastro novo funciona e redireciona | Cadastro criado, redirecionou corretamente | ✅ |
| B3 — cadastro duplicado mostra erro do backend | Erro exibido na tela | ✅ |
| B4 — validação de campos no cadastro | Erros de validação exibidos | ✅ |
| B5 — login funciona + sessão persiste (F5) | Login ok, Dashboard carregado, F5 manteve sessão | ✅ |
| B6 — login com senha errada mostra erro | Erro exibido | ✅ |

## ÉPICO 1 — VALIDAÇÃO CONCLUÍDA ✅ (2026-06-14)

Parte A (13/13) + Parte B (6/6) — todos os testes passaram.

**Correções aplicadas durante a validação:**
- P-FIX-001: erros de build TypeScript (débito técnico pré-existente do frontend)
- P-FIX-002: ProtectedRoute — rotas internas agora exigem autenticação

**Smoke checks confirmados para reuso futuro:**
- SMOKE-1: Login retorna token válido ✅
- SMOKE-2: rota protegida sem token → 401 (backend) / redireciona /login (frontend) ✅
- SMOKE-3: rota protegida com token válido → 200 / carrega normalmente ✅
