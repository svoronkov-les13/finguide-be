# RFC: FinGuide dev/prod в Kubernetes на winemap.world

- **Статус:** Draft / Proposed baseline
- **Дата адаптации:** 2026-05-18
- **Площадка:** `winemap.world` / host `Fibonacci`
- **Scope:** FinGuide backend, frontend, PostgreSQL, Redis, auth configuration, CI/CD, observability, backup/restore и safe rollout в существующий MicroK8s cluster.
- **Источник:** исходный RFC `http://66.42.121.18/java-k8s-dev-prod-rfc.html`, адаптирован под GitHub Pages документацию FinGuide.
- **Связанные страницы:** [RFC Ops-периметр](ops-rfc.md), [Operations и CI/CD](operations.md), [Текущее состояние](status.md), [Roadmap](roadmap.md)

## 1. Executive summary

Этот RFC является concrete deployment profile для umbrella [RFC Ops-периметр](ops-rfc.md). Он описывает, как выполнить общий Ops baseline на существующей площадке `winemap.world`, если команда выбирает Kubernetes-путь вместо hardened VM пути.

FinGuide можно развернуть на `winemap.world` как отдельный dev/prod периметр в MicroK8s, но только в additive-режиме: новая инфраструктура добавляется рядом с текущим приложением `winemap`, не импортирует его ресурсы в Terraform state и не меняет уже работающие namespaces, services, ingress, PVC или host nginx routing без отдельного RFC.

Рекомендуемый baseline:

- создать отдельные namespaces `finguide-dev` и `finguide-prod`;
- использовать отдельные Helm releases с именами `finguide-*`;
- держать PostgreSQL, Redis, secrets и OIDC clients отдельно для dev/prod;
- перевести backend с H2 demo persistence на PostgreSQL profile + Flyway до production rollout;
- публиковать backend/frontend как container images и продвигать в prod тот же image tag, который прошёл dev smoke;
- использовать existing Loki/Grafana/Prometheus только если это безопасно и не требует upgrade текущих shared-компонентов;
- подключать Sentry как SaaS через DSN, не поднимать self-hosted Sentry в baseline;
- не включать full ELK/OpenSearch в baseline из-за resource и ops overhead;
- начать с discovery → namespaces/quotas → data services → backend → frontend → observability.

`winemap.world` по ресурсам подходит для стартового dev/prod FinGuide: 12 logical CPU, 31 GiB RAM, около 371 GiB свободного диска, MicroK8s уже поднят. Главный риск — shared single-node cluster рядом с текущим `winemap`, MongoDB, Qdrant и Loki/Grafana. Поэтому RFC делает упор на namespace isolation, ResourceQuota/LimitRange, backups, explicit approvals и diff-before-apply guardrails.

Relationship to Ops RFC: этот документ не снижает требования Ops RFC. PostgreSQL/Flyway, backup/restore, secrets rotation, staging/pre-prod validation, prod promotion approval, observability, TLS and rollback остаются обязательными. Отличие только в runtime substrate: Kubernetes namespaces/Helm вместо systemd/static deploy.

## 2. Current platform state

Фактически известное состояние площадки:

- Hostname: `Fibonacci`.
- Public host: `winemap.world`.
- Public IP: `185.175.47.249`.
- OS: Ubuntu 24.04.2 LTS.
- Kubernetes: MicroK8s `v1.32.13`, single node, node `Ready`.
- CPU: 12 logical CPU / Intel i7-8700.
- RAM: 31 GiB total.
- Disk `/`: 433 GiB total, около 371 GiB available на момент первичной проверки.
- Existing Kubernetes namespaces/workloads: `winemap`, `mongodb`, `qdrant`, `loki-grafana`, `kube-system`.
- Host nginx currently proxies `https://winemap.world` to `localhost:30080`.
- External NodePorts observed: winemap `30080`, MongoDB `30017`, Grafana `31360`, Kubernetes Dashboard `31390`.

Вывод: ресурсов достаточно для FinGuide dev/prod при лимитах, но сервер нельзя считать пустым. Все действия должны быть scoped to `finguide-*` resources, а discovery текущего cluster state должен быть read-only.

## 3. FinGuide context

FinGuide сейчас состоит из двух основных репозиториев:

- `finguide-be` — Java 21 / Spring Boot 3 backend, contract-first API, Spring Security OAuth2 Resource Server, сейчас использует H2 demo persistence;
- `finguide-web` — React 19 / TypeScript / Vite frontend, TanStack Query/Router, generated API client, OIDC auth support.

