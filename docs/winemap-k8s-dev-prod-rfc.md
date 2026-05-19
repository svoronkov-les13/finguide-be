# RFC: FinGuide demo/dev в Kubernetes на finguide.les13.tech

- **Статус:** Draft / Proposed baseline
- **Дата адаптации:** 2026-05-19
- **Площадка:** `finguide.les13.tech`
- **Scope:** FinGuide backend, frontend, PostgreSQL, Redis, auth configuration, CI/CD, observability, backup/restore и safe rollout на новом сервере FinGuide.
- **Историческое имя файла:** страница сохраняет прежний URL `winemap-k8s-dev-prod-rfc/`, чтобы не ломать внешние ссылки; содержание теперь описывает целевую площадку `finguide.les13.tech`.
- **Связанные страницы:** [RFC Ops-периметр](ops-rfc.md), [Operations и CI/CD](operations.md), [Текущее состояние](status.md), [Roadmap](roadmap.md)

## 1. Executive summary

Этот RFC является concrete deployment profile для umbrella [RFC Ops-периметр](ops-rfc.md). Он описывает, как развернуть FinGuide на новом сервере `finguide.les13.tech`.

Новая трактовка площадки:

- `finguide.les13.tech` — основной demo-стенд FinGuide, близкий к production по дисциплине эксплуатации: TLS, PostgreSQL/Flyway, backup/restore, secrets, observability, rollback.
- На этом же сервере позже должен появиться dev/pre-prod стенд для проверки релизов перед публикацией в demo.
- Сервер не является выделенным только под FinGuide. На нём позже будут другие проекты, поэтому FinGuide обязан жить в ограниченных namespace/resource budgets и не занимать все CPU/RAM/disk.
- `winemap.world` больше не является целевой площадкой FinGuide. Предыдущий Kubernetes-профиль для `winemap.world` считается superseded этим RFC.

Рекомендуемый baseline:

- создать отдельные namespaces `finguide-demo` и позже `finguide-dev`;
- использовать отдельные Helm releases с именами `finguide-*`;
- держать PostgreSQL, Redis, secrets и OIDC clients отдельно для demo/dev;
- перевести backend с H2 demo persistence на PostgreSQL profile + Flyway до production-like demo rollout;
- публиковать backend/frontend как container images и продвигать в demo тот же image tag, который прошёл dev smoke;
- подключать observability аккуратно, без тяжёлых self-hosted стеков в baseline;
- начать с discovery → quotas → demo data services → demo backend/frontend → backup/observability → dev/pre-prod.

Relationship to Ops RFC: этот документ не снижает требования Ops RFC. PostgreSQL/Flyway, backup/restore, secrets rotation, dev/pre-prod validation, demo promotion approval, observability, TLS and rollback остаются обязательными. Отличие только в runtime substrate: Kubernetes namespaces/Helm вместо systemd/static deploy.

## 2. Platform assumptions

Фактически подтверждённое состояние нового сервера нужно зафиксировать отдельным discovery-отчётом перед мутациями. До discovery этот RFC опирается только на целевые ограничения:

- Public host: `finguide.les13.tech`.
- Server purpose: shared host for FinGuide demo/dev and future projects.
- Kubernetes: allowed target runtime if MicroK8s/k3s/another lightweight cluster is installed or approved.
- Host resources: not fully reserved for FinGuide.
- Public routing: HTTPS must terminate either at host nginx/Caddy/Traefik or Kubernetes ingress.

Phase 0 must discover and document:

- OS/version, CPU/RAM/disk;
- Kubernetes distribution/version, if present;
- existing namespaces/workloads, if any;
- ingress strategy and occupied ports;
- storage class and available disk budget;
- backup target options;
- DNS/TLS ownership.

Вывод: `finguide.les13.tech` — правильная площадка для demo/pre-prod FinGuide, но не пустой dedicated production cluster. Все действия должны быть scoped to `finguide-*` resources, с ResourceQuota/LimitRange с первого шага.

## 3. FinGuide context

