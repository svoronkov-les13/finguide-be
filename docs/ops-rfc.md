# RFC: Ops-периметр FinGuide

- **Статус:** предложено
- **Дата:** 2026-05-18
- **Область:** путь от текущего демо-стенда к production-ready эксплуатации FinGuide: backend, frontend, Keycloak, данные, наблюдаемость, CI/CD и релизный процесс.
- **Владелец:** Ops / platform track
- **Связанные страницы:** [Operations и CI/CD](operations.md), [RFC Kubernetes demo/dev на finguide.les13.tech](winemap-k8s-dev-prod-rfc.md), [Текущее состояние](status.md), [Roadmap](roadmap.md)

## 1. Краткое резюме

У FinGuide уже есть рабочий публичный демо-стенд: frontend под `/fg/`, Spring Boot backend под `/finguide-api`, Keycloak realm и автоматический deploy через self-hosted GitHub Actions runner. Это хорошо для быстрой продуктовой итерации, но текущий Ops-периметр остаётся демонстрационным: embedded H2, частично ручная серверная конфигурация, ограниченные smoke-проверки, нет формализованных backup/restore процедур, нет наблюдаемости уровня инцидентов, нет отдельного dev/pre-prod окружения и нет полной политики секретов.

Новый целевой сервер для production-like demo — `finguide.les13.tech`. На нём размещается публичный демо-стенд, близкий к production по эксплуатационной дисциплине: HTTPS/TLS, PostgreSQL + Flyway, backup/restore, секреты, наблюдаемость и rollback. Позже на этом же сервере должен появиться dev/pre-prod стенд. Сервер не должен считаться выделенным только под FinGuide: там будут и другие проекты, поэтому FinGuide обязан жить в ограниченном resource budget.

Этот RFC предлагает эволюционный план без big-bang миграции. Сначала фиксируем инфраструктурную базу: инвентаризацию, секреты, резервное копирование, health/readiness, логи и runbooks. Затем переводим stateful-часть на PostgreSQL + Flyway, разделяем demo и dev/pre-prod, усиливаем CI/CD gate и только после этого добавляем полноценные SLO/alerts, blue-green/canary-подходы и IaC.

Отдельный [Kubernetes RFC для finguide.les13.tech](winemap-k8s-dev-prod-rfc.md) описывает конкретный профиль размещения на новом сервере. Он не отменяет этот документ, а реализует тот же Ops baseline через Kubernetes namespaces/Helm.

## 2. Текущее состояние

### 2.1 Публичные адреса

- Frontend legacy demo: `http://66.42.121.18/fg/`.
- Целевой frontend demo: `https://finguide.les13.tech/`.
- Backend API legacy: `http://66.42.121.18/finguide-api/api/v1`.
- Целевой Backend API: `https://finguide.les13.tech/api/v1` или `https://api.finguide.les13.tech/api/v1`.
- Backend health legacy: `http://66.42.121.18/finguide-api/actuator/health`.
- Swagger UI legacy: `http://66.42.121.18/finguide-api/swagger-ui.html`.
- Springdoc OpenAPI JSON legacy: `http://66.42.121.18/finguide-api/v3/api-docs`.
- Keycloak realm legacy: `http://66.42.121.18/auth/realms/finguide`.
- Документация: `https://svoronkov-les13.github.io/finguide-be/`.

Целевые публичные endpoints должны работать по HTTPS. Keycloak issuer и redirect URI должны быть точными для каждого окружения.

### 2.2 Runtime и deploy

- Backend: Java 21, Spring Boot 3.3, Spring Security OAuth2 Resource Server, Spring Data JDBC.
- Текущее хранение данных: embedded H2/PostgreSQL profile; схема через Liquibase, demo seed через `data.sql`.
- Текущий backend deploy: self-hosted GitHub Actions runner на legacy сервере, systemd service `finguide-api.service`, runtime jar `/opt/finguide-api/finguide-be.jar`.
- Текущий frontend deploy: self-hosted GitHub Actions runner на legacy сервере, статические файлы в `/var/www/mtproxy-info/fg`.
- Целевой deploy: перенос публичного demo на `finguide.les13.tech`, предпочтительно через container/Helm или через равноценно документированный hardened VM profile.
- Deploy документации: GitHub Pages через `mkdocs build --strict`.

