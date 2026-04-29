# Keycloak для FinGuide

Keycloak разворачивается как отдельный Docker Compose stack рядом с `finguide-api` и `finguide-web`.
PostgreSQL обязателен именно для Keycloak: embedded/dev-file DB в production/demo стенде не используем.
Миграция финансовых данных FinGuide с H2 на PostgreSQL остаётся вне рамок задачи #18.

## Состав stack

- `keycloak-postgres` — PostgreSQL 16 для Keycloak, volume `keycloak-postgres-data`.
- `keycloak` — Keycloak 26, public realm `finguide`, public SPA client `finguide-web`.
- Custom theme `finguide` — стилизация login/account экранов под FinGuide.

## Быстрый старт на сервере

```bash
sudo mkdir -p /opt/finguide-keycloak
sudo cp -a deploy/keycloak/. /opt/finguide-keycloak/
sudo cp /opt/finguide-keycloak/.env.example /opt/finguide-keycloak/.env
sudo editor /opt/finguide-keycloak/.env # задать реальные секреты
cd /opt/finguide-keycloak
sudo docker compose --env-file .env up -d
sudo ./configure-realm.py
```

Публичный URL по умолчанию: `http://66.42.121.18/auth`.
Frontend callback: `http://66.42.121.18/fg/auth/callback`.

## Nginx

```nginx
location = /auth { return 301 /auth/; }
location /auth/ {
  proxy_pass http://127.0.0.1:3094/auth/;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header X-Forwarded-Host $host;
  proxy_set_header X-Forwarded-Prefix /auth;
}
```

## Realm/client

Автоматическая настройка:

```bash
cd /opt/finguide-keycloak
sudo ./configure-realm.py
```

Скрипт создаёт/обновляет:

1. Realm: `finguide`.
2. Client: `finguide-web`, type `OpenID Connect`, public client.
3. Flow: Standard flow enabled, PKCE required (`S256`).
4. Redirect URI: `http://66.42.121.18/fg/auth/callback`.
5. Post logout redirect URI: `http://66.42.121.18/fg/login`.
6. Web origins: `http://66.42.121.18`.
7. Roles: `user`, `admin`.
8. Audience mapper для `finguide-api`, чтобы access token проходил backend `aud` check.
9. Theme: Login theme `finguide`, Account theme `finguide`.

Для текущего HTTP demo URL скрипт ставит `sslRequired=none`. После перевода `/auth` на HTTPS нужно вернуть `sslRequired=external`.

## Backup/restore

Backup:

```bash
cd /opt/finguide-keycloak
sudo docker compose exec -T keycloak-postgres pg_dump -U "$KEYCLOAK_DB_USERNAME" "$KEYCLOAK_DB_NAME" > keycloak-backup.sql
```

Restore на остановленном/новом stack:

```bash
cd /opt/finguide-keycloak
sudo docker compose exec -T keycloak-postgres psql -U "$KEYCLOAK_DB_USERNAME" "$KEYCLOAK_DB_NAME" < keycloak-backup.sql
```

Секреты из `.env` не коммитим и не публикуем в логах.
