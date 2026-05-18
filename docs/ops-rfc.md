# RFC: Ops-периметр FinGuide

- **Статус:** Proposed
- **Дата:** 2026-05-18
- **Scope:** demo → production-ready периметр для FinGuide backend, frontend, Keycloak, данных, observability и релизного процесса.
- **Owner:** Ops / platform track
- **Связанные страницы:** [Operations и CI/CD](operations.md), [RFC Kubernetes dev/prod на winemap.world](winemap-k8s-dev-prod-rfc.md), [Текущее состояние](status.md), [Roadmap](roadmap.md)

## 1. Executive summary

FinGuide уже имеет рабочий публичный demo-стенд: frontend под `/fg/`, Spring Boot backend под `/finguide-api`, Keycloak realm и self-hosted GitHub Actions deploy на один сервер. Это хорошо для быстрой продуктовой итерации, но текущий Ops-периметр всё ещё остаётся demo-периметром: embedded H2, ручная серверная конфигурация, ограниченные smoke checks, нет формализованных backup/restore процедур, нет наблюдаемости уровня инцидентов, нет отдельного staging окружения и нет описанной политики секретов.

Этот RFC предлагает эволюционный план без big-bang миграции. Идея: сохранить скорость разработки, но добавить минимальный production-grade слой вокруг уже работающего стенда. В первую очередь фиксируем инфраструктурную базу: инвентаризацию, секреты, резервное копирование, health/readiness, логи и runbooks. Затем переводим stateful-часть на PostgreSQL + Flyway, вводим staging/prod разделение, усиливаем CI/CD gate и только после этого добавляем полноценные SLO/alerts, blue-green/canary-подходы и IaC.

Этот документ — umbrella RFC для Ops-направления. Конкретный вариант размещения FinGuide в MicroK8s на `winemap.world` описан в отдельном [RFC Kubernetes dev/prod на winemap.world](winemap-k8s-dev-prod-rfc.md). Kubernetes RFC не отменяет этот Ops RFC, а является одним из deployment profiles: он должен выполнять те же требования по PostgreSQL/Flyway, backup/restore, secrets, staging/prod split, observability, TLS и rollback.

## 2. Current state

### 2.1 Public endpoints

- Frontend: `http://66.42.121.18/fg/`
- Backend API: `http://66.42.121.18/finguide-api/api/v1`
- Backend health: `http://66.42.121.18/finguide-api/actuator/health`
- Swagger UI: `http://66.42.121.18/finguide-api/swagger-ui.html`
- Springdoc OpenAPI JSON: `http://66.42.121.18/finguide-api/v3/api-docs`
- Keycloak realm: `http://66.42.121.18/auth/realms/finguide`
- GitHub Pages docs: `https://svoronkov-les13.github.io/finguide-be/`

### 2.2 Runtime and deploy

- Backend: Java 21, Spring Boot 3.3, Spring Security OAuth2 Resource Server, Spring Data JDBC.
- Persistence: embedded H2 demo state, initialized from `schema.sql` and `data.sql`.
- Backend deployment: self-hosted GitHub Actions runner on `66.42.121.18`, systemd service `finguide-api.service`, runtime jar `/opt/finguide-api/finguide-be.jar`.
- Frontend deployment: self-hosted GitHub Actions runner on the same server, static assets under `/var/www/mtproxy-info/fg`.
- Docs deployment: GitHub Pages through `mkdocs build --strict`.
- Deploy gate: backend `mvn -B clean package`; frontend `bun install --frozen-lockfile` + `bun run build:fg`; docs `mkdocs build --strict`.

### 2.3 Current strengths

- Deploy from `main` is automated for backend, frontend and docs.
- Backend deploy includes jar backup and public/local health smoke.
- OpenAPI coverage guard exists and prevents accidental contract drift growth.
- Frontend has build/type gates and real API integration for core screens.
- Documentation already captures current runtime, roadmap and architecture.