### 2.3 Что уже хорошо

- Deploy из `main` автоматизирован для backend, frontend и документации.
- Backend deploy включает backup jar-файла и public/local health smoke.
- Есть OpenAPI coverage guard, который предотвращает незаметный рост contract drift.
- Frontend имеет build/type gates и интеграцию с real API для ключевых экранов.
- Документация уже фиксирует runtime, roadmap и архитектуру.

### 2.4 Основные пробелы

1. **Хранение данных:** H2 подходит для demo, но не подходит для durable production-like state.
2. **Backup/restore:** нет описанных RPO/RTO, расписания backup, restore drill и политики шифрования backup.
3. **Окружения:** нет dev/pre-prod окружения, отделённого от публичного demo.
4. **Секреты:** нет единой модели владения GitHub secrets, service credentials, Keycloak secrets и их ротацией.
5. **Наблюдаемость:** deploy проверяет только health; нет формальных alerts, dashboards, SLO, error budget и synthetic journeys.
6. **Безопасность:** legacy endpoints работают по HTTP, Keycloak realm demo-grade, server hardening не зафиксирован как код/runbook.
7. **Change management:** `main` auto-deploy напрямую; это быстро, но рискованно после появления реальных пользовательских данных.
8. **Infrastructure drift:** часть серверного состояния ручная; повторяемость после rebuild не гарантирована.

## 3. Цели

### 3.1 Основные цели

- Определить практичный Ops roadmap от demo к production-ready эксплуатации.
- Сохранить скорость разработки и снизить риск потери данных или сломанных релизов.
- Ввести durable persistence через migrations, backups и restore verification.
- Сделать deploy и rollback явными, документированными и проверяемыми.
- Добавить наблюдаемость, которая отвечает на вопросы: продукт доступен, auth работает, пользователи не заблокированы, последний релиз ничего не ухудшил?
- Описать operational ownership так, чтобы другой инженер мог эксплуатировать систему без tribal knowledge.

### 3.2 Что не входит в этот RFC

- Переписывание приложения в microservices.
- Назначение Kubernetes единственным production path. Kubernetes допустим только как явно выбранный deployment profile, который выполняет этот Ops baseline.
- Enterprise-grade multi-region HA до появления production-нагрузки.
- Замена текущего CI/CD, если self-hosted Actions остаётся достаточным.
- Product analytics/BI; этот RFC про service observability и reliability.

## 4. Целевая архитектура

### 4.1 Окружения

Нужно зафиксировать три логических окружения, даже если первая реализация использует один физический сервер:

| Окружение | Назначение | Данные | Триггер deploy | Внешние пользователи |
| --- | --- | --- | --- | --- |
| `local-dev` | локальная работа инженера | disposable | ручной локальный запуск | нет |
| `dev` / `pre-prod` | проверка релиз-кандидата | seeded/synthetic | merge в staging branch или ручной workflow | только внутренние |
| `demo` / будущий `prod` | публичный production-like стенд | durable или близкие к durable данные | tagged release или approved deploy | да |

Первый обязательный шаг: опубликовать публичный production-like `demo` на `finguide.les13.tech` со своей DB/schema, отдельным Keycloak client и отдельной конфигурацией. Позже тот же сервер может принять `dev`/pre-prod как отдельное окружение, но с отдельными данными и credentials.

В Kubernetes RFC для `finguide.les13.tech` namespace `finguide-demo` соответствует публичному production-like demo, а `finguide-dev` — dev/pre-prod. Локальный инженерный `local-dev` остаётся вне сервера.

### 4.2 Runtime topology

Рекомендуемая ближайшая topology:

```text
Internet
  |
  v
nginx / reverse proxy
  |-- / или /fg/                 -> static frontend assets
  |-- /api/ или /finguide-api/   -> Spring Boot backend
  |-- /auth/                     -> Keycloak, если auth размещён на этом же сервере
  |-- /docs/ или GitHub Pages    -> документация

Spring Boot backend
  |-- PostgreSQL primary database
  |-- Keycloak OIDC issuer
  |-- structured logs to journald/Loki
  |-- metrics via Actuator/Prometheus endpoint

Backup job
  |-- pg_dump / filesystem metadata
  |-- encrypted off-host storage
  |-- restore verification job
```

Базовая рекомендация для первого production-ready milestone — hardened VM или Kubernetes без избыточной сложности: PostgreSQL, nginx/reverse proxy, TLS, backups, observability и понятный rollback. Kubernetes не должен обходить требования Phase 0–3: discovery, quotas, backups, secrets и smoke checks обязательны.

Если команда выбирает Kubernetes на `finguide.les13.tech`, профиль должен быть additive и isolated: только `finguide-*` namespaces/resources, отдельные demo/dev data services, conservative ResourceQuota/LimitRange и те же Ops gates из этого RFC. На сервере должен остаться headroom для будущих не-FinGuide проектов.

### 4.3 Хранение данных

Нужно перейти с embedded H2 на PostgreSQL через Flyway migrations.

Обязательные свойства:

- Application schema создаётся только через versioned migrations.
- H2 может остаться для tests и local/demo mode, но production-like profile не должен использовать in-memory state.
- Каждый deploy запускает migration validation до переключения runtime на новый artifact.
- Rollback policy различает code rollback и schema rollback:
  - предпочтительны backward-compatible migrations;
  - destructive migrations требуют явного expand/migrate/contract plan;
  - перед рискованной migration нужен backup snapshot.

Критерии приёмки:

- `SPRING_PROFILES_ACTIVE=prod` или эквивалентный production-like profile использует PostgreSQL.
- `mvn -B test` покрывает migration compatibility и repository behavior.
- Fresh DB создаётся только из migrations.
- Prod-like dump можно восстановить в dev/pre-prod, после чего backend стартует успешно.

### 4.4 Секреты и конфигурация

Конфигурация должна быть явной по окружениям.

Классы секретов:

- GitHub Actions secrets: deploy-related values, если они нужны.
- Backend runtime secrets: DB URL/user/password, OIDC issuer/client settings.
- Keycloak admin/bootstrap secrets.
- Backup encryption credentials.
- Будущие external integration secrets: email, notifications, storage.

Правила:

1. Не хранить secrets в repository, docs, shell history или issue comments.
2. Для каждого класса secrets должна быть процедура rotation.
3. Runtime secrets доступны только service user и deploy mechanism.
4. GitHub PAT для ручных действий должен быть short-lived или rotated сразу после использования.
5. При раскрытии secret нужно создавать incident note.

Минимальная реализация:

- systemd `EnvironmentFile=/etc/finguide-api/finguide-api.env` с permissions `0600`;
- документированный список required environment variables без значений;
- GitHub Actions использует repository/environment secrets, а не inline tokens;
- runbook `docs/runbooks/secrets-rotation.md`.

### 4.5 CI/CD release flow

CI/CD должен соответствовать Kubernetes RFC для `finguide.les13.tech`: demo не должен собираться заново из плавающей ветки после появления dev/pre-prod. В demo продвигается тот же image tag или artifact, который уже прошёл проверки в `finguide-dev`.

Рекомендуемый flow:

1. Pull request gate:
   - backend: `mvn -B test`, OpenAPI coverage guard, migration validation;
   - frontend: tests/typecheck/build;
   - infra: `helm template`, `helm diff`, namespace-scope check, policy check;
   - docs: `mkdocs build --strict`.
2. Build and publish:
   - собрать backend Docker image из `finguide-be`;
   - собрать frontend Docker image из `finguide-web` с environment-aware runtime/build config;
   - опубликовать images в registry, по умолчанию GitHub Container Registry (`ghcr.io`).
