#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p mock-java/build/classes
javac --release 21 -d mock-java/build/classes src/mock/java/les13/finguide/mock/FinGuideMockServer.java
jar --create --file mock-java/build/finguide-mock.jar --main-class les13.finguide.mock.FinGuideMockServer -C mock-java/build/classes .
PORT="${PORT:-3092}" OPENAPI_PATH="${OPENAPI_PATH:-openapi/openapi-mock.json}" java -jar mock-java/build/finguide-mock.jar
