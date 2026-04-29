#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
compose = (root / "deploy/keycloak/compose.yaml").read_text()
env = (root / "deploy/keycloak/.env.example").read_text()
readme = (root / "deploy/keycloak/README.md").read_text()
checks = {
    "keycloak service": re.search(r"^  keycloak:\n", compose, re.M),
    "postgres service": "keycloak-postgres:" in compose,
    "postgres image": "postgres:16" in compose,
    "keycloak postgres db": "KC_DB: postgres" in compose,
    "persistent volume": "keycloak-postgres-data" in compose,
    "healthcheck": compose.count("healthcheck:") >= 2,
    "restart policy": compose.count("restart: unless-stopped") >= 2,
    "env example secrets": all(k in env for k in ["KEYCLOAK_ADMIN_PASSWORD", "KEYCLOAK_DB_PASSWORD", "KEYCLOAK_PUBLIC_URL"]),
    "theme mounted": "themes/finguide" in compose,
    "backup docs": "pg_dump" in readme and "Restore" in readme,
    "no real secrets": "github_pat_" not in compose + env + readme and "change-me-generate-secret" in env,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Keycloak compose verification failed: " + ", ".join(failed))
print("Keycloak compose verification passed")