3. Deploy to dev/pre-prod:
   - автоматически deploy в `finguide-dev`, когда это окружение появится;
   - выполнить migration rehearsal, smoke tests и synthetic checks.
4. Promote to demo:
   - вручную или через approved workflow продвинуть те же image tags в `finguide-demo`;
   - перед demo migration выполнить backup preflight, когда данные станут значимыми;
   - запустить demo migration Job;
   - выполнить post-deploy smoke и принять rollback decision.

До появления `finguide-dev` допускается прямой deploy demo из `main`, но это только transitional mode. После появления dev/pre-prod публичный demo должен получать уже проверенный artifact/image, а не rebuild from moving branch.

Рекомендуемые workflow names:

- `backend-ci.yml`
- `backend-image.yml`
- `backend-deploy-dev.yml`
- `backend-promote-demo.yml`
- `frontend-ci.yml`
- `frontend-image.yml`
- `frontend-deploy-dev.yml`
- `frontend-promote-demo.yml`
- `infra-diff.yml`
- `docs-pages.yml`

### 4.6 Rollback

Rollback должен быть скучным: понятным, быстрым и проверенным.

Backend rollback:

- Хранить последние N jar artifacts в `/opt/finguide-api/releases/`.
- `/opt/finguide-api/current.jar` — symlink на активный release.
- Deploy создаёт новый release file, атомарно переключает symlink и перезапускает service.
- Rollback переключает symlink на previous known-good release и перезапускает service.

Frontend rollback:

- Хранить timestamped directories со статическими assets.
- `/var/www/mtproxy-info/fg` указывает на current release или восстанавливается из previous backup.
- Rollback сохраняет целостность assets и не смешивает старые/новые bundles.

Database rollback:

- Для compatible migrations предпочтительны forward fixes.
- Для destructive migrations обязателен backup snapshot и явный restore plan.
- Любой restore в demo/prod требует human approval и incident record.

### 4.7 Наблюдаемость

Минимальные сигналы:

- **Доступность:** HTTP uptime для frontend, backend `/actuator/health`, Keycloak realm discovery.
- **Корректность:** synthetic journey: загрузить app, получить demo/current plan, вызвать cashflow endpoint, проверить форму ответа.
- **Задержки:** p50/p95/p99 для backend requests по route family.
- **Ошибки:** 5xx rate, auth failures, failed migrations, failed deploys.
- **Ресурсы:** CPU, memory, disk, DB size, DB connections, service restarts.
- **Безопасность:** failed SSH attempts, expired certs, suspicious Keycloak admin events.

Suggested stack для текущего масштаба:

- Spring Boot Actuator metrics.
- Prometheus или Grafana Agent/Alloy для scraping local services.
- Loki для structured logs, если он уже доступен; иначе journald + logrotate как phase 1.
- Grafana dashboard с service health, latency, errors и deploy markers.
- Alerting в Telegram/email только для high-signal conditions.

Начальные alert rules:

| Alert | Threshold | Severity |
| --- | --- | --- |
| BackendDown | `/actuator/health` fails for 2 minutes | page |
| FrontendDown | frontend route fails for 2 minutes | page |
| KeycloakDown | realm discovery fails for 2 minutes | page |
| High5xxRate | 5xx > 2% for 10 minutes | page |
| DiskWillFill | disk > 85% | warn |
| BackupMissing | no successful backup in 26h | page |
| CertificateExpiring | TLS cert expires in <14d | warn |
| DeployFailed | GitHub Actions deploy failure | page |

### 4.8 Backup и restore

Backup не считается рабочим, пока restore не проверен.

Предложение по policy:

- RPO: сначала 24h, позже 1h при появлении real paid/customer data.
- RTO: сначала 4h, позже 1h.
- PostgreSQL full logical backup daily через `pg_dump`.
- Retention: 7 daily, 4 weekly, 6 monthly.
- Encryption: age/gpg или storage-provider encryption с ограниченными credentials.
- Storage: off-host object storage или другой controlled host; local-only backup недостаточен.
- Restore drill: monthly в dev/pre-prod или disposable DB.