FinGuide сейчас состоит из двух основных репозиториев:

- `finguide-be` — Java 21 / Spring Boot 3 backend, contract-first API, Spring Security OAuth2 Resource Server, сейчас использует H2 demo persistence;
- `finguide-web` — React 19 / TypeScript / Vite frontend, TanStack Query/Router, generated API client, OIDC auth support.

Текущий legacy demo на `66.42.121.18` остаётся историческим состоянием до миграции:

- Frontend: `http://66.42.121.18/fg/`
- Backend API: `http://66.42.121.18/finguide-api/api/v1`
- Swagger: `http://66.42.121.18/finguide-api/swagger-ui.html`
- Keycloak realm: `http://66.42.121.18/auth/realms/finguide`
- GitHub Pages docs: `https://svoronkov-les13.github.io/finguide-be/`

Новый target demo должен быть опубликован на `https://finguide.les13.tech/` или согласованных поддоменах этого домена. H2 может остаться только для local/tests/temporary seed; production-like demo должен получить PostgreSQL, Flyway migrations, environment-based configuration, secrets outside git, backup/restore runbooks.

## 4. Non-goals and hard boundaries

FinGuide IaC **не должен**:

- занимать весь сервер под FinGuide;
- создавать cluster-wide зависимости без явной необходимости;
- ставить тяжёлые observability/search стеки вроде self-hosted Sentry или ELK/OpenSearch в baseline;
- использовать общие БД/Redis/secrets будущих проектов;
- удалять или менять не-`finguide-*` resources;
- выполнять destructive Ansible/Kubernetes tasks без explicit approve;
- публиковать secrets в git, GitHub Issues, GitHub Pages, shell history или CI logs.

Принцип: **FinGuide infrastructure is bounded, isolated and reversible**.

## 5. Target environments

Naming alignment with the Ops RFC:

- Ops RFC `dev` = local engineer workflow and remains outside this server.
- Ops RFC `staging` / pre-prod = future `finguide-dev` namespace/environment on `finguide.les13.tech`.
- Ops RFC `prod` = production-grade target later; current public environment is named `demo`, because it is close to prod operationally but not yet a committed production service.

### 5.1 Demo

Назначение:

- stable public demo for stakeholders;
- production-like operational discipline;
- durable enough data model for realistic testing;
- mandatory backup/restore before trusting non-disposable data;
- conservative resource limits, because server is shared.

Draft configuration:

- Namespace: `finguide-demo`.
- Hostname: `finguide.les13.tech`.
- Backend API: same host path `/api/v1` or separate host `api.finguide.les13.tech`.
- Swagger: same API host path `/swagger-ui.html` or `api.finguide.les13.tech/swagger-ui.html`.
- Data: separate PostgreSQL demo instance/PVC.
- Auth: separate Keycloak client `finguide-web-demo`, exact redirect URIs.

### 5.2 Dev / pre-prod

Назначение:

- проверка feature branches / pre-release builds;
- migration rehearsals;
- integration smoke tests;
- безопасная проверка Helm values и ingress routing;
- короткий retention и допустимый reset данных.

Draft configuration:

- Namespace: `finguide-dev`.
- Hostname option A: `dev.finguide.les13.tech`.
- Hostname option B: `finguide-dev.les13.tech`.
- Backend API: same host path `/api/v1` or separate host `api.dev.finguide.les13.tech`.
- Data: separate PostgreSQL dev instance/PVC.
- Auth: separate Keycloak client `finguide-web-dev`, separate redirect URIs.

DNS naming can change without changing the core architecture.

## 6. Target architecture

```text
Internet
  |
  | 80/443
  v
Host reverse proxy / Kubernetes ingress
  |
  +-- namespace finguide-demo
  |     +-- finguide-web-demo
  |     +-- finguide-api-demo
  |     +-- postgres-demo + PVC
  |     +-- redis-demo, only if needed
  |     +-- migration Job
  |     +-- backup CronJob
  |
  +-- namespace finguide-dev, later
  |     +-- finguide-web-dev
  |     +-- finguide-api-dev
  |     +-- postgres-dev + PVC
  |     +-- redis-dev, only if needed
  |     +-- migration Job
  |
  +-- future non-FinGuide projects
        [out of scope for FinGuide IaC]
```