### 2.4 Current gaps

1. **Persistence gap:** H2 is useful for demo, but not acceptable as durable production state.
2. **Backup/restore gap:** no documented RPO/RTO, backup schedule, restore drill, or backup encryption policy.
3. **Environment gap:** no staging environment separated from demo/prod.
4. **Secrets gap:** no single documented owner model for GitHub secrets, service credentials, Keycloak secrets and rotation.
5. **Observability gap:** deploy checks only health; there are no formal alerts, dashboards, SLOs, error budgets or synthetic user journeys.
6. **Security gap:** public endpoints are HTTP, Keycloak realm is demo-grade, and server hardening is not fully captured as code/runbook.
7. **Change management gap:** `main` auto-deploys directly; this is fast but risky once real user data appears.
8. **Infrastructure drift gap:** server state is partly manual; repeatability after rebuild is not guaranteed.

## 3. Goals

### 3.1 Primary goals

- Define a practical Ops roadmap from demo to production-ready operation.
- Preserve current development velocity while reducing the probability of data loss and broken deployments.
- Introduce durable persistence with migrations, backups and restore verification.
- Make deploy and rollback behavior explicit and rehearsable.
- Add observability that answers: is the product up, is auth working, are users blocked, and did the last release degrade anything?
- Document operational ownership clearly enough that another engineer can operate the system without tribal knowledge.

### 3.2 Non-goals for this RFC

- Rewriting the application architecture into microservices.
- Mandating Kubernetes as the only production path. Kubernetes is allowed only as an explicitly approved deployment profile that satisfies this Ops baseline.
- Building enterprise-grade multi-region HA before there is production load.
- Replacing the current CI/CD system if self-hosted Actions remains adequate.
- Solving product analytics/BI; this RFC covers service observability and reliability.

## 4. Proposed target architecture

### 4.1 Environments

Adopt three logical environments, even if the first implementation still uses one physical server:

| Environment | Purpose | Data | Deploy trigger | External users |
| --- | --- | --- | --- | --- |
| `dev` | local engineer workflow | disposable | manual local run | no |
| `staging` | release candidate validation | seeded/synthetic | merge to staging branch or manual workflow | internal only |
| `prod` | user-facing service | durable real data | tagged release or approved main deploy | yes |

Minimum acceptable first step: create `staging` as a separate backend service/profile and frontend path on the existing server, with separate DB/schema, separate Keycloak client and separate config. Later it can move to a separate VM or to an approved Kubernetes profile.

Naming note: in the winemap Kubernetes RFC the cluster namespace `finguide-dev` plays the same role as this RFC's `staging`/pre-prod environment. Local engineer `dev` remains outside the cluster.

### 4.2 Runtime topology

Recommended near-term topology:

```text
Internet
  |
  v
nginx / reverse proxy
  |-- /fg/                 -> static frontend assets
  |-- /finguide-api/       -> Spring Boot backend :3093
  |-- /auth/               -> Keycloak
  |-- /docs/ or GitHub Pages external docs

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

Default recommendation for the current demo path is a hardened VM first: systemd/static frontend, PostgreSQL, nginx, backups and observability. Kubernetes is not required to reach the first production-ready milestone.

If the team explicitly chooses `winemap.world` as the target platform, the Kubernetes profile must be additive and isolated: only `finguide-*` namespaces/resources, no mutation of existing `winemap` workloads, separate dev/prod data services, and the same Ops gates from this RFC. In that case, the Kubernetes RFC becomes the implementation detail for sections 4–6, not a competing roadmap.

### 4.3 Persistence

Move from embedded H2 to PostgreSQL using Flyway migrations.

Required properties:

- Application schema is created only through versioned migrations.
- H2 may remain for tests and local/demo mode, but production profile must not use in-memory state.
- Every deploy runs migration validation before switching runtime to the new jar.
- Rollback policy distinguishes code rollback from schema rollback:
  - backward-compatible migrations preferred;
  - destructive migrations require explicit expand/migrate/contract plan;
  - backup snapshot before risky migration.

Acceptance criteria:

- `SPRING_PROFILES_ACTIVE=prod` uses PostgreSQL.
- `mvn -B test` covers migration compatibility and repository behavior.
- A fresh DB can be created from migrations only.
- A prod-like dump can be restored into staging and backend starts successfully.

### 4.4 Secrets and configuration

Configuration should be explicit by environment.

Required secret classes:

- GitHub Actions secrets: deploy-related values, if any.
- Backend runtime secrets: DB URL/user/password, OIDC issuer/client settings where needed.
- Keycloak admin/bootstrap secrets.
- Backup encryption credentials.
- External integration secrets later: email, notifications, storage.

Rules:

1. No secrets in repository, docs, shell history or issue comments.
2. Rotation procedure exists for every secret class.
3. Runtime secrets are readable only by the service user and deploy mechanism.
4. GitHub PATs used manually are short-lived or rotated immediately after use.
5. Incident note is created whenever a secret is exposed.

Minimum implementation:

- systemd `EnvironmentFile=/etc/finguide-api/finguide-api.env` with `0600` permissions;
- documented list of required environment variables without values;
- GitHub Actions uses repository/environment secrets rather than inline tokens;
- a `docs/runbooks/secrets-rotation.md` page.

### 4.5 CI/CD release flow

Current auto-deploy from `main` is acceptable for demo. For production data, move to a two-stage flow:

1. Pull request gate:
   - backend: unit/integration tests, OpenAPI coverage, migration validation;
   - frontend: typecheck, tests, build;
   - docs: strict mkdocs build.
2. Merge to `main`:
   - deploy to staging automatically;
   - run smoke and synthetic checks.
3. Promote to prod:
   - manual approval or tagged release;
   - backup preflight;
   - deploy;
   - post-deploy smoke;
   - rollback decision window.

Recommended workflow names:

- `backend-ci.yml`
- `backend-deploy-staging.yml`
- `backend-deploy-prod.yml`
- `frontend-ci.yml`
- `frontend-deploy-staging.yml`
- `frontend-deploy-prod.yml`
- `docs-pages.yml`

### 4.6 Rollback

Rollback must be boring.

Backend rollback:

- Keep last N jar artifacts under `/opt/finguide-api/releases/`.
- `/opt/finguide-api/current.jar` is a symlink to the active release.
- Deploy creates new release file, updates symlink atomically and restarts service.
- Rollback switches symlink to previous known-good release and restarts service.

Frontend rollback:

- Keep timestamped static asset directories.
- `/var/www/mtproxy-info/fg` points to current release or is replaced from previous backup.
- Rollback preserves asset integrity and does not mix old/new bundles.

Database rollback:

- Prefer forward fixes for compatible migrations.
- For destructive migrations, require backup snapshot and explicit restore plan.
- Any restore in prod requires human approval and incident record.

### 4.7 Observability

Minimum signals:

- **Availability:** HTTP uptime for `/fg/`, backend `/actuator/health`, Keycloak realm discovery.
- **Correctness:** synthetic journey: load app, obtain demo plan/current plan, call cashflow endpoint, verify response shape.
- **Latency:** p50/p95/p99 for backend requests by route family.
- **Errors:** 5xx rate, auth failures, failed migrations, failed deploys.
- **Resources:** CPU, memory, disk, DB size, DB connections, service restarts.
- **Security:** failed SSH attempts, expired certs, suspicious Keycloak admin events.

Suggested stack for current scale:

- Spring Boot Actuator metrics.
- Prometheus or Grafana Agent/Alloy scraping local services.
- Loki for structured logs if already available; otherwise journald + logrotate as phase 1.
- Grafana dashboard with service health, latency, errors and deploy markers.
- Alerting to Telegram/email for high-signal conditions only.

Initial alert rules:

| Alert | Threshold | Severity |
| --- | --- | --- |
| BackendDown | `/actuator/health` fails for 2 minutes | page |
| FrontendDown | `/fg/` fails for 2 minutes | page |
| KeycloakDown | realm discovery fails for 2 minutes | page |
| High5xxRate | 5xx > 2% for 10 minutes | page |
| DiskWillFill | disk > 85% | warn |
| BackupMissing | no successful backup in 26h | page |
| CertificateExpiring | TLS cert expires in <14d | warn |
| DeployFailed | GitHub Actions deploy failure | page |

### 4.8 Backups and restore

Backups are not real until restore is tested.

Policy proposal:

- RPO: 24h initially, 1h later if real paid/customer data appears.
- RTO: 4h initially, 1h later.
- PostgreSQL full logical backup daily via `pg_dump`.
- Retention: 7 daily, 4 weekly, 6 monthly.
- Encryption: age/gpg or storage-provider encryption with restricted credentials.
- Storage: off-host object storage or another controlled host; local-only backup is not enough.
- Restore drill: monthly into staging or disposable DB.

Backup job must emit:

- start/end timestamp;
- DB name and schema version;
- compressed size;
- checksum;
- storage target;
- success/failure metric/log line.

### 4.9 Security baseline

Server baseline:

- SSH key-only login; password login disabled.
- Root login disabled or restricted; operational user with sudo where needed.
- UFW or equivalent firewall allows only SSH, HTTP/HTTPS and required internal ports.
- Internal app ports bound to localhost where possible.
- Automatic security updates or documented patch cadence.
- `fail2ban` or equivalent SSH brute-force protection.
- Regular package inventory and reboot policy for kernel updates.

Application baseline:

- HTTPS for public endpoints before production data.
- Secure cookies and correct proxy headers.
- Keycloak clients configured with exact redirect URIs, not broad wildcards.
- CORS restricted to known frontend origins.
- Actuator exposes only safe endpoints publicly; detailed metrics protected or internal.
- Anonymous demo data cannot mutate shared seed state.

Repository baseline:

- Branch protection for `main` once prod data exists.
- Required PR checks before merge.
- Secret scanning enabled.
- CODEOWNERS or explicit reviewer convention for Ops files.

### 4.10 Runbooks

Create runbooks under `docs/runbooks/`:

1. `deploy-backend.md` — normal deploy, smoke, rollback.
2. `deploy-frontend.md` — normal deploy, smoke, rollback.
3. `database-backup-restore.md` — backup inspection and restore drill.
4. `keycloak-ops.md` — realm/client config, user issue triage, token/debug commands.
5. `incident-response.md` — severity, communication, timeline, follow-up.
6. `secrets-rotation.md` — rotate GitHub PAT, GitHub Actions secrets, DB password, Keycloak admin/client secrets.
7. `server-hardening.md` — SSH/firewall/packages/service users.

Each runbook should have:

- when to use it;
- prerequisites;
- exact commands;
- expected output;
- rollback/abort path;
- verification checklist.

## 5. Phased delivery plan

### Phase 0 — inventory and guardrails

Goal: make current demo operation explicit and safer before changing runtime architecture.

Tasks:

- Document all services, ports, paths, systemd units, deploy directories and current GitHub Pages/CI flows.
- For the winemap Kubernetes profile, run read-only cluster discovery and document existing namespaces, NodePorts, storage classes and conflict matrix.
- Add runbook skeletons for deploy, rollback and incident response.
- Add a secrets inventory with names only, no values.
- Add branch protection proposal for `main`.
- Add a pre-prod checklist to docs.
- Confirm GitHub Pages docs navigation includes Ops RFC and runbooks.

Exit criteria:

- A new engineer can identify what is running and how it is deployed.
- If Kubernetes is selected, diff/discovery proves planned changes are scoped to `finguide-*` resources only.
- Docs build is strict and green.
- No secret values are present in docs/issues.

### Phase 1 — production persistence foundation

Goal: make data durable and migration-driven.

Tasks:

- Add PostgreSQL profile and connection config.
- Introduce Flyway migrations from current schema.
- Keep H2 for tests/demo if useful.
- Add migration validation in CI.
- Create DB backup job.
- Perform first restore drill into staging/disposable DB.

Exit criteria:

- Backend can run against PostgreSQL from empty DB.
- Backup and restore are documented and verified.
- H2 is no longer used for any production-like state.

### Phase 2 — staging/prod split

Goal: reduce release risk before real users/data.

Tasks:

- Create staging backend service/profile.
- Create staging frontend base path or host.
- Create separate Keycloak client/realm settings for staging.
- Split deploy workflows into staging and prod.
- Add promotion step from staging to prod.

Exit criteria:

- Merging to `main` validates in staging first.
- Production deploy requires explicit promotion.
- Staging smoke covers frontend, backend and auth discovery.

### Phase 3 — observability and alerting

Goal: detect user-impacting failures before users report them.

Tasks:

- Add metrics endpoint/config.
- Add dashboards for backend, frontend availability, DB and host resources.
- Add synthetic journey check.
- Add high-signal alerts.
- Add deploy markers to dashboards if feasible.

Exit criteria:

- A failed backend, frontend, Keycloak or backup triggers an alert.
- Dashboard answers current status in under 60 seconds.
- Incidents can be reconstructed from logs and deploy history.

### Phase 4 — hardening and repeatability

Goal: reduce manual drift and improve recovery confidence.

Tasks:

- Codify server setup with Ansible/Terraform/OpenTofu or a minimal reproducible shell+docs baseline.
- Enforce firewall and service user conventions.
- Add TLS and certificate expiry monitoring.
- Add dependency/security update cadence.
- Rehearse full VM rebuild or app redeploy from clean host.

Exit criteria:

- Server can be rebuilt from docs/code with bounded manual steps.
- TLS is enabled for public production endpoints.
- Recovery process is tested at least once.

## 6. Decision points

### 6.1 Single VM vs Kubernetes

Recommendation: **single hardened VM is the default near-term path; Kubernetes on `winemap.world` is acceptable only as an explicit isolated deployment profile**.

Rationale:

- Current app has low service count and the existing systemd/static deploy works.
- The urgent risks are data durability, secrets, backups and observability, not orchestration.
- Kubernetes adds ingress/storage/secret/runner complexity, so it must not bypass Phase 0–3 guardrails.
- `winemap.world` already runs MicroK8s with enough capacity, so it can be a cost-effective target if isolation, quotas, backups and no-touch rules for existing workloads are enforced.

Use the hardened VM path when speed and simplicity matter most. Use the winemap Kubernetes profile when the team accepts shared-cluster risk and wants containerized dev/prod parity now. Revisit the decision when:

- multiple independent backend services appear;
- horizontal scaling is needed;
- infrastructure ownership is clear;
- deployment/rollback needs exceed what systemd release directories can provide;
- a dedicated cluster or managed DB becomes available.

### 6.2 PostgreSQL location

Options:

1. PostgreSQL on same VM.
2. Managed PostgreSQL.
3. Separate self-managed DB VM.

Recommendation for first production step: **managed PostgreSQL if budget allows; otherwise environment-local PostgreSQL with off-host backups**. For the current VM path this means same-VM PostgreSQL; for the winemap Kubernetes profile this means per-environment PostgreSQL StatefulSets/PVCs or a managed DB if chosen later.

Trade-off:

- Same VM or same-node Kubernetes PostgreSQL is cheap and simple, but host loss affects app and DB simultaneously.
- Managed DB reduces backup/patch burden and improves durability, but adds cost and provider coupling.
- Separate DB VM is flexible but increases ops burden.
- Kubernetes PVCs are not backups; prod dumps must leave the node.

### 6.3 Deployment promotion model

Options:

1. Keep auto-deploy from `main` to prod.
2. Auto-deploy `main` to staging, manual promotion to prod.
3. Tag-only prod releases.

Recommendation: **option 2 now, option 3 later if release cadence becomes formal**.

## 7. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Migration breaks prod data | high | staging restore drill, backward-compatible migrations, pre-deploy backup |
| Backup exists but restore fails | high | monthly restore drill and checksum validation |
| Token/secret exposure | high | short-lived tokens, rotation runbook, secret scanning, no inline secrets |
| Self-hosted runner compromise | high | minimal runner permissions, service user isolation, no broad secrets on runner |
| Alert fatigue | medium | start with few high-signal alerts only |
| Overengineering slows product work | medium | phased delivery; Kubernetes only through the isolated winemap profile after discovery/preflight |
| Manual server drift | medium | inventory first, then lightweight IaC/runbooks |
| HTTP public endpoints with auth | medium/high | add TLS before production data |

## 8. Acceptance checklist for the Ops epic

The Ops work can be considered complete for the first production-ready milestone when:

- [ ] Production-like backend runs on PostgreSQL with Flyway migrations.
- [ ] Daily encrypted off-host DB backup exists.
- [ ] Restore drill is documented and has succeeded at least once.
- [ ] Staging/pre-prod and prod configs are separated. In the winemap profile this maps to `finguide-dev` and `finguide-prod`.
- [ ] Prod deploy has explicit promotion or approval.
- [ ] Backend/frontend/Keycloak health checks are monitored.
- [ ] At least one synthetic journey validates app + API integration.
- [ ] TLS is enabled for public production endpoints.
- [ ] Secrets inventory and rotation runbook exist.
- [ ] Backend and frontend rollback runbooks exist and have been tested once.
- [ ] `main` branch protection and required checks are enabled or explicitly deferred.
- [ ] Incident response template exists.

## 9. Proposed GitHub issue breakdown

This RFC should be tracked as one Ops epic with smaller implementation issues:

1. **Ops inventory and runbook skeletons**
   - document services/ports/paths/systemd units;
   - add deploy/rollback/incident runbooks.
2. **PostgreSQL + Flyway production profile**
   - add migrations;
   - add prod config;
   - validate from empty DB.
3. **Backup and restore drill**
   - encrypted off-host backups;
   - restore into staging/disposable DB;
   - alert on missing backups.
4. **Staging/prod split**
   - separate config, Keycloak client, deploy workflows;
   - staging smoke before prod promotion;
   - for winemap Kubernetes, map staging/pre-prod to `finguide-dev` namespace and prod to `finguide-prod`.
5. **Observability baseline**
   - metrics/logs/dashboard;
   - synthetic check;
   - alert rules.
6. **TLS and server hardening**
   - HTTPS;
   - SSH/firewall/package baseline;
   - cert expiry monitoring.
7. **Repeatable infrastructure baseline**
   - Ansible/OpenTofu or minimal reproducible scripts;
   - rebuild rehearsal.

## 10. Open questions

1. Should first production DB be managed PostgreSQL, self-hosted on the current VM, or per-environment PostgreSQL inside winemap Kubernetes?
2. Should staging/pre-prod live on the current VM, a separate small VM, or `finguide-dev` namespace on winemap?
3. What alert channel should be canonical: Telegram, email, GitHub issue, or another incident tool?
4. What is the initial acceptable RPO/RTO once real user data exists?
5. Should prod deploy require manual approval immediately, or only after PostgreSQL is introduced?

## 11. Recommended next action

Create and prioritize the Ops epic in the GitHub Project, then implement Phase 0 before more production-facing changes. Phase 0 is intentionally documentation-heavy because it reduces operational ambiguity immediately and creates the runway for PostgreSQL/backups/staging without disrupting current product work. If the team chooses the winemap Kubernetes profile, Phase 0 must include read-only cluster discovery and a conflict matrix before any namespace or Helm changes.
