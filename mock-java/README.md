# Java 21 legacy mock server

Small dependency-free Java 21 HTTP server for deterministic FinGuide stub responses. It is now a **transition-only** tool: primary Swagger/OpenAPI should point to the real Spring Boot backend services.

Primary real services:

```txt
http://66.42.121.18/finguide-api/swagger-ui.html
http://66.42.121.18/finguide-api/v3/api-docs
http://66.42.121.18/finguide-api/api/v1
```

Legacy mock services, kept for comparison during migration:

```txt
http://66.42.121.18/finguide-mock/
http://66.42.121.18/finguide-mock/openapi.json
```

## Build and run mock locally

From repo root:

```bash
./scripts/run-mock.sh
```

Or manually:

```bash
mkdir -p mock-java/build/classes
javac --release 21 -d mock-java/build/classes src/main/java/les13/finguide/mock/FinGuideMockServer.java
jar --create --file mock-java/build/finguide-mock.jar --main-class les13.finguide.mock.FinGuideMockServer -C mock-java/build/classes .
PORT=3092 OPENAPI_PATH=openapi/openapi-mock.json java -jar mock-java/build/finguide-mock.jar
```

Open:

```txt
http://127.0.0.1:3092/
```

Demo bearer for old mock Swagger:

```txt
Bearer mock-access-token-java21
```
