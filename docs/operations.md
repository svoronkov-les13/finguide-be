# Operations и CI/CD

Эта страница фиксирует фактическую схему деплоя FinGuide backend на публичный demo-стенд.

## Публичный backend

- Public API base: <http://66.42.121.18/finguide-api/api/v1>
- Health: <http://66.42.121.18/finguide-api/actuator/health>
- Swagger UI: <http://66.42.121.18/finguide-api/swagger-ui.html>
- OpenAPI JSON: <http://66.42.121.18/finguide-api/v3/api-docs>
- Systemd service: `finguide-api.service`
- Runtime jar: `/opt/finguide-api/finguide-be.jar`
- Local health used by deploy smoke test: `http://127.0.0.1:3093/actuator/health`

## GitHub Actions deploy

Backend deploy автоматизирован через `.github/workflows/deploy.yml`.

Триггеры:

- push в `main`;
- ручной `workflow_dispatch`.

Runner:

- self-hosted GitHub Actions runner на сервере `66.42.121.18`;
- labels: `self-hosted`, `finguide-be`;
- рабочая директория runner на сервере: `/home/clawd/actions-runner-finguide-be`.

Job `deploy` делает:

1. checkout репозитория;
2. `mvn -B clean package` — сборка и все тесты;
3. копирование собранного jar в `/opt/finguide-api/finguide-be.jar`;
4. backup предыдущего jar рядом с timestamp suffix;
5. `sudo systemctl restart finguide-api.service`;
6. smoke test локального и публичного health endpoint.

Если health не поднимается за 30 попыток, job печатает последние логи `finguide-api.service` и падает.

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
curl -fsS http://66.42.121.18/finguide-api/actuator/health
systemctl is-active finguide-api.service
stat -c '%y %s %n' /opt/finguide-api/finguide-be.jar
```

Ожидаемо:

- health содержит `"status":"UP"`;
- service status: `active`;
- timestamp jar соответствует последнему deploy.

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