Baseline components:

- `finguide-web`: static frontend container served by nginx.
- `finguide-api`: Spring Boot container.
- `postgres`: per-environment PostgreSQL StatefulSet or managed equivalent later.
- `redis`: optional per-environment Redis only after a concrete use case appears.
- `migration Job`: runs Flyway/schema migration before app rollout.
- `backup CronJob`: demo PostgreSQL backups to off-node storage.
- `observability`: lightweight metrics/logs with strict retention and labels `app=finguide`, `env=demo|dev`.
- `Sentry`: SaaS DSN via Kubernetes Secret if application error tracking is enabled.

## 7. Terraform / Ansible / Helm ownership model

### 7.1 Terraform / OpenTofu

Terraform/OpenTofu should own durable external and inventory concerns:

- environment inventory for `finguide.les13.tech`;
- optional DNS records if provider credentials are available;
- optional backup bucket / S3-compatible storage;
- optional generated Ansible inventory;
- optional Kubernetes namespaces/quotas only if provider is scoped strictly to new `finguide-*` namespaces.

It must not manage future non-FinGuide projects.

### 7.2 Ansible

Ansible owns host/bootstrap/safety checks:

- read-only discovery of host and cluster state;
- Kubernetes health verification;
- storage class discovery;
- ingress strategy and occupied ports discovery;
- local tools install only if absent: `kubectl`, `helm`, backup helper scripts;
- create/update FinGuide-only namespaces, quotas and bootstrap secrets;
- smoke checks;
- backup/restore drills.

Ansible playbooks must use tags and check mode where possible. Destructive tasks require explicit approval and must be isolated behind clear tags.

### 7.3 Helm

Helm owns application runtime:

- `finguide-api` chart;
- `finguide-web` chart;
- PostgreSQL chart/values;
- optional Redis chart/values;
- migration Jobs;
- ConfigMaps and Secret references;
- Services and Ingress resources;
- resource requests/limits;
- liveness/readiness probes;
- optional HPA only after metrics baseline exists.

## 8. Proposed infra repository structure

Recommended separate repository: `finguide-infra`.

```text
finguide-infra/
  README.md
  docs/
    rfc-finguide-les13-k8s.md
    runbook.md
    restore-drill.md
  terraform/
    environments/
      finguide-les13/
        main.tf
        variables.tf
        outputs.tf
        terraform.tfvars.example
  ansible/
    inventory/
      finguide-les13.example.yml
    playbooks/
      00-discovery.yml
      01-preflight.yml
      10-bootstrap-finguide-namespaces.yml
      20-install-shared-prereqs.yml
      30-deploy-finguide.yml
      40-smoke.yml
      50-backup-restore-drill.yml
  helm/
    finguide-api/
    finguide-web/
    finguide-postgres/
    finguide-redis/
  environments/
    demo/
      values-api.yaml
      values-web.yaml
      values-postgres.yaml
    dev/
      values-api.yaml
      values-web.yaml
      values-postgres.yaml
  scripts/
    render.sh
    diff.sh
    smoke.sh
```

Why separate repo:

- keeps app repos focused on product code;
- makes infra review and CODEOWNERS easier;
- avoids accidental deploy changes mixed into feature PRs;
- can hold environment-specific Helm values without leaking secrets.

Secrets must still live outside git.

## 9. Safe deployment phases

### Phase 0 — discovery only

Read-only commands only:

- inspect host OS/resources/disk;
- inspect Kubernetes namespaces/workloads, if cluster exists;
- inspect ingress, services, PVC/PV and storage classes;
- inspect occupied public ports;
- verify no `finguide-*` resources already exist.

Deliverables:

- discovery report;
- conflict matrix;
- occupied names/ports list;
- recommended ingress/DNS path;
- initial resource budget;
- no host/cluster mutations.

