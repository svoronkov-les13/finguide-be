# Operations и CI/CD

Эта страница фиксирует фактическую схему публикации backend image, GitHub Pages документации и runtime deployment на Kubernetes.

## Публичный backend

- Public API base: <https://finguide.les13.tech/finguide-api/api/v1>
- Health: <https://finguide.les13.tech/finguide-api/actuator/health>
- Swagger UI: <https://finguide.les13.tech/finguide-api/swagger-ui.html>
- OpenAPI JSON: <https://finguide.les13.tech/finguide-api/v3/api-docs>
- Kubernetes namespace: `finguide`
- Deployment/service: `finguide-api`
- Runtime image: `ghcr.io/svoronkov-les13/finguide-api:<tag>`
- Runtime docs source of truth: `finguide-ops`

## GitHub Actions image publish

Backend container publishing is handled by `.github/workflows/docker-ghcr.yaml`.

Триггеры:

- push в `main`;
- ручной `workflow_dispatch` with `image_tag`.

Job делает:

- Docker build;
- push to `ghcr.io/svoronkov-les13/finguide-api`;
- tags: SHA, optional manual tag, `latest` on default branch.

Runtime Kubernetes deploy is owned by `finguide-ops`, not by this repository. The ops workflow renders `k8s/overlays/les13` or `k8s/overlays/dev`, sets the backend image tag and waits for rollout.

Legacy `.github/workflows/deploy.yml` / systemd notes are historical. Do not use `/opt/finguide-api/finguide-be.jar` or `finguide-api.service` as the current production-like deployment path.

## GitHub Pages docs deploy

Документация публикуется через `.github/workflows/pages.yml`.

Триггеры:

- push в `main`, если изменились `docs/**`, `mkdocs.yml`, `requirements-docs.txt` или сам workflow;
- ручной `workflow_dispatch`.

Job выполняет:

```bash
pip install -r requirements-docs.txt
mkdocs build --strict
```

После успешной сборки Pages публикуются на:

```txt
https://svoronkov-les13.github.io/finguide-be/
```

## Проверка после деплоя

Минимальный ручной smoke test:

```bash
curl -fsS https://finguide.les13.tech/finguide-api/actuator/health
curl -fsS https://finguide.les13.tech/finguide-api/v3/api-docs >/dev/null
curl -fsS https://finguide.les13.tech/auth/realms/finguide/.well-known/openid-configuration >/dev/null
```

Ожидаемо:

- health содержит `"status":"UP"`;
- OpenAPI JSON отдаётся backend'ом под context path `/finguide-api`;
- Keycloak discovery отвечает с issuer `https://finguide.les13.tech/auth/realms/finguide`.

Kubernetes-level диагностика находится в `finguide-ops/docs/runbook.md`:

```bash
kubectl -n finguide get pods,ingress
kubectl -n finguide logs deployment/finguide-api --tail=100
kubectl -n finguide exec deploy/finguide-api-postgres -- psql -U finguide -d finguide -c '\dt public.*'
```

## Правило работы с `main`

`main` автоматически деплоится. Незавершённые изменения нужно делать в отдельной ветке или worktree, затем мержить в `main` только после локальной проверки.

Для backend минимальный gate перед merge:

```bash
mvn -B test
```

Для docs минимальный gate:

```bash
mkdocs build --strict
```
