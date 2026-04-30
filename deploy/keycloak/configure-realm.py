#!/usr/bin/env python3
"""Configure the FinGuide Keycloak realm from .env without printing secrets."""

from __future__ import annotations

import json
import os
import shlex
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ENV_FILE = ROOT / ".env"
COMPOSE_FILE = ROOT / "compose.yaml"
REALM_JSON = ROOT / "realm-config" / "finguide-realm.json"


def load_env(path: Path) -> dict[str, str]:
    if not path.exists():
        raise SystemExit(f"Missing {path}; copy .env.example to .env and set real secrets first")
    result = os.environ.copy()
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key] = value
    return result


def run(args: list[str], env: dict[str, str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, env=env, check=True, text=True, capture_output=capture)


def compose(env: dict[str, str], *args: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run(["docker", "compose", "--env-file", str(ENV_FILE), "-f", str(COMPOSE_FILE), *args], env, capture=capture)


def kcadm(env: dict[str, str], *args: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return compose(env, "exec", "-T", "keycloak", "/opt/keycloak/bin/kcadm.sh", *args, capture=capture)


def build_realm(env: dict[str, str]) -> dict[str, object]:
    realm = env.get("FINGUIDE_REALM", "finguide")
    web_client = env.get("FINGUIDE_WEB_CLIENT_ID", "finguide-web")
    redirect = env.get("FINGUIDE_WEB_REDIRECT_URI", "http://66.42.121.18/fg/auth/callback")
    post_logout = env.get("FINGUIDE_WEB_POST_LOGOUT_REDIRECT_URI", "http://66.42.121.18/fg/login")
    origin = redirect.split("/fg/", 1)[0]
    ssl_required = "none" if env.get("KEYCLOAK_PUBLIC_URL", "").startswith("http://") else "external"
    return {
        "realm": realm,
        "enabled": True,
        "displayName": "FinGuide",
        "registrationAllowed": True,
        "resetPasswordAllowed": True,
        "rememberMe": True,
        "internationalizationEnabled": True,
        "supportedLocales": ["ru", "en"],
        "defaultLocale": "ru",
        "sslRequired": ssl_required,
        "loginTheme": "finguide",
        "accountTheme": "finguide",
        "roles": {"realm": [{"name": "user"}, {"name": "admin"}]},
        "clients": [
            {
                "clientId": "finguide-api",
                "name": "FinGuide API",
                "enabled": True,
                "protocol": "openid-connect",
                "publicClient": False,
                "standardFlowEnabled": False,
                "directAccessGrantsEnabled": False,
                "serviceAccountsEnabled": False,
                "redirectUris": [],
                "webOrigins": [],
            },
            {
                "clientId": web_client,
                "name": "FinGuide Web",
                "enabled": True,
                "protocol": "openid-connect",
                "publicClient": True,
                "standardFlowEnabled": True,
                "directAccessGrantsEnabled": False,
                "implicitFlowEnabled": False,
                "redirectUris": [redirect],
                "webOrigins": [origin],
                "attributes": {
                    "pkce.code.challenge.method": "S256",
                    "post.logout.redirect.uris": post_logout,
                },
                "protocolMappers": [
                    {
                        "name": "finguide-api-audience",
                        "protocol": "openid-connect",
                        "protocolMapper": "oidc-audience-mapper",
                        "consentRequired": False,
                        "config": {
                            "included.client.audience": "finguide-api",
                            "id.token.claim": "false",
                            "access.token.claim": "true",
                        },
                    }
                ],
            },
        ],
    }


def shell_join(args: list[str]) -> str:
    return " ".join(shlex.quote(value) for value in args)


def main() -> int:
    env = load_env(ENV_FILE)
    realm_name = env.get("FINGUIDE_REALM", "finguide")
    REALM_JSON.parent.mkdir(parents=True, exist_ok=True)
    REALM_JSON.write_text(json.dumps(build_realm(env), ensure_ascii=False, indent=2) + "\n")

    compose(env, "cp", str(REALM_JSON), "keycloak:/tmp/finguide-realm.json")
    kcadm(
        env,
        "config",
        "credentials",
        "--server",
        "http://localhost:8080/auth",
        "--realm",
        "master",
        "--user",
        env["KEYCLOAK_ADMIN_USERNAME"],
        "--password",
        env["KEYCLOAK_ADMIN_PASSWORD"],
    )

    master_ssl_required = "none" if env.get("KEYCLOAK_PUBLIC_URL", "").startswith("http://") else "external"
    kcadm(env, "update", "realms/master", "-s", f"sslRequired={master_ssl_required}")

    exists = subprocess.run(
        ["docker", "compose", "--env-file", str(ENV_FILE), "-f", str(COMPOSE_FILE), "exec", "-T", "keycloak", "/opt/keycloak/bin/kcadm.sh", "get", f"realms/{realm_name}"],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode == 0

    if exists:
        realm = build_realm(env)
        kcadm(
            env,
            "update",
            f"realms/{realm_name}",
            "-s",
            "enabled=true",
            "-s",
            "registrationAllowed=true",
            "-s",
            "resetPasswordAllowed=true",
            "-s",
            "rememberMe=true",
            "-s",
            "internationalizationEnabled=true",
            "-s",
            "supportedLocales=[\"ru\",\"en\"]",
            "-s",
            "defaultLocale=ru",
            "-s",
            f"sslRequired={realm['sslRequired']}",
            "-s",
            "loginTheme=finguide",
            "-s",
            "accountTheme=finguide",
        )
        for role in ("user", "admin"):
            if subprocess.run(
                ["docker", "compose", "--env-file", str(ENV_FILE), "-f", str(COMPOSE_FILE), "exec", "-T", "keycloak", "/opt/keycloak/bin/kcadm.sh", "get", f"roles/{role}", "-r", realm_name],
                cwd=ROOT,
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode != 0:
                kcadm(env, "create", "roles", "-r", realm_name, "-s", f"name={role}")
    else:
        kcadm(env, "create", "realms", "-f", "/tmp/finguide-realm.json")

    print(f"Configured Keycloak realm {realm_name} from {REALM_JSON}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
