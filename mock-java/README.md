# Java 21 mock server

Small dependency-free Java 21 HTTP server for FinGuide mock Swagger and deterministic stub responses. Source lives in the classic main source tree: `src/main/java`.

## Build and run

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

Demo bearer:

```txt
Bearer mock-access-token-java21
```
