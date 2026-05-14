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

### Roadmap pivot: FinPlan design system — 2026-05-14+

Новый frontend-дизайн [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7) меняет порядок работ: это не разовая косметическая задача, а UX/UI-направление, которое нужно вести параллельно с backend-доменом. Внедрение идёт вручную в текущий React/Vite frontend, без прямого импорта сгенерированного Figma-кода.

Базовые правила:

- большой redesign дробится на маленькие issues/PR с visual before/after screenshots;
- сначала фиксируются design tokens, app shell и shared UI primitives;
- backend endpoints делаются в том порядке, в котором они разблокируют реальные экраны;
- недостающие API fields из Figma-design выносятся в backend follow-up issues, а не маскируются mock-данными во frontend.

### Now — persisted analytics follow-up, UI foundation

- [x] [#26](https://github.com/svoronkov-les13/finguide-be/issues/26) — запретить мутацию общего anonymous demo seed plan — PR #28, merge `08776ab`.
- [x] [#4](https://github.com/svoronkov-les13/finguide-be/issues/4) — analytics/cashflow/pension из persisted state: persisted assumptions/balance/projection/pension endpoints, TDD coverage, OpenAPI gap уменьшен до 26 операций — PR #29, merge `d430187`.
- [x] [#11](https://github.com/svoronkov-les13/finguide-be/issues/11) — persisted pension settings endpoints `GET/PATCH /plans/{planId}/pension`, TDD coverage, OpenAPI gap уменьшен до 24 операций.
- [x] [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) — contributions ledger endpoints `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}`, TDD coverage, OpenAPI gap уменьшен до 19 операций.
- [x] [#12](https://github.com/svoronkov-les13/finguide-be/issues/12) — budget/monthly tracker endpoints `GET/PATCH /plans/{planId}/budget`, `POST /plans/{planId}/budget/envelopes/autogenerate`, `GET/POST /plans/{planId}/calendar/monthly-tracker`, TDD coverage, OpenAPI gap уменьшен до 14 операций.
- [ ] [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7) — выделить первые implementation issues: design tokens, app shell/sidebar/topbar, shared UI primitives, dashboard desktop target.

### Next — frontend contract smoke + first redesigned screens

- [ ] [finguide-web#2](https://github.com/svoronkov-les13/finguide-web/issues/2) — smoke generated client после стабилизации контракта.
- [ ] Frontend по [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7): dashboard redesign и onboarding/common data screens.

### Then — tracker, scenarios, tables

- [ ] [#13](https://github.com/svoronkov-les13/finguide-be/issues/13) — scenarios CRUD/compare.
- [ ] Frontend по [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7): incomes/expenses, goals, scenario tables, tracker UI.

### Later — account, replace-plan, async perimeter, polish

- [ ] [#7](https://github.com/svoronkov-les13/finguide-be/issues/7) — replace current plan endpoint.
- [ ] [#8](https://github.com/svoronkov-les13/finguide-be/issues/8) — profile/avatar/account endpoints.
- [ ] [#14](https://github.com/svoronkov-les13/finguide-be/issues/14) — import/export jobs.
- [ ] [#15](https://github.com/svoronkov-les13/finguide-be/issues/15) — notifications endpoints.
- [ ] Frontend по [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7): settings/account, FAQ/help, instruction modals, final visual polish.

## Почему такой порядок

1. Guardrails первыми: OpenAPI coverage уже зафиксирован, seed immutability нужна до дальнейших мутаций demo state.
2. Persisted analytics остаётся ядром продуктовой ценности: без неё redesign будет красивой оболочкой над неполными расчётами.
3. FinPlan design foundation запускается параллельно, чтобы не переделывать каждую страницу дважды.
4. Pension settings и contributions идут до tracker/scenarios, потому что они влияют на расчёты и отображение cashflow.
5. Tracker/scenarios/account/import/export/notifications двигаются после базовой стабилизации домена и UI primitives.