Текущий публичный demo на `66.42.121.18`:

- Frontend: `http://66.42.121.18/fg/`
- Backend API: `http://66.42.121.18/finguide-api/api/v1`
- Swagger: `http://66.42.121.18/finguide-api/swagger-ui.html`
- Keycloak realm: `http://66.42.121.18/auth/realms/finguide`
- GitHub Pages docs: `https://svoronkov-les13.github.io/finguide-be/`

Для Kubernetes rollout backend должен получить production-safe persistence story: PostgreSQL, Flyway migrations, environment-based configuration, secrets outside git, backup/restore runbooks. H2 может остаться только для local/demo/tests.

## 4. Non-goals and hard boundaries

FinGuide IaC **не должен**:

- менять namespace `winemap`;
- менять workloads, services, ingress, PVC текущего `winemap`;
- менять namespaces `mongodb`, `qdrant`, `loki-grafana`, `kube-system`, кроме read-only discovery;
- делать `helm upgrade` существующих Grafana/Loki releases без отдельного maintenance RFC;
- менять host nginx proxy `https://winemap.world -> localhost:30080` без отдельного решения;
- импортировать existing Kubernetes resources в Terraform state;
- использовать общие MongoDB/Qdrant/PostgreSQL текущего приложения;
- удалять cluster-wide resources;
- выполнять destructive Ansible tasks без explicit approve;
- публиковать secrets в git, GitHub Issues, GitHub Pages, shell history или CI logs.

Принцип: **new FinGuide infrastructure is additive, isolated and reversible**.

## 5. Target environments

Naming alignment with the Ops RFC:

- Ops RFC `dev` = local engineer workflow and remains outside this cluster.
- Ops RFC `staging` / pre-prod = this RFC's `finguide-dev` namespace.
- Ops RFC `prod` = this RFC's `finguide-prod` namespace.

The namespace name `finguide-dev` is kept because it is concise and conventional in Kubernetes, but functionally it is the shared pre-prod/staging environment for release validation.

### 5.1 Dev

Назначение:

- проверка feature branches / pre-release builds;
- migration rehearsals;
- integration smoke tests;
- безопасная проверка Helm values и ingress routing;
- короткий retention и допустимый reset данных.

Draft configuration:

- Namespace: `finguide-dev`.
- Hostname option A: `dev.finguide.winemap.world`.
- Hostname option B: `finguide-dev.winemap.world`.
- Backend API: same host path `/api/v1` or separate host `api.dev.finguide.winemap.world`.
- Data: separate PostgreSQL dev instance/PVC.
- Auth: separate Keycloak client `finguide-web-dev`, separate redirect URIs.

### 5.2 Prod

Назначение:

- stable user-facing environment;
- durable financial data;
- separate credentials;
- mandatory backup/restore;
- stricter resource limits;
- explicit promotion from tested image.

Draft configuration:

- Namespace: `finguide-prod`.
- Hostname option A: `finguide.winemap.world`.
- Hostname option B: dedicated FinGuide project domain later.
- Backend API: same host path `/api/v1` or separate host `api.finguide.winemap.world`.
- Data: separate PostgreSQL prod instance/PVC.
- Auth: separate Keycloak client `finguide-web-prod`, exact redirect URIs.

DNS naming can change without changing the core architecture.

## 6. Target architecture

```text
Internet
  |
  | 80/443
  v
Host nginx / existing routing
  |
  | [do not change current winemap path without separate decision]
  v
MicroK8s ingress / NodePort layer
  |
  +-- namespace finguide-dev
  |     +-- finguide-web-dev
  |     +-- finguide-api-dev
  |     +-- postgres-dev + PVC
  |     +-- redis-dev
  |     +-- migration Job
  |
  +-- namespace finguide-prod
  |     +-- finguide-web-prod
  |     +-- finguide-api-prod
  |     +-- postgres-prod + PVC
  |     +-- redis-prod
  |     +-- migration Job
  |     +-- backup CronJob
  |
  +-- existing namespaces: winemap, mongodb, qdrant, loki-grafana
        [read-only from FinGuide IaC perspective]
```

Baseline components:

- `finguide-web`: static frontend container served by nginx.
- `finguide-api`: Spring Boot container.
- `postgres`: per-environment PostgreSQL StatefulSet or managed equivalent later.
- `redis`: per-environment Redis Deployment/StatefulSet depending on durability needs.
- `migration Job`: runs Flyway/schema migration before app rollout.
- `backup CronJob`: prod PostgreSQL backups to off-node storage.
- `observability`: existing Loki/Grafana/Prometheus read-only integration if safe; otherwise FinGuide-specific dashboards/alerts only.
- `Sentry`: SaaS DSN via Kubernetes Secret.

