import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class FinGuideMockServer {
    private static final String API_PREFIX = "/api/v1";
    private static final Path OPENAPI_PATH = Path.of(System.getenv().getOrDefault("OPENAPI_PATH", "openapi-mock.json"));

    private static final String PROFILE = """
        {
          "id": "11111111-1111-4111-8111-111111111111",
          "name": "Александр Петров",
          "email": "alex.petrov@example.com",
          "phone": "+7 (903) 555-12-34",
          "avatarUrl": null,
          "age": 32,
          "gender": "male",
          "initialBalance": 2500000,
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """;

    private static final String PENSION = """
        {
          "currentAge": 32,
          "retirementAge": 60,
          "monthlyExpenses": 120000,
          "currency": "RUB",
          "expectedReturnPct": 9,
          "inflationPct": 7,
          "withdrawalStrategy": "spend_down_30y",
          "statePensionEnabled": true,
          "statePensionMonthly": 22000
        }
        """;

    private static final String INCOME = """
        {
          "id": "inc-1",
          "name": "Зарплата",
          "amount": 280000,
          "currency": "RUB",
          "frequency": "monthly",
          "growthType": "manual",
          "growthPct": 8,
          "startDate": "2024-01-01",
          "endDate": null,
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """;

    private static final String INCOMES = """
        [
          {
            "id": "inc-1",
            "name": "Зарплата",
            "amount": 280000,
            "currency": "RUB",
            "frequency": "monthly",
            "growthType": "manual",
            "growthPct": 8,
            "startDate": "2024-01-01",
            "endDate": null,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "inc-2",
            "name": "Фриланс-проекты",
            "amount": 65000,
            "currency": "RUB",
            "frequency": "monthly",
            "growthType": "manual",
            "growthPct": 5,
            "startDate": "2024-06-01",
            "endDate": null,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "inc-3",
            "name": "Дивиденды",
            "amount": 180000,
            "currency": "RUB",
            "frequency": "yearly",
            "growthType": "manual",
            "growthPct": 10,
            "startDate": "2025-04-15",
            "endDate": null,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          }
        ]
        """;

    private static final String EXPENSE = """
        {
          "id": "exp-1",
          "name": "Ипотека",
          "amount": 85000,
          "currency": "RUB",
          "frequency": "monthly",
          "growthType": "manual",
          "growthPct": 0,
          "growthLabel": "Фикс. ставка",
          "budgetClass": "needs",
          "startDate": "2023-01-01",
          "endDate": "2043-01-01",
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """;

    private static final String EXPENSES = """
        [
          {
            "id": "exp-1",
            "name": "Ипотека",
            "amount": 85000,
            "currency": "RUB",
            "frequency": "monthly",
            "growthType": "manual",
            "growthPct": 0,
            "growthLabel": "Фикс. ставка",
            "budgetClass": "needs",
            "startDate": "2023-01-01",
            "endDate": "2043-01-01",
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "exp-2",
            "name": "Продукты и быт",
            "amount": 42000,
            "currency": "RUB",
            "frequency": "monthly",
            "growthType": "inflation",
            "growthPct": 7,
            "growthLabel": "Инфляция",
            "budgetClass": "needs",
            "startDate": "2024-01-01",
            "endDate": null,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "exp-4",
            "name": "Рестораны и кафе",
            "amount": 22000,
            "currency": "RUB",
            "frequency": "monthly",
            "growthType": "manual",
            "growthPct": 4,
            "growthLabel": "+4% / год",
            "budgetClass": "wants",
            "startDate": "2024-01-01",
            "endDate": null,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          }
        ]
        """;

    private static final String GOAL = """
        {
          "id": "goal-1",
          "name": "Финансовая подушка",
          "icon": "shield",
          "currentCost": 1500000,
          "savedAmount": 920000,
          "currency": "RUB",
          "targetYear": 2027,
          "type": "one_time",
          "growthType": "inflation",
          "growthPct": 7,
          "indexLabel": "Инфляция",
          "priority": 1,
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """;

    private static final String GOALS = """
        [
          {
            "id": "goal-1",
            "name": "Финансовая подушка",
            "icon": "shield",
            "currentCost": 1500000,
            "savedAmount": 920000,
            "currency": "RUB",
            "targetYear": 2027,
            "type": "one_time",
            "growthType": "inflation",
            "growthPct": 7,
            "indexLabel": "Инфляция",
            "priority": 1,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "goal-2",
            "name": "Первый взнос на квартиру",
            "icon": "home",
            "currentCost": 5000000,
            "savedAmount": 1350000,
            "currency": "RUB",
            "targetYear": 2029,
            "type": "one_time",
            "growthType": "manual",
            "growthPct": 8,
            "indexLabel": "+8% / год",
            "priority": 2,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "goal-3",
            "name": "Новый автомобиль",
            "icon": "car",
            "currentCost": 3500000,
            "savedAmount": 870000,
            "currency": "RUB",
            "targetYear": 2028,
            "type": "one_time",
            "growthType": "inflation",
            "growthPct": 5,
            "indexLabel": "Инфляция",
            "priority": 3,
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          }
        ]
        """;

    private static final String CONTRIBUTION = """
        {
          "id": "contr-1",
          "goalId": "goal-1",
          "amount": 120000,
          "currency": "RUB",
          "date": "2025-11-05",
          "note": "Бонус за квартал",
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """;

    private static final String CONTRIBUTIONS = """
        [
          {
            "id": "contr-1",
            "goalId": "goal-1",
            "amount": 120000,
            "currency": "RUB",
            "date": "2025-11-05",
            "note": "Бонус за квартал",
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "contr-2",
            "goalId": "goal-2",
            "amount": 200000,
            "currency": "RUB",
            "date": "2025-10-15",
            "note": "Из дивидендов",
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          }
        ]
        """;

    private static final String BUDGET = """
        {
          "method": "503020",
          "envelopes": [
            {"id":"exp-1","name":"Ипотека","limit":85000,"icon":"home","color":"#C9A84C","spent":85000,"remaining":0,"pct":100,"isOver":false},
            {"id":"exp-2","name":"Продукты и быт","limit":46000,"icon":"shopping","color":"#5AD1D1","spent":42000,"remaining":4000,"pct":91.3,"isOver":false},
            {"id":"exp-4","name":"Рестораны и кафе","limit":25000,"icon":"utensils","color":"#F59E0B","spent":22000,"remaining":3000,"pct":88,"isOver":false}
          ],
          "classifications": {
            "exp-1": "needs",
            "exp-2": "needs",
            "exp-4": "wants"
          }
        }
        """;

    private static final String PLAN = """
        {
          "id": "plan_demo",
          "profile": %s,
          "pension": %s,
          "incomes": %s,
          "expenses": %s,
          "goals": %s,
          "contributions": %s,
          "budget": %s,
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """.formatted(PROFILE, PENSION, INCOMES, EXPENSES, GOALS, CONTRIBUTIONS, BUDGET);

    private static final String AUTH_TOKENS = """
        {
          "accessToken": "mock-access-token-java21",
          "refreshToken": "mock-refresh-token-java21",
          "expiresIn": 3600,
          "user": %s
        }
        """.formatted(PROFILE);

    private static final String DASHBOARD = """
        {
          "totalMonthlyIncome": 360000,
          "totalYearlyIncome": 4500000,
          "totalMonthlyExpenses": 149000,
          "totalYearlyExpenses": 1788000,
          "netMonthlyBalance": 211000,
          "netYearlyBalance": 2712000,
          "savingsRatePct": 58.6,
          "totalGoalsCost": 10000000,
          "totalGoalsSaved": 3140000,
          "totalGoalsRemaining": 6860000,
          "monthlyGoalContribution": 148000,
          "availableForPension": 63000,
          "projectedPensionCapital": 84200000,
          "yearsToRetirement": 28,
          "emergencyFundTarget": 894000,
          "emergencyFundCurrent": 920000,
          "emergencyFundPct": 100,
          "yearlyProjection": [
            {"year":2026,"income":4500000,"expenses":1788000,"goalsCost":900000,"netSavings":1812000},
            {"year":2027,"income":4836000,"expenses":1902000,"goalsCost":650000,"netSavings":4000000},
            {"year":2028,"income":5200000,"expenses":2035000,"goalsCost":2900000,"netSavings":4590000},
            {"year":2029,"income":5590000,"expenses":2177000,"goalsCost":4100000,"netSavings":3510000}
          ]
        }
        """;

    private static final String HEALTH = """
        {
          "score": 84,
          "items": [
            {"key":"savings_rate","label":"Норма сбережений","value":58.6,"status":"good","hint":"Выше рекомендуемых 20%"},
            {"key":"emergency_fund","label":"Подушка безопасности","value":100,"status":"good","hint":"Резерв покрывает 6 месяцев расходов"},
            {"key":"diversification","label":"Диверсификация","value":3,"status":"warning","hint":"Добавьте ещё 1-2 источника дохода"}
          ]
        }
        """;

    private static final String SCENARIO = """
        {
          "id": "base",
          "name": "Основной план",
          "emoji": "📊",
          "description": "Ваш текущий финансовый план",
          "isBase": true,
          "basePlanId": "plan_demo",
          "snapshot": %s,
          "adjustments": {"incomeAdjPct":0,"expenseAdjPct":0,"returnAdjPct":0,"inflationAdjPct":0,"retirementAgeShift":0,"goalsCostAdjPct":0},
          "createdAt": "2026-04-28T09:00:00Z",
          "updatedAt": "2026-04-28T09:00:00Z"
        }
        """.formatted(PLAN);

    private static final String SCENARIOS = """
        [
          %s,
          {
            "id": "optimistic",
            "name": "Оптимистичный",
            "emoji": "🚀",
            "description": "Доходы растут быстрее, расходы медленнее",
            "isBase": false,
            "basePlanId": "plan_demo",
            "adjustments": {"incomeAdjPct":15,"expenseAdjPct":5,"returnAdjPct":1,"inflationAdjPct":-1,"retirementAgeShift":-2,"goalsCostAdjPct":0},
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          },
          {
            "id": "pessimistic",
            "name": "Пессимистичный",
            "emoji": "⚡",
            "description": "Стресс-тест: снижение доходов и рост расходов",
            "isBase": false,
            "basePlanId": "plan_demo",
            "adjustments": {"incomeAdjPct":-10,"expenseAdjPct":12,"returnAdjPct":-2,"inflationAdjPct":2,"retirementAgeShift":3,"goalsCostAdjPct":15},
            "createdAt": "2026-04-28T09:00:00Z",
            "updatedAt": "2026-04-28T09:00:00Z"
          }
        ]
        """.formatted(SCENARIO);

    private static final String NOTIFICATIONS = """
        [
          {"id":"notif-1","type":"milestone","category":"goal","title":"Подушка безопасности сформирована","description":"Резерв покрывает 6 месяцев расходов","severity":"important","read":false,"createdAt":"2026-04-28T09:00:00Z"},
          {"id":"notif-2","type":"tip","category":"income","title":"Диверсификация доходов","description":"Добавьте ещё один источник дохода для снижения риска","severity":"normal","read":false,"createdAt":"2026-04-28T09:00:00Z"}
        ]
        """;

    private static final String EXPORT_JOB = """
        {
          "id": "export-demo-1",
          "status": "done",
          "format": "json",
          "downloadUrl": "http://66.42.121.18/finguide-mock/downloads/export-demo-1.json",
          "expiresAt": "2026-04-29T09:00:00Z"
        }
        """;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3092"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", FinGuideMockServer::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("FinGuide Java 21 mock server listening on http://127.0.0.1:%d%n", port);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = normalize(exchange.getRequestURI().getPath());
        addCommonHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (path.equals("/") || path.equals("/swagger") || path.equals("/swagger-ui")) {
            html(exchange, 200, swaggerHtml());
            return;
        }
        if (path.equals("/healthz")) {
            text(exchange, 200, "ok\n");
            return;
        }
        if (path.equals("/openapi.json")) {
            json(exchange, 200, Files.readString(OPENAPI_PATH, StandardCharsets.UTF_8));
            return;
        }
        if (path.startsWith("/downloads/")) {
            json(exchange, 200, data(PLAN));
            return;
        }
        if (!path.startsWith(API_PREFIX)) {
            json(exchange, 404, error("NOT_FOUND", "No mock route for " + method + " " + path));
            return;
        }

        byte[] ignoredBody = exchange.getRequestBody().readAllBytes();
        String api = normalize(path.substring(API_PREFIX.length()));
        routeApi(exchange, method, api, ignoredBody.length);
    }

    private static void routeApi(HttpExchange exchange, String method, String api, int bodySize) throws IOException {
        if (api.matches("/auth/(register|login|refresh)")) {
            json(exchange, method.equals("POST") && api.endsWith("register") ? 201 : 200, data(AUTH_TOKENS));
            return;
        }
        if (api.equals("/auth/logout") && method.equals("POST")) { noContent(exchange, 204); return; }
        if (api.equals("/auth/password/forgot") && method.equals("POST")) { noContent(exchange, 202); return; }
        if (api.equals("/auth/password/reset") && method.equals("POST")) { noContent(exchange, 204); return; }

        if (api.equals("/me") && (method.equals("GET") || method.equals("PATCH"))) { json(exchange, 200, data(PROFILE)); return; }
        if (api.equals("/me/avatar") && (method.equals("PUT") || method.equals("DELETE"))) { json(exchange, 200, data(PROFILE)); return; }
        if (api.equals("/me/password") && method.equals("PUT")) { noContent(exchange, 204); return; }

        if (api.equals("/plans/current") && (method.equals("GET") || method.equals("PUT"))) { json(exchange, 200, data(PLAN)); return; }

        if (api.matches("/plans/[^/]+/goals/reorder") && method.equals("POST")) { json(exchange, 200, data(GOALS)); return; }
        if (api.matches("/plans/[^/]+/pension") && (method.equals("GET") || method.equals("PATCH"))) { json(exchange, 200, data(PENSION)); return; }
        if (api.matches("/plans/[^/]+/budget") && (method.equals("GET") || method.equals("PATCH"))) { json(exchange, 200, data(BUDGET)); return; }
        if (api.matches("/plans/[^/]+/budget/envelopes/autogenerate") && method.equals("POST")) { json(exchange, 200, data(BUDGET)); return; }
        if (api.matches("/plans/[^/]+/dashboard") && method.equals("GET")) { json(exchange, 200, data(DASHBOARD)); return; }
        if (api.matches("/plans/[^/]+/analytics/projection") && method.equals("GET")) {
            json(exchange, 200, data("""
                [
                  {"year":2026,"income":4500000,"expenses":1788000,"goalsCost":900000,"netSavings":1812000},
                  {"year":2027,"income":4836000,"expenses":1902000,"goalsCost":650000,"netSavings":4000000},
                  {"year":2028,"income":5200000,"expenses":2035000,"goalsCost":2900000,"netSavings":4590000}
                ]
                """));
            return;
        }
        if (api.matches("/plans/[^/]+/analytics/health") && method.equals("GET")) { json(exchange, 200, data(HEALTH)); return; }
        if (api.matches("/plans/[^/]+/calendar/monthly-tracker") && method.equals("GET")) {
            json(exchange, 200, data("""
                [
                  {"month":"2026-01","status":"completed","plannedAmount":148000,"actualAmount":155000,"note":"Выше плана"},
                  {"month":"2026-02","status":"partial","plannedAmount":148000,"actualAmount":90000,"note":"Часть ушла на ремонт"},
                  {"month":"2026-03","status":"completed","plannedAmount":148000,"actualAmount":148000,"note":"По плану"}
                ]
                """));
            return;
        }
        if (api.matches("/plans/[^/]+/calendar/monthly-tracker") && method.equals("POST")) { noContent(exchange, 204); return; }

        if (crud(exchange, method, api, "incomes", INCOMES, INCOME)) return;
        if (crud(exchange, method, api, "expenses", EXPENSES, EXPENSE)) return;
        if (crud(exchange, method, api, "goals", GOALS, GOAL)) return;
        if (crud(exchange, method, api, "contributions", CONTRIBUTIONS, CONTRIBUTION)) return;

        if (api.equals("/scenarios") && method.equals("GET")) { json(exchange, 200, data(SCENARIOS)); return; }
        if (api.equals("/scenarios") && method.equals("POST")) { json(exchange, 201, data(SCENARIO)); return; }
        if (api.matches("/scenarios/[^/]+") && (method.equals("GET") || method.equals("PATCH"))) { json(exchange, 200, data(SCENARIO)); return; }
        if (api.matches("/scenarios/[^/]+") && method.equals("DELETE")) { noContent(exchange, 204); return; }
        if (api.equals("/scenarios/compare") && method.equals("POST")) {
            json(exchange, 200, data("""
                {
                  "scenarioAId":"base",
                  "scenarioBId":"optimistic",
                  "deltaNetWorth":12800000,
                  "deltaRetirementAge":-2,
                  "summary":"Оптимистичный сценарий ускоряет достижение целей и увеличивает пенсионный капитал."
                }
                """));
            return;
        }

        if (api.equals("/notifications") && method.equals("GET")) { json(exchange, 200, data(NOTIFICATIONS)); return; }
        if (api.equals("/notifications/read") && method.equals("POST")) { noContent(exchange, 204); return; }
        if (api.equals("/import") && method.equals("POST")) { json(exchange, 200, data(PLAN)); return; }
        if (api.equals("/export") && method.equals("POST")) { json(exchange, 201, data(EXPORT_JOB)); return; }
        if (api.matches("/export/[^/]+") && method.equals("GET")) { json(exchange, 200, data(EXPORT_JOB)); return; }

        json(exchange, 404, error("NOT_FOUND", "No mock route for " + method + " " + API_PREFIX + api));
    }

    private static boolean crud(HttpExchange exchange, String method, String api, String name, String listJson, String itemJson) throws IOException {
        String collectionPattern = "/plans/[^/]+/" + name;
        String itemPattern = collectionPattern + "/[^/]+";
        if (api.matches(collectionPattern)) {
            if (method.equals("GET")) { json(exchange, 200, data(listJson)); return true; }
            if (method.equals("POST")) { json(exchange, 201, data(itemJson)); return true; }
        }
        if (api.matches(itemPattern)) {
            if (method.equals("GET") || method.equals("PATCH")) { json(exchange, 200, data(itemJson)); return true; }
            if (method.equals("DELETE")) { noContent(exchange, 204); return true; }
        }
        return false;
    }

    private static String swaggerHtml() {
        return """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>FinGuide Mock Swagger</title>
              <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
              <style>
                :root { color-scheme: light; }
                body { margin:0; background:#f8f6f0; color:#261d10; }
                .topbar { display:none; }
                .swagger-ui { color:#261d10; }
                .swagger-ui .info .title { color:#1f1608; }
                .swagger-ui .info .base-url, .swagger-ui .info li, .swagger-ui .info p { color:#5d5142; }
                .swagger-ui .scheme-container { background:#fffdf8; border:1px solid #eadcc0; box-shadow:0 10px 30px rgba(42,31,12,.06); }
                .swagger-ui .opblock, .swagger-ui .model-box, .swagger-ui section.models { background:#fff; }
                .swagger-ui .opblock-tag, .swagger-ui .models h4, .swagger-ui table thead tr td, .swagger-ui table thead tr th { color:#261d10; }
                .banner { padding:14px 24px; background:#fffaf0; color:#261d10; border-bottom:1px solid #eadcc0; font-family:Inter,system-ui,sans-serif; box-shadow:0 2px 18px rgba(42,31,12,.04); }
                .banner b { color:#a87912; }
                .banner code { padding:3px 7px; border-radius:8px; background:#fff; border:1px solid #eadcc0; color:#111827; font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace; }
              </style>
            </head>
            <body>
              <div class="banner"><b>FinGuide Mock API</b> — Java 21 stubs. Bearer: <code>mock-access-token-java21</code>. Нажми <b>Try it out</b>, чтобы получить мок-ответы с сервера.</div>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                window.ui = SwaggerUIBundle({
                  url: './openapi.json',
                  dom_id: '#swagger-ui',
                  deepLinking: true,
                  tryItOutEnabled: true,
                  persistAuthorization: true,
                  displayRequestDuration: true,
                  defaultModelsExpandDepth: 1,
                  docExpansion: 'none'
                });
              </script>
            </body>
            </html>
            """;
    }

    private static String data(String json) {
        return "{\"data\":" + json + "}";
    }

    private static String error(String code, String message) {
        return """
            {"error":{"code":"%s","message":"%s","requestId":"mock-%s"}}
            """.formatted(escape(code), escape(message), Instant.now().toEpochMilli());
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        String normalized = path.replaceAll("/+$", "");
        return normalized.isEmpty() ? "/" : normalized;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void addCommonHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Requested-With");
        headers.set("Cache-Control", "no-store");
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, body);
    }

    private static void html(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        send(exchange, status, body);
    }

    private static void text(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        send(exchange, status, body);
    }

    private static void noContent(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