### Phase 1 — namespaces and quotas

Create only:

- namespace `finguide-demo`;
- namespace `finguide-dev` when dev is actually needed;
- ResourceQuota / LimitRange;
- service accounts;
- image pull secrets if needed.

No application workloads yet.

Exit criteria:

- namespaces exist;
- quotas prevent noisy-neighbor damage;
- `kubectl diff` / Helm diff shows only `finguide-*` resources;
- non-FinGuide resources unchanged.

### Phase 2 — demo data services

Deploy:

- PostgreSQL demo;
- optional Redis demo only with a concrete use case;
- backup CronJob for demo PostgreSQL;
- restore test into temporary namespace or temporary DB.

Exit criteria:

- demo DB credentials are separate;
- demo backup leaves the server or is ready to leave the server before non-disposable data;
- restore drill succeeds;
- Redis cannot be used as durable financial storage.

### Phase 3 — demo backend/frontend

Deploy:

- `finguide-api-demo`;
- migration Job demo;
- `finguide-web-demo`;
- ingress/routes for `finguide.les13.tech`;
- smoke checks.

Exit criteria:

- backend runs with PostgreSQL, not H2;
- `/actuator/health` is green;
- Swagger/OpenAPI endpoint is reachable;
- frontend loads and points to correct API/OIDC issuer;
- TLS is enabled.

### Phase 4 — dev / pre-prod

Deploy only after demo baseline is stable:

- `finguide-api-dev`;
- `finguide-web-dev`;
- separate dev PostgreSQL;
- separate OIDC client;
- automatic deploy/smoke from `main` or staging branch.

Exit criteria:

- dev can rehearse migrations and releases without touching demo data;
- demo promotion uses image tags already tested in dev;
- dev data can be reset safely.

### Phase 5 — observability and alerts

Add only FinGuide-specific observability resources:

- dashboards;
- log labels `app=finguide`, `env=demo|dev`;
- alerts;
- Sentry DSN smoke, if Sentry is enabled;
- synthetic checks.

Do not add heavy shared observability stacks until a separate resource decision exists.

Exit criteria:

- backend/frontend/DB health visible on dashboard;
- at least one synthetic app/API journey runs;
- failed backend or backup triggers an alert;
- logs are filterable by app and environment.

## 10. Resource budget

Starting budget must be conservative because `finguide.les13.tech` will host other projects.

Suggested starting requests/limits:

| Component | Demo request/limit | Dev request/limit | Notes |
| --- | --- | --- | --- |
| `finguide-api` | `300m/1 CPU`, `768Mi/1536Mi` | `250m/750m`, `512Mi/1Gi` | set JVM heap explicitly |
| `finguide-web` | `50m/250m`, `128Mi/256Mi` | `50m/200m`, `128Mi/256Mi` | nginx static |
| PostgreSQL | `300m/1 CPU`, `1Gi/2Gi` | `250m/750m`, `768Mi/1536Mi` | demo PVC initially `20–40Gi`; dev smaller |
| Redis, optional | `100m/300m`, `256Mi/512Mi` | `50m/200m`, `128Mi/256Mi` | configure `maxmemory` |
| Migration Job | `250m/1 CPU`, `512Mi/1Gi` | `250m/750m`, `512Mi/768Mi` | Job, not long-running |

Namespace quota draft:

- `finguide-demo`: max `3 CPU`, `5 GiB RAM`, storage `40–60 GiB`.
- `finguide-dev`: max `2 CPU`, `3 GiB RAM`, storage `20–30 GiB`.
- Combined FinGuide budget should stay below roughly half of the host after discovery unless the server is explicitly dedicated later.

This preserves headroom for future projects and host-level operations.

## 11. Data and migrations

Backend must support PostgreSQL config through environment variables/secrets:

- `FINGUIDE_DATASOURCE_URL`
- `FINGUIDE_DATASOURCE_USERNAME`
- `FINGUIDE_DATASOURCE_PASSWORD`
- `SPRING_PROFILES_ACTIVE=postgres,demo` or equivalent
- OIDC issuer/audience/client config
- Sentry DSN if backend SDK is added

Migration recommendation:

- introduce Flyway before trusting demo data;
- schema changes run through `finguide-api-migrate` Job;
- app deployment waits for successful migration;
- dev migration can run automatically;
- demo migration requires backup preflight once data matters;
- destructive migrations require expand/migrate/contract plan and backup snapshot.

Current H2/demo mode is not acceptable for the public production-like demo.

## 12. Redis usage

Redis may be deployed only when FinGuide defines what uses it.

Acceptable Redis use cases:

- cache;
- rate limiting;
- transient job queue;
- short-lived session adjunct if needed.

Not acceptable:

- durable financial records;
- source of truth for plans, goals, tracker entries, contributions or calculations.

Redis baseline:

- separate demo/dev instances;
- password auth;
- `maxmemory` configured;
- eviction policy explicit;
- persistence optional unless Redis is used for queue durability.

## 13. Keycloak / OIDC

Options:

1. **External/shared Keycloak** — recommended initially.
   - realm `finguide` or separate realm if needed;
   - clients `finguide-web-demo` and `finguide-web-dev`;
   - exact redirect URIs per environment.
2. **Keycloak in cluster** — possible later, but adds PostgreSQL/storage/admin overhead.
3. **Temporary demo auth** — acceptable for dev only, not public demo.

Baseline decision: use external/shared Keycloak first, keep credentials and redirect URIs separated for demo/dev.

## 14. Observability, logs and Sentry

Baseline:

- metrics: Prometheus-compatible scrape or lightweight agent;
- logs: journald/Loki-compatible pipeline with labels `app=finguide`, `env=demo|dev`;
- errors: Sentry SaaS DSN if enabled;
- health: Spring Boot actuator readiness/liveness endpoints;
- dashboards: JVM, HTTP latency, error rate, Postgres, ingress, pod restarts;
- alerts: backend down, frontend down, DB down, backup missing, high 5xx, disk pressure, cert expiring.

Full ELK/OpenSearch and self-hosted Sentry are not baseline. They are too heavy for a shared host unless resources are intentionally dedicated.

## 15. Backup and restore

Demo minimum before non-disposable data:

- daily PostgreSQL logical dump;
- encrypted private off-node storage;
- retention: 7 daily + 4 weekly + 6 monthly;
- monthly restore drill;
- backup success/failure alert.

Dev:

- short retention, 3–7 daily;
- reset is allowed.

Do not rely only on local PVC. Demo backup must leave the server before real user data is trusted to this environment.

Backup metadata should include:

- start/end timestamp;
- DB name/environment;
- schema migration version;
- compressed size;
- checksum;
- storage target;
- success/failure marker.

## 16. Security baseline

- SSH key-only.
- Kubernetes API not exposed publicly.
- No plaintext secrets in git, docs, issues, logs or shell history.
- Separate secrets for demo/dev.
- TLS via cert-manager or host reverse proxy.
- NetworkPolicy if CNI supports it safely.
- ResourceQuota and LimitRange mandatory.
- Images pulled from trusted registry.
- Non-root containers where possible.
- Demo DB not reachable from dev app.
- Actuator detailed endpoints internal/protected.
- CORS restricted to known frontend origins.
- Keycloak clients use exact redirect URIs, not broad wildcards.

## 17. CI/CD design

Recommended flow:

1. Build backend Docker image from `finguide-be`.
2. Build frontend Docker image from `finguide-web` with environment-aware runtime/build config.
3. Push images to registry.
4. Deploy to `finguide-dev` automatically once dev exists.
5. Run smoke tests.
6. Promote same image tags to `finguide-demo` manually or through approved workflow.
7. Run demo migration Job with backup preflight once data matters.
8. Run demo smoke tests.

Before `finguide-dev` exists, demo may be deployed directly from `main`, but this is a transitional mode only.