Backup job должен писать:

- start/end timestamp;
- DB name и schema version;
- compressed size;
- checksum;
- storage target;
- success/failure metric/log line.

### 4.9 Security baseline

Server baseline:

- SSH key-only login; password login disabled.
- Root login disabled или restricted; operational user с sudo где нужно.
- UFW или equivalent firewall разрешает только SSH, HTTP/HTTPS и нужные internal ports.
- Internal app ports bound to localhost where possible.
- Automatic security updates или documented patch cadence.
- `fail2ban` или equivalent SSH brute-force protection.
- Regular package inventory и reboot policy для kernel updates.

Application baseline:

- HTTPS для public endpoints до появления production data.
- Secure cookies и корректные proxy headers.
- Keycloak clients configured with exact redirect URIs, без broad wildcards.
- CORS restricted to known frontend origins.
- Actuator exposes only safe endpoints publicly; detailed metrics protected или internal.
- Anonymous demo data cannot mutate shared seed state.

Repository baseline:

- Branch protection for `main` once prod/demo data matters.
- Required PR checks before merge.
- Secret scanning enabled.
- CODEOWNERS или explicit reviewer convention для Ops files.

### 4.10 Runbooks

Создать runbooks в `docs/runbooks/`:

1. `deploy-backend.md` — normal deploy, smoke, rollback.
2. `deploy-frontend.md` — normal deploy, smoke, rollback.
3. `database-backup-restore.md` — backup inspection и restore drill.
4. `keycloak-ops.md` — realm/client config, user issue triage, token/debug commands.
5. `incident-response.md` — severity, communication, timeline, follow-up.
6. `secrets-rotation.md` — rotate GitHub PAT, GitHub Actions secrets, DB password, Keycloak admin/client secrets.
7. `server-hardening.md` — SSH/firewall/packages/service users.

Каждый runbook должен содержать:

- когда его использовать;
- prerequisites;
- exact commands;
- expected output;
- rollback/abort path;
- verification checklist.

## 5. Поэтапный план

### Phase 0 — inventory и guardrails

Цель: сделать текущую demo-эксплуатацию явной и безопаснее до изменения runtime architecture.

Задачи:

- Задокументировать services, ports, paths, systemd units, deploy directories и текущие GitHub Pages/CI flows.
- Для профиля `finguide.les13.tech` выполнить read-only host/cluster discovery и зафиксировать resources, ingress/ports, storage classes и conflict matrix.
- Добавить skeleton runbooks для deploy, rollback и incident response.
- Добавить secrets inventory только с именами, без значений.
- Добавить branch protection proposal для `main`.
- Добавить pre-prod checklist в docs.
- Проверить, что GitHub Pages docs navigation включает Ops RFC и runbooks.

Критерии выхода:

- Новый инженер понимает, что запущено и как оно deployится.
- Если выбран Kubernetes, diff/discovery доказывает, что planned changes scoped только к `finguide-*` resources, а resource quotas оставляют headroom для будущих проектов.
- Strict docs build зелёный.
- В docs/issues нет secret values.

### Phase 1 — foundation для production persistence

Цель: сделать данные durable и migration-driven.

Задачи:

- Добавить PostgreSQL profile и connection config.
- Ввести Flyway migrations из текущей schema.
- Оставить H2 для tests/demo, если это полезно.
- Добавить migration validation в CI.
- Создать DB backup job.
- Выполнить первый restore drill в dev/pre-prod или disposable DB.

Критерии выхода:

- Backend стартует на PostgreSQL из empty DB.
- Backup и restore documented and verified.
- H2 больше не используется для production-like state.

### Phase 2 — dev/demo split

Цель: снизить release risk до появления real users/data.

Задачи:

- Создать dev/pre-prod backend service/profile.
- Создать dev/pre-prod frontend base path или host.
- Создать отдельный Keycloak client/realm settings для dev/pre-prod.
- Разделить deploy workflows на dev/pre-prod и demo/prod.
- Добавить promotion step из dev/pre-prod в demo/prod.

