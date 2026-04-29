# Отчёт о тестировании #18 — Keycloak/OIDC

Дата: 2026-04-29 UTC.

## Что проверено

### Backend unit/integration

```bash
mvn -q test
```

Статус: passed.

Покрытие новой части:

- `KeycloakJwtAdapterTests` — маппинг `JWT.sub`, `email`, `preferred_username/name`, `realm_access.roles`.
- `AuthIntegrationTests` — `401` без Bearer token при `FINGUIDE_DEMO_MODE=false`, успешный доступ owner JWT, `403` при неверном `aud`, запрет чужого плана, доступ `admin`, наличие `bearerAuth` в Springdoc.
- существующие CRUD/read repository/controller/service tests для плана, доходов, расходов и целей.

### Docker Compose / Keycloak static verification

```bash
./scripts/verify-keycloak-compose.py
```

Статус: passed.

Проверяет, что `deploy/keycloak` содержит:

- сервисы `keycloak` и `keycloak-postgres`;
- persistent PostgreSQL volume;
- healthcheck/restart policy;
- `.env.example` только с placeholders;
- FinGuide theme для login/account;
- backup/restore и nginx notes в README;
- отсутствие committed secrets по ключевым маркерам.

### Документация GitHub Pages

```bash
/tmp/finguide-mkdocs-venv/bin/mkdocs build --strict
```

Статус: passed.

Проверены страницы:

- `docs/contract.md` — Keycloak-owned auth вместо backend password endpoints;
- `docs/backend-architecture-keycloak.md` — Docker Compose, PostgreSQL, OIDC/JWT, ownership checks;
- `docs/index.md` — текущий статус реализации.

### Backend auto smoke

```bash
mvn -q -DskipTests package
SERVER_PORT=18081 FINGUIDE_DEMO_MODE=true FINGUIDE_H2_CONSOLE_ENABLED=false \
  java -jar target/finguide-be-0.1.0-SNAPSHOT.jar
curl -fsS http://127.0.0.1:18081/api/v1
curl -fsS http://127.0.0.1:18081/api/v1/me
curl -fsS http://127.0.0.1:18081/api/v1/plans/current
curl -fsS http://127.0.0.1:18081/v3/api-docs
```

Статус: passed.

Проверено:

- API index отвечает envelope `{ data: ... }`;
- `GET /api/v1/me` в demo mode возвращает профиль `Александр Петров`;
- `GET /api/v1/plans/current` возвращает demo plan `22222222-2222-4222-8222-222222222222`;
- Springdoc публикует `bearerAuth` security scheme.

### Frontend unit/type/lint/build

```bash
bun run generate:api
bun run typecheck
bun run lint
bun run test
bun run build:fg
```

Статус: passed.

Покрытие новой части:

- `src/auth/oidc.test.ts` — хранение OIDC session, Bearer header, expiry/malformed storage cleanup, PKCE login/callback/logout, fallback SHA-256 для HTTP без `crypto.subtle`;
- build под `/fg/` успешно собирает auth routes `/login` и `/auth/callback`.

## Ограничения текущего стенда

- Docker/Compose и nginx route `/auth/` были установлены после явного подтверждения пользователя.
- Публичный demo пока работает по HTTP, поэтому Keycloak realm временно настроен с `sslRequired=none`. После HTTPS нужно вернуть `sslRequired=external`.
- Публичный backend сохраняет `FINGUIDE_DEMO_MODE=true`, чтобы не сломать demo-план во время перехода.

### Docker/Compose install and live Keycloak deployment

```bash
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker compose --env-file /opt/finguide-keycloak/.env -f /opt/finguide-keycloak/compose.yaml up -d
sudo /opt/finguide-keycloak/configure-realm.py
```

Статус: passed.

Фактические версии/состояние:

- Docker `29.4.1`;
- Docker Compose `v5.1.3`;
- `finguide-keycloak-postgres` healthy;
- `finguide-keycloak` healthy;
- bind: `127.0.0.1:3094 -> 8080`;
- nginx route `/auth/` добавлен, backup: `/etc/nginx/openclaw-backups/mtproxy-info.keycloak-20260429212152`.

Smoke:

```bash
curl -fsS http://66.42.121.18/auth/realms/finguide/.well-known/openid-configuration
curl -fsS 'http://66.42.121.18/auth/realms/finguide/protocol/openid-connect/auth?...'
curl -fsS http://66.42.121.18/fg/
curl -fsS http://66.42.121.18/finguide-api/api/v1
```

Статус: passed.

Проверено:

- public issuer `http://66.42.121.18/auth/realms/finguide`;
- login page отдаётся и содержит FinGuide/username/password markers;
- frontend `/fg/` доступен;
- backend `/finguide-api/api/v1` доступен.

Примечание: текущий public demo работает по HTTP, поэтому realm настроен с `sslRequired=none`. При переводе `/auth` на HTTPS нужно вернуть `sslRequired=external`.

### Public frontend/backend deployment smoke after enabling OIDC

```bash
curl -fsS http://66.42.121.18/fg/
curl -fsS http://66.42.121.18/fg/login
curl -fsS http://66.42.121.18/auth/realms/finguide/.well-known/openid-configuration
curl -fsS http://66.42.121.18/finguide-api/api/v1/plans/current
curl -fsS http://66.42.121.18/finguide-api/api/v1/me
curl -fsS http://66.42.121.18/finguide-api/v3/api-docs
```

Статус: passed.

Проверено:

- `/fg/` и `/fg/login` отдают SPA;
- production frontend bundle содержит `VITE_FINGUIDE_AUTH_ENABLED=true`, issuer `http://66.42.121.18/auth/realms/finguide`, client `finguide-web`;
- Keycloak discovery отдаёт issuer `http://66.42.121.18/auth/realms/finguide`;
- Keycloak login page доступна;
- backend `/api/v1/plans/current`, `/api/v1/me`, `/v3/api-docs` доступны за `/finguide-api/`;
- Docker Compose контейнеры `finguide-keycloak` и `finguide-keycloak-postgres` healthy.