Important: demo should promote an already tested image, not rebuild from a moving branch, once dev/pre-prod exists.

Suggested gates:

- backend: `mvn -B test`, OpenAPI coverage guard, container build, migration validation;
- frontend: test/typecheck/build, container build;
- infra: `helm template`, `helm diff`, namespace-scope check, policy check;
- docs: `mkdocs build --strict`.

## 18. Registry decision

Open options:

- GitHub Container Registry (`ghcr.io`) — good default for GitHub-based project.
- Docker Hub — simple, but rate limits and namespace policy matter.
- Selectel Registry or another private registry — good if the wider hosting strategy moves there.

Recommendation: start with **GitHub Container Registry** unless there is an existing organizational registry preference.

## 19. Rollback model

Rollback must be planned before demo rollout.

Backend:

- Helm rollback to previous release revision;
- only safe if DB migration is backward-compatible;
- destructive migrations require explicit backup/restore path.

Frontend:

- Helm rollback to previous image tag;
- static assets are immutable image contents.

Database:

- prefer forward fixes for compatible migrations;
- restore only with human approval and incident record;
- backup snapshot before risky migrations.

Infra:

- every apply must be preceded by diff;
- changes must be scoped to `finguide-*` namespaces/resources;
- no cluster-wide deletions in normal rollout.

## 20. Acceptance criteria

Implementation is acceptable when:

- [ ] `finguide-demo` namespace exists with quotas.
- [ ] `finguide-dev` namespace exists with quotas when dev/pre-prod is introduced.
- [ ] FinGuide resource quotas preserve headroom for future projects on `finguide.les13.tech`.
- [ ] Backend runs with PostgreSQL, not H2.
- [ ] Flyway migrations run through a migration Job.
- [ ] Frontend points to correct environment API/OIDC issuer.
- [ ] Demo DB backup exists and restore drill succeeds before non-disposable data.
- [ ] Logs/metrics identify `env=demo|dev`.
- [ ] Sentry receives test event from demo/dev if Sentry integration is enabled.
- [ ] Rollback path is documented and tested at least once in dev or a disposable demo rehearsal.
- [ ] `kubectl diff`/Helm diff shows only FinGuide resources before apply.
- [ ] Demo promotion uses the same image tag already tested in dev once dev exists.
- [ ] TLS is enabled for public demo endpoints.

## 21. Open decisions

Need decide before implementation:

1. Final DNS names for demo/dev.
2. Kubernetes distribution on `finguide.les13.tech`: MicroK8s, k3s, existing cluster, or hardened VM first.
3. Registry: GitHub Container Registry, Docker Hub, Selectel Registry, or private registry.
4. Observability target: lightweight host logs/metrics, existing remote Grafana/Loki, or new minimal stack.
5. Where to store demo backups: Selectel S3, another S3-compatible bucket, or existing backup host.
6. Keycloak location: existing external/shared, in-cluster, or separate service.
7. Whether PostgreSQL/Flyway support lands in `finguide-be` before infra repo is created.
8. Exact maximum resource share FinGuide may consume on the shared server.

## 22. Recommended decision

Approve this baseline:

- deploy the public FinGuide demo to `finguide.les13.tech` in isolated `finguide-demo` namespace;
- add `finguide-dev` later on the same server for pre-prod validation;
- enforce conservative ResourceQuota/LimitRange from the first mutation;
- use additive Terraform/OpenTofu, Ansible and Helm only;
- PostgreSQL per environment, Redis only if a real use case appears;
- backend must move to PostgreSQL/Flyway before production-like demo data, matching the Ops RFC persistence baseline;
- use GHCR for images unless another registry is chosen;
- use Sentry SaaS if needed, not self-hosted Sentry;
- do not include full ELK/OpenSearch in baseline;
- roll out by phases: discovery → quotas → demo data → demo app → backup/observability → dev/pre-prod.

This gives a safe near-prod demo path on the new FinGuide server, preserves headroom for future projects, and avoids coupling FinGuide rollout to `winemap.world`.