Критерии выхода:

- Merge в `main` сначала валидируется в dev/pre-prod.
- Demo/prod deploy требует explicit promotion.
- Dev/pre-prod smoke покрывает frontend, backend и auth discovery.

### Phase 3 — наблюдаемость и alerting

Цель: находить user-impacting failures раньше пользователей.

Задачи:

- Добавить metrics endpoint/config.
- Добавить dashboards для backend, frontend availability, DB и host resources.
- Добавить synthetic journey check.
- Добавить high-signal alerts.
- Добавить deploy markers на dashboards, если feasible.

Критерии выхода:

- Падение backend, frontend, Keycloak или backup triggers an alert.
- Dashboard отвечает на вопрос о текущем статусе меньше чем за 60 секунд.
- Incidents можно восстановить по logs и deploy history.

### Phase 4 — hardening и repeatability

Цель: снизить manual drift и повысить recovery confidence.

Задачи:

- Codify server setup через Ansible/Terraform/OpenTofu или minimal reproducible shell+docs baseline.
- Enforce firewall и service user conventions.
- Добавить TLS и certificate expiry monitoring.
- Добавить dependency/security update cadence.
- Отрепетировать full VM rebuild или app redeploy с clean host.

Критерии выхода:

- Server можно rebuild из docs/code с ограниченным набором manual steps.
- TLS enabled для public production endpoints.
- Recovery process tested at least once.

## 6. Решения

### 6.1 Single VM vs Kubernetes

Рекомендация: **`finguide.les13.tech` — default target для production-like demo; runtime может быть hardened VM или Kubernetes, но Kubernetes должен быть isolated и resource-capped**.

Обоснование:

- У приложения пока мало service components, и существующий systemd/static deploy работает.
- Срочные риски — data durability, secrets, backups и наблюдаемость, а не orchestration.
- Kubernetes добавляет ingress/storage/secret/runner complexity, поэтому не должен bypass Phase 0–3 guardrails.
- `finguide.les13.tech` — новый FinGuide server, где позже будут другие проекты; поэтому quotas, backups и no-touch rules для не-FinGuide resources обязательны.

Использовать hardened VM path, когда важнее speed and simplicity. Использовать Kubernetes profile на `finguide.les13.tech`, когда нужна containerized demo/dev parity и команда принимает дополнительную сложность ingress/storage/secrets. Вернуться к решению, когда:

- появятся несколько independent backend services;
- потребуется horizontal scaling;
- станет понятным infrastructure ownership;
- deployment/rollback needs превысят возможности systemd release directories;
- появится dedicated cluster или managed DB.

### 6.2 Где размещать PostgreSQL

Варианты:

1. PostgreSQL на том же VM.
2. Managed PostgreSQL.
3. Отдельный self-managed DB VM.

Рекомендация для первого production-like шага: **managed PostgreSQL, если позволяет budget; иначе environment-local PostgreSQL с off-host backups**. Для hardened VM path это same-VM PostgreSQL с off-host backups; для Kubernetes profile на `finguide.les13.tech` — per-environment PostgreSQL StatefulSets/PVCs или managed DB, если он будет выбран позже.

Компромиссы:

- Same VM или same-node Kubernetes PostgreSQL дешевле и проще, но потеря host влияет сразу на app и DB.
- Managed DB снижает backup/patch burden и повышает durability, но добавляет cost и provider coupling.
- Separate DB VM гибче, но повышает ops burden.
- Kubernetes PVCs — это не backups; demo/prod dumps должны уходить с node.

### 6.3 Deployment promotion model

Варианты:

1. Оставить auto-deploy из `main` в prod/demo.
2. Auto-deploy `main` в staging/dev, manual promotion в prod/demo.
3. Tag-only prod releases.

Рекомендация: **вариант 2 сейчас, вариант 3 позже, если release cadence станет формальным**.

## 7. Риски и mitigations

