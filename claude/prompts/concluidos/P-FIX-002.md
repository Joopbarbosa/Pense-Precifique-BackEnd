# P-FIX-002 — Adicionar proteção de rotas no frontend (ProtectedRoute)

**Camada:** front
**Contexto:** correção pontual de infraestrutura, identificada na validação do Épico 1
(item B1) — não bloqueia P-XXX numerados, mas é pré-requisito de segurança para todas as
páginas internas dos épicos 2-8

---

## Contexto para o Claude Code

Durante a validação do Épico 1, identificamos que **qualquer rota interna do sistema
(ex: `/dashboard`, `/clientes`, `/orcamentos`, etc.) pode ser acessada digitando a URL
diretamente no navegador, mesmo sem estar autenticado** — o conteúdo (ainda que
mockado/estático) é exibido normalmente, sem redirecionar para `/login`.

O backend já está correto: rotas como `/usuarios/me` retornam `401` sem token válido
(confirmado nos testes A8/A10 do roteiro de validação). O que falta é o **frontend**
verificar, antes de renderizar qualquer página interna, se existe um usuário autenticado
— e redirecionar para `/login` caso contrário.

**Você atualmente está usando (de P-009):**
- `src/store/authStore.ts` — store Zustand com `token`, `isAuthenticated()`, `logout()`
- `src/services/api.ts` — axios com interceptor que já lida com `401` em chamadas de API
  (mas isso só age DEPOIS de uma requisição falhar — não impede a renderização inicial
  da página)

**O que esta tarefa resolve:** criar um componente `ProtectedRoute` (ou
`PrivateRoute`) que envolve as rotas internas em `src/routes/index.tsx`, verificando
`useAuthStore.getState().isAuthenticated()` (ou o hook equivalente) antes de renderizar
— se não autenticado, redireciona para `/login` via `<Navigate>` do React Router.

**Cenário de aceite (equivalente ao B1 do roteiro de validação):**
- Sem token no Local Storage, acessar `http://localhost:3000/dashboard` diretamente →
  deve redirecionar para `/login`
- Com token válido, acessar `/dashboard` → carrega normalmente (como já está hoje)
- Páginas públicas (`/login`, `/cadastro`) continuam acessíveis sem token

---

## Prompt

Cole este prompt no Claude Code:

---

Adicione proteção de rotas ao frontend. Antes de editar, **leia `src/routes/index.tsx`**
para entender a estrutura de rotas atual (biblioteca usada — React Router provavelmente
— e como as rotas estão organizadas).

### 1. Crie `src/components/shared/ProtectedRoute.tsx`

```tsx
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

export default function ProtectedRoute() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
```

Ajuste o caminho do import de `authStore` conforme a localização real (verifique se é
`../../store/authStore` ou outro caminho relativo correto a partir de
`src/components/shared/`).

Se `useAuthStore` não tiver `isAuthenticated()` como método de seleção direta (ex: se
for `isAuthenticated: () => boolean` chamado como função), adapte a sintaxe do seletor
Zustand adequadamente — o importante é o resultado: `true`/`false` indicando se há
token válido armazenado.

### 2. Edite `src/routes/index.tsx`

Reestruture as rotas para que todas as páginas internas (não-autenticação) fiquem
**dentro** de um elemento `<Route element={<ProtectedRoute />}>` pai. Por exemplo, se
hoje a estrutura é algo como:

```tsx
<Routes>
  <Route path="/login" element={<LoginPage />} />
  <Route path="/cadastro" element={<CadastroPage />} />
  <Route path="/dashboard" element={<DashboardPage />} />
  <Route path="/clientes" element={<ClientesPage />} />
  {/* ...demais rotas internas... */}
</Routes>
```

Deve passar a ser:

