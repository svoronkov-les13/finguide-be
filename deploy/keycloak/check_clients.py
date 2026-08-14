#!/usr/bin/env python3
import json, sys
clients = json.load(sys.stdin)
for c in clients:
    cid = c.get("clientId", "")
    if cid in ("finguide-web", "finguide-api"):
        print(f"{cid}:")
        print(f"  redirectUris: {c.get('redirectUris', [])}")
        print(f"  webOrigins: {c.get('webOrigins', [])}")
        print(f"  publicClient: {c.get('publicClient', False)}")