| Риск | Влияние | Снижение риска |
| --- | --- | --- |
| Migration breaks prod data | high | staging/dev restore drill, backward-compatible migrations, pre-deploy backup |
| Backup exists but restore fails | high | monthly restore drill and checksum validation |
| Token/secret exposure | high | short-lived tokens, rotation runbook, secret scanning, no inline secrets |
| Self-hosted runner compromise | high | minimal runner permissions, service user isolation, no broad secrets on runner |
| Alert fatigue | medium | start with few high-signal alerts only |
| Overengineering slows product work | medium | phased delivery; Kubernetes only through isolated `finguide.les13.tech` profile after discovery/preflight |
| Manual server drift | medium | inventory first, then lightweight IaC/runbooks |
| HTTP public endpoints with auth | medium/high | add TLS before production data |

## 8. Acceptance checklist для Ops epic

Ops work можно считать завершённым для первого production-ready milestone, когда:

- [ ] Production-like backend runs on PostgreSQL with Flyway migrations.
- [ ] Daily encrypted off-host DB backup exists.
- [ ] Restore drill documented and succeeded at least once.
- [ ] Demo and dev/pre-prod configs separated. В профиле `finguide.les13.tech` это `finguide-demo` и `finguide-dev`.
- [ ] Prod/demo deploy has explicit promotion or approval.
- [ ] Backend/frontend/Keycloak health checks monitored.
- [ ] At least one synthetic journey validates app + API integration.
- [ ] TLS enabled for public production endpoints.
- [ ] Secrets inventory and rotation runbook exist.
- [ ] Backend and frontend rollback runbooks exist and have been tested once.
- [ ] `main` branch protection and required checks enabled or explicitly deferred.
- [ ] Incident response template exists.

## 9. Предлагаемая декомпозиция GitHub issues

Этот RFC нужно вести как один Ops epic с меньшими implementation issues:

1. **Ops inventory и skeleton runbooks**
   - document services/ports/paths/systemd units;
   - add deploy/rollback/incident runbooks.
2. **PostgreSQL + Flyway production profile**
   - add migrations;
   - add prod config;
   - validate from empty DB.
3. **Backup and restore drill**
   - encrypted off-host backups;
   - restore into staging/dev or disposable DB;
   - alert on missing backups.
4. **Dev/demo split**
   - separate config, Keycloak client, deploy workflows;
   - dev/pre-prod smoke before demo/prod promotion;
   - для Kubernetes на `finguide.les13.tech`: public demo → namespace `finguide-demo`, pre-prod → namespace `finguide-dev`.
5. **Observability baseline**
   - metrics/logs/dashboard;
   - synthetic check;
   - alert rules.
6. **TLS and server hardening**
   - HTTPS;
   - SSH/firewall/package baseline;
   - cert expiry monitoring.
7. **Repeatable infrastructure baseline**
   - Ansible/OpenTofu или minimal reproducible scripts;
   - rebuild rehearsal.

## 10. Открытые вопросы

1. Где размещать первую demo DB: managed PostgreSQL, self-hosted on `finguide.les13.tech`, или per-environment PostgreSQL inside Kubernetes?
2. Когда поднимать dev/pre-prod на `finguide.les13.tech`: сразу как `finguide-dev`, на отдельной маленькой VM или после стабилизации demo baseline?
3. Какой alert channel сделать canonical: Telegram, email, GitHub issue или другой incident tool?
4. Какие initial RPO/RTO приемлемы после появления real user data?
5. Нужен ли manual approval для demo/prod deploy сразу или только после введения PostgreSQL?

## 11. Рекомендуемый следующий шаг

Создать и приоритизировать Ops epic в GitHub Project, затем выполнить Phase 0 до новых production-facing изменений. Phase 0 намеренно documentation-heavy: она сразу снижает operational ambiguity и создаёт runway для PostgreSQL, backups и dev/pre-prod без остановки продуктовой разработки. Если команда выбирает Kubernetes profile на `finguide.les13.tech`, Phase 0 должен включать read-only host/cluster discovery, conflict matrix и resource budget до любых namespace или Helm changes.