```tsx
<Routes>
  {/* Rotas públicas */}
  <Route path="/login" element={<LoginPage />} />
  <Route path="/cadastro" element={<CadastroPage />} />

  {/* Rotas protegidas */}
  <Route element={<ProtectedRoute />}>
    <Route path="/dashboard" element={<DashboardPage />} />
    <Route path="/clientes" element={<ClientesPage />} />
    {/* ...demais rotas internas... */}
  </Route>
</Routes>
```

**Identifique TODAS as rotas internas existentes** (Dashboard, Clientes, Orçamentos —
todas as sub-rotas, Insumos, Produtos, Produção, Configurações, etc.) e mova-as para
dentro do bloco protegido. Mantenha **apenas** `/login`, `/cadastro`, e
`/onboarding` (se já existir como rota) como públicas — onboarding é parte do fluxo
pós-cadastro, mas avalie: se o onboarding também exige um usuário já criado/autenticado
(token retornado no cadastro), ele deveria estar dentro do bloco protegido também. Use
seu julgamento com base no que `CadastroPage.tsx` faz após o sucesso (ela chama
`setAuth` antes de redirecionar, conforme P-009) — se sim, `/onboarding` também deve
estar protegido.

Não altere a ordem/estrutura de rotas além do necessário para esse agrupamento — apenas
envolva as rotas internas no `<ProtectedRoute />`.

### 3. Comportamento de redirecionamento pós-login (opcional, mas recomendado)

Se for simples de adicionar sem grandes mudanças: ao redirecionar para `/login` por
falta de autenticação, guarde a rota que o usuário tentou acessar (ex: via `state` do
`<Navigate>`: `<Navigate to="/login" state={{ from: location }} replace />`), para que
`LoginPage.tsx` possa, após login bem-sucedido, redirecionar de volta para essa rota em
vez de sempre ir para `/dashboard`. Isso é uma melhoria de UX — **se a alteração em
`LoginPage.tsx` for trivial** (poucas linhas), implemente; caso contrário, deixe um
comentário `// TODO: redirecionar para state.from após login` e não modifique
`LoginPage.tsx` nesta tarefa (evitar escopo grande em uma correção pontual).

### Convenções do projeto a seguir:
- Componente em `src/components/shared/` (ver `ESTRUTURA.md`, pasta `components/shared/`)
- Não altere layout/estilo de nenhuma página

Após editar, rode `npm run build` para garantir que não há erros de TypeScript, e suba
`docker compose up --build` (ou `npm run dev` para teste rápido) para testar
manualmente:

1. Limpe o Local Storage (DevTools → Application/Armazenamento → Local Storage → limpar)
2. Acesse `http://localhost:3000/dashboard` diretamente → deve redirecionar para
   `/login`
3. Repita para outras rotas internas (ex: `/clientes`, se existir)
4. Faça login normalmente → deve acessar `/dashboard` (ou a rota protegida) sem
   problemas
5. Confirme que `/login` e `/cadastro` continuam acessíveis sem login

---

## Checklist de validação

Antes de marcar como concluído, validar:
- [ ] `ProtectedRoute.tsx` criado em `src/components/shared/`
- [ ] Todas as rotas internas (Dashboard, Clientes, Insumos, Produtos, Produção,
      Orçamentos e sub-rotas, Configurações) envolvidas em `<ProtectedRoute />`
- [ ] `/login` e `/cadastro` permanecem acessíveis sem autenticação
- [ ] Acessar rota interna sem token → redireciona para `/login`
- [ ] Acessar rota interna com token válido → funciona normalmente (sem regressão)
- [ ] `npm run build` sem erros
- [ ] `docker compose up --build` sobe sem erros

## Commit

```bash
git add .
git commit -m "fix(frontend): adiciona ProtectedRoute para proteger rotas internas sem autenticação"
```

## Ao concluir

Informe "P-FIX-002 concluído" — retomamos a Parte B do roteiro de Validação do Épico 1
a partir do B1 (reteste rápido) e seguimos B2-B6.