## 7. Terraform / Ansible / Helm ownership model

### 7.1 Terraform

Terraform is **not** the primary Kubernetes applicator for this cluster. It should own durable external and inventory concerns:

- environment inventory for `winemap.world`;
- optional DNS records if provider credentials are available;
- optional backup bucket / S3-compatible storage;
- optional generated Ansible inventory;
- optional Kubernetes namespaces/quotas only if provider is scoped strictly to new `finguide-*` namespaces.

Terraform must not manage or import existing cluster resources.

### 7.2 Ansible

Ansible owns host/bootstrap/safety checks:

- read-only discovery of cluster state;
- MicroK8s health verification;
- storage class discovery;
- ingress strategy and occupied NodePorts discovery;
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
- Redis chart/values;
- migration Jobs;
- ConfigMaps and Secret references;
- Services and Ingress resources;
- resource requests/limits;
- liveness/readiness probes;
- optional HPA after metrics baseline exists.

## 8. Proposed infra repository structure

Recommended separate repository: `finguide-infra`.

```text
finguide-infra/
  README.md
  docs/
    rfc-finguide-winemap-k8s.md
    runbook.md
    restore-drill.md
  terraform/
    environments/
      winemap/
        main.tf
        variables.tf
        outputs.tf
        terraform.tfvars.example
    modules/
      dns-records/
      backup-bucket/
      ansible-inventory/
  ansible/
    inventory/
      winemap.example.yml
    playbooks/
      00-discovery.yml
      01-preflight.yml
      10-bootstrap-finguide-namespaces.yml
      20-install-shared-prereqs.yml
      30-deploy-finguide.yml
      40-smoke.yml
      50-backup-restore-drill.yml
    roles/
      microk8s_readonly/
      finguide_namespaces/
      finguide_secrets/
      backup_tools/
      smoke_checks/
  helm/
    finguide-api/
    finguide-web/
    finguide-postgres/
    finguide-redis/
  environments/
    dev/
      values-api.yaml
      values-web.yaml
      values-postgres.yaml
      values-redis.yaml
    prod/
      values-api.yaml
      values-web.yaml
      values-postgres.yaml
      values-redis.yaml
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

- `kubectl get namespaces`
- `kubectl get ingress,svc,pvc,pv -A`
- `kubectl top node/pods`
- inspect ingress class and NodePorts
- inspect storage classes
- verify no `finguide-*` resources already exist

Deliverables:

- discovery report;
- conflict matrix;
- occupied names/ports list;
- recommended ingress/DNS path;
- no cluster mutations.

Exit criteria:

- we know exactly what exists before adding FinGuide;
- there is a written list of resources that must not be touched;
- `finguide-*` naming conflicts are resolved.

### Phase 1 — namespaces and quotas

Create only:

- namespace `finguide-dev`;
- namespace `finguide-prod`;
- ResourceQuota / LimitRange;
- service accounts;
- image pull secrets if needed.

No application workloads yet.

Exit criteria:

- namespaces exist;
- quotas prevent noisy-neighbor damage;
- `kubectl diff` / Helm diff shows only `finguide-*` resources;
- existing namespaces unchanged.

### Phase 2 — data services

Deploy:

- PostgreSQL dev/prod;
- Redis dev/prod;
- backup CronJob for prod PostgreSQL;
- restore test into temporary namespace or temporary DB.

Exit criteria:

- dev/prod DB credentials are separate;
- prod backup leaves the node or is ready to leave the node before real data;
- restore drill succeeds;
- Redis cannot be used as durable financial storage.

### Phase 3 — backend

Deploy:

- `finguide-api-dev`;
- migration Job dev;
- smoke dev;
- `finguide-api-prod`;
- migration Job prod;
- smoke prod.

Exit criteria:

- backend runs with PostgreSQL, not H2;
- `/actuator/health` is green;
- Swagger/OpenAPI endpoint is reachable in each environment;
- migration job is idempotent or safely repeatable;
- prod migration requires explicit approval.

### Phase 4 — frontend

Deploy:

- `finguide-web-dev` built/configured for dev API/OIDC;
- `finguide-web-prod` built/configured for prod API/OIDC;
- ingress routes;
- smoke browser/API checks.

Exit criteria:

- frontend loads under selected dev/prod hostnames;
- frontend points to correct backend API;
- OIDC redirect URI matches environment;
- no prod frontend points to dev backend or dev Keycloak client.

### Phase 5 — observability and alerts

Add only FinGuide-specific observability resources:

- dashboards;
- log labels `app=finguide`, `env=dev|prod`;
- alerts;
- Sentry DSN smoke;
- synthetic checks.

Do not upgrade existing shared Grafana/Loki until a separate maintenance window.

Exit criteria:

- backend/frontend/DB health visible on dashboard;
- at least one synthetic app/API journey runs;
- failed backend or backup triggers an alert;
- logs are filterable by app and environment.

## 10. Resource budget

Suggested starting requests/limits for shared `winemap.world` cluster:

| Component | Dev request/limit | Prod request/limit | Notes |
| --- | --- | --- | --- |
| `finguide-api` | `250m/1 CPU`, `768Mi/1536Mi` | `500m/2 CPU`, `1536Mi/3Gi` | set JVM heap explicitly |
| `finguide-web` | `50m/250m`, `128Mi/256Mi` | `100m/500m`, `128Mi/512Mi` | nginx static |
| PostgreSQL | `250m/1 CPU`, `1Gi/2Gi` | `500m/2 CPU`, `2Gi/4Gi` | prod PVC initially `30–50Gi+` |
| Redis | `100m/500m`, `256Mi/512Mi` | `200m/1 CPU`, `512Mi/1Gi` | configure `maxmemory` |
| Migration Job | `250m/1 CPU`, `512Mi/1Gi` | `250m/1 CPU`, `512Mi/1Gi` | Job, not long-running |

Namespace quota draft:

- `finguide-dev`: max `3 CPU`, `5 GiB RAM`, storage `30–50 GiB`.
- `finguide-prod`: max `6 CPU`, `10 GiB RAM`, storage `80–120 GiB`.

This preserves headroom for current `winemap`, MongoDB, Qdrant and Loki/Grafana workloads.

## 11. Data and migrations

Backend must support PostgreSQL config through environment variables/secrets:

- `FINGUIDE_DATASOURCE_URL`
- `FINGUIDE_DATASOURCE_USERNAME`
- `FINGUIDE_DATASOURCE_PASSWORD`
- `SPRING_PROFILES_ACTIVE=postgres,prod` or equivalent
- OIDC issuer/audience/client config
- Sentry DSN if backend SDK is added

Migration recommendation:

- introduce Flyway before real prod data;
- schema changes run through `finguide-api-migrate` Job;
- app deployment waits for successful migration;
- dev migration can run automatically;
- prod migration requires manual approval;
- destructive migrations require expand/migrate/contract plan and backup snapshot.

Current H2/demo mode is not acceptable for prod.

## 12. Redis usage

Redis may be deployed in the target architecture, but FinGuide must define what uses it.

Acceptable Redis use cases:

- cache;
- rate limiting;
- transient job queue;
- short-lived session adjunct if needed.

Not acceptable:

- durable financial records;
- source of truth for plans, goals, tracker entries, contributions or calculations.

Redis baseline:

- separate dev/prod instances;
- password auth;
- `maxmemory` configured;
- eviction policy explicit;
- persistence optional unless Redis is used for queue durability.

## 13. Keycloak / OIDC

FinGuide already has a Keycloak/OIDC boundary.

Options:

1. **External/shared Keycloak** — recommended initially.
   - realm `finguide` or separate realm if needed;
   - clients `finguide-web-dev` and `finguide-web-prod`;
   - exact redirect URIs per environment.
2. **Keycloak in cluster** — possible later, but adds PostgreSQL/storage/admin overhead.
3. **Temporary demo auth** — acceptable for dev only, not prod.

Baseline decision: use external/shared Keycloak first, keep credentials and redirect URIs separated for dev/prod.

## 14. Observability, logs and Sentry

Baseline:

- metrics: Prometheus + Grafana;
- logs: Loki/Promtail or existing compatible pipeline with labels `app=finguide`, `env=dev|prod`;
- errors: Sentry SaaS DSN;
- health: Spring Boot actuator readiness/liveness endpoints;
- dashboards: JVM, HTTP latency, error rate, Postgres, ingress, pod restarts;
- alerts: backend down, frontend down, DB down, backup missing, high 5xx, disk pressure, cert expiring.

Full ELK/OpenSearch:

- not baseline;
- optional profile only after checking memory/disk budget;
- should not be co-located with prod DB without explicit limits and retention.

Self-hosted Sentry:

- not baseline;
- too heavy for the current shared single-node unless resources are intentionally dedicated.

## 15. Backup and restore

Prod minimum:

- daily PostgreSQL logical dump;
- optional WAL/PITR later when data importance grows;
- encrypted private storage;
- retention: 7 daily + 4 weekly + 6 monthly;
- monthly restore drill;
- backup success/failure alert.

Dev:

- short retention, 3–7 daily;
- reset is allowed.

Do not rely only on local PVC. Prod backup must leave the server before real user data is trusted to this environment.

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
- Separate secrets for dev/prod.
- TLS via cert-manager or existing host nginx TLS path.
- NetworkPolicy if MicroK8s CNI supports it safely.
- ResourceQuota and LimitRange mandatory.
- Images pulled from trusted registry.
- Non-root containers where possible.
- Prod DB not reachable from dev app.
- Actuator detailed endpoints internal/protected.
- CORS restricted to known frontend origins.
- Keycloak clients use exact redirect URIs, not broad wildcards.

## 17. CI/CD design

Recommended flow:

1. Build backend Docker image from `finguide-be`.
2. Build frontend Docker image from `finguide-web` with environment-aware runtime/build config.
3. Push images to registry.
4. Deploy to `finguide-dev` automatically.
5. Run smoke tests.
6. Promote same image tags to `finguide-prod` manually.
7. Run prod migration Job with approval.
8. Run prod smoke tests.

Important: prod must promote an already tested image, not rebuild from a moving branch.

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

Recommendation: start with **GitHub Container Registry** unless there is an existing organizational registry preference. It keeps repo/image permissions close to source control and works naturally with GitHub Actions.

## 19. Rollback model

Rollback must be planned before prod rollout.

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

- [ ] `finguide-dev` and `finguide-prod` namespaces exist with quotas.
- [ ] Existing `winemap`, `mongodb`, `qdrant`, `loki-grafana` resources are unchanged.
- [ ] `finguide-dev`/`finguide-prod` map to Ops RFC staging/prod and use separate PostgreSQL, Redis and secrets.
- [ ] Backend runs with PostgreSQL, not H2.
- [ ] Flyway migrations run through a migration Job.
- [ ] Frontend points to correct environment API/OIDC issuer.
- [ ] Prod DB backup exists and restore drill succeeds.
- [ ] Logs/metrics identify `env=dev|prod`.
- [ ] Sentry receives test event from dev and prod if Sentry integration is enabled.
- [ ] Rollback path is documented and tested at least once in dev.
- [ ] `kubectl diff`/Helm diff shows only FinGuide resources before apply.
- [ ] Prod promotion uses the same image tag already tested in dev.
- [ ] TLS is enabled for public prod endpoints.

## 21. Open decisions

Need decide before implementation:

1. Final DNS names for dev/prod.
2. Registry: GitHub Container Registry, Docker Hub, Selectel Registry, or private registry.
3. Whether to reuse existing Grafana/Loki read-only or deploy FinGuide-specific observability components.
4. Where to store prod backups: Selectel S3, another S3-compatible bucket, or existing backup host.
5. Keycloak location: existing external/shared, in-cluster, or temporary dev-only setup.
6. Whether PostgreSQL/Flyway support lands in `finguide-be` before infra repo is created.
7. Whether prod should initially share the same single node or wait for a separate VM/managed DB.
8. Whether this Kubernetes profile is approved as the production path, or kept as an implementation option while the Ops RFC starts with hardened VM milestones.

## 22. Recommended decision

Approve this baseline:

- deploy FinGuide to `winemap.world` in isolated namespaces;
- use additive Terraform/Ansible/Helm only;
- keep current `winemap` installation untouched;
- PostgreSQL/Redis per environment inside Kubernetes for the first iteration;
- backend must move to PostgreSQL/Flyway before prod data, matching the Ops RFC persistence baseline;
- use GHCR for images unless another registry is chosen;
- use existing Loki/Grafana/Prometheus only in read-only/no-upgrade mode;
- use Sentry SaaS, not self-hosted Sentry;
- do not include full ELK/OpenSearch in baseline;
- roll out by phases: discovery → namespaces → data → backend → frontend → observability.

This gives the cheapest safe Kubernetes path now, preserves the current `winemap` installation, and keeps the option to migrate FinGuide to Selectel or a dedicated cluster later using the same Helm/Ansible structure. If the team does not explicitly approve Kubernetes yet, the Ops RFC hardened VM path remains the default near-term path, and this document should be treated as the ready implementation profile for a later platform decision.
