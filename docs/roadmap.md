# Roadmap

Источник правды по задачам — GitHub Project `finguide` и roadmap issue [#27](https://github.com/svoronkov-les13/finguide-be/issues/27). Ниже — сжатая версия для GitHub Pages.

## Уже сделано

Даты восстановлены по коммитам, где связь была очевидна; иначе использовано окно `createdAt → closedAt` issue.

### Foundation — 2026-04-29

- [x] [#1](https://github.com/svoronkov-les13/finguide-be/issues/1) — embedded H2 persistence for demo plans — commit `fcb1b01`.
- [x] [#2](https://github.com/svoronkov-les13/finguide-be/issues/2) — real plan read endpoints — commit `961b7ad`.
- [x] [#6](https://github.com/svoronkov-les13/finguide-be/issues/6) — Swagger на real services/backend вместо mock — commits `c8439bf`, `d2bfa24`.
- [x] [#5](https://github.com/svoronkov-les13/finguide-be/issues/5) — real Spring backend behind nginx — deployment window around `c8439bf`/`75f78f6`.
- [x] [finguide-web#1](https://github.com/svoronkov-les13/finguide-web/issues/1) — frontend default API base to real backend — commit `ad45332`.

### Core CRUD/Auth — 2026-04-29 → 2026-05-01

- [x] [#3](https://github.com/svoronkov-les13/finguide-be/issues/3) — income/expense/goal CRUD — commit `8af4996`.
- [x] [#9](https://github.com/svoronkov-les13/finguide-be/issues/9) — auth API endpoints — issue window 2026-04-29.
- [x] [#18](https://github.com/svoronkov-les13/finguide-be/issues/18) — Keycloak OIDC для backend/frontend — backend `941c0e7`, web `2ab2595`, polish до 2026-04-30.
- [x] [#20](https://github.com/svoronkov-les13/finguide-be/issues/20) — registered full name вместо placeholder — merge `36c3d1d`.
- [x] [#22](https://github.com/svoronkov-les13/finguide-be/issues/22) — user-owned current plan after login — commit `5b36524`.
- [x] [#24](https://github.com/svoronkov-les13/finguide-be/issues/24) — no demo/default plan flash during session restore — backend `d6213d1`, web `1197b24`.
- [x] [finguide-web#5](https://github.com/svoronkov-les13/finguide-web/issues/5) — sidebar counters from persisted plan — commit `d92aea0`.

## Дальше

### CI/CD и guardrails — 2026-05-14

- [x] Backend auto-deploy из `main` через self-hosted GitHub Actions runner на `66.42.121.18` — commit `1ed1fed`.
- [x] Frontend auto-deploy из `main` через self-hosted GitHub Actions runner на `66.42.121.18` — web commit `ce9e8eb`.
- [x] [#16](https://github.com/svoronkov-les13/finguide-be/issues/16) — OpenAPI coverage guard для real Swagger — commit `3cc7165`.

### Iteration 1 — guardrails + persisted analytics, 2026-05-04 → 2026-05-10

- [ ] [#26](https://github.com/svoronkov-les13/finguide-be/issues/26) — запретить мутацию общего anonymous demo seed plan.
- [ ] [#4](https://github.com/svoronkov-les13/finguide-be/issues/4) — analytics/cashflow/pension из persisted state.

### Iteration 2 — persisted domain expansion, 2026-05-11 → 2026-05-20

- [ ] [finguide-web#2](https://github.com/svoronkov-les13/finguide-web/issues/2) — smoke generated client после стабилизации контракта.
- [ ] [#11](https://github.com/svoronkov-les13/finguide-be/issues/11) — pension settings endpoints.
- [ ] [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) — contributions ledger endpoints.
- [ ] [#12](https://github.com/svoronkov-les13/finguide-be/issues/12) — budget/monthly tracker endpoints.

### Iteration 3 — scenarios + account + async perimeter, 2026-05-25 → 2026-06-09

- [ ] [#13](https://github.com/svoronkov-les13/finguide-be/issues/13) — scenarios CRUD/compare.
- [ ] [#7](https://github.com/svoronkov-les13/finguide-be/issues/7) — replace current plan endpoint.
- [ ] [#8](https://github.com/svoronkov-les13/finguide-be/issues/8) — profile/avatar/account endpoints.
- [ ] [#14](https://github.com/svoronkov-les13/finguide-be/issues/14) — import/export jobs.
- [ ] [#15](https://github.com/svoronkov-les13/finguide-be/issues/15) — notifications endpoints.

## Почему такой порядок

1. Guardrails первыми: OpenAPI coverage и seed immutability уменьшают риск сломать demo/contract поведение.
2. Затем расчёты из persisted state: это ядро продуктовой ценности.
3. Потом доменные write-фичи, влияющие на расчёты: pension, contributions, budget.
4. Сценарии и replace-plan после стабилизации состояния плана.
5. Import/export и notifications — perimeter/async слой, не ядро MVP.
