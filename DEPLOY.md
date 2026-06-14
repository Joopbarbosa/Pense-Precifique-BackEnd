# Deploy — Pense & Precifique

> **Status:** projeto rodando local.
> Este guia entra em ação quando a VPS for contratada.
> A Skill `docker-infra` vai gerar a configuração completa de Nginx + SSL + CI/CD.

---

## Pré-requisitos (quando chegar a hora)

- VPS Ubuntu 22.04+ (DigitalOcean, Hostinger, Contabo...)
- Domínio apontando para o IP da VPS
- Docker + Docker Compose instalados na VPS

---

## O que a Skill `docker-infra` vai gerar

- `docker-compose.prod.yml` com Nginx reverse proxy
- Configuração SSL com Let's Encrypt (Certbot)
- Script de deploy (`deploy.sh`)
- Configuração de firewall (ufw)
- CI/CD básico (GitHub Actions ou manual)

---

## Rodar local agora

```bash
# 1. Copiar variáveis de ambiente
cp .env.example .env
# Preencher .env com seus valores

# 2. Subir banco + backend + frontend
docker-compose up --build

# Acessos:
# Frontend:  http://localhost:3000
# Backend:   http://localhost:8080
# Swagger:   http://localhost:8080/swagger-ui.html
# Banco:     localhost:5432 (via DBeaver ou psql)
```

---

## Quando contratar a VPS

Abra uma sessão com o Claude e diga:
> "Quero configurar o deploy do Pense & Precifique na VPS — tenho Ubuntu 22.04 na [provedor]."

A Skill `docker-infra` assume a partir daí.
