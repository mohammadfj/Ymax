# P0-1-WIDGET-REMEDIATION-PLAN — Ymax 2026-11-27

Mode: plan only. No write executed. Requires explicit PO approval before any code/config change.

---

## 1. Current state (OBSERVED live)

`GET /api/widget/config` → HTTP 200, no auth guard, returns JSON containing:
- `auth: { username: "visitor", password: "[REDACTED]" }` — visitor SIP credential
- `ice_servers[]`: `turn:192.168.250.42:3478?transport=tcp/udp` + `turn:192.168.100.65:3478?transport=udp`, each with `username: "<ts>:t1"` and `credential: "[REDACTED]"` — TURN credentials
- `brand.name = "وای‌مکس"` (Ymax brand preserved)

The code comment claims "P0-1 fix… no longer leaks TURN or visitor SIP credentials to unauthenticated visitors," but the handler still returns them. `Cache-Control: no-store` (the earlier cache fix) is present, but the sensitive fields themselves remain in the anonymous response.

## 2. Problem statement

- Anonymous visitors receive long-lived SIP + TURN credentials in plaintext.
- `tenantId` can be supplied via resolver/host but no domain-ownership verification exists (single-tenant fallback to tenant 1).
- TURN credentials are short-lived by design (minted per request), but SIP `visitorPass` comes from the tenant row and is static until rotated.

## 3. Security requirements (per PO protocol #4: no secrets exposed)

- No TURN/SIP credentials in a response reachable without authentication.
- Public Visitor Widget must remain usable without the visitor logging in as Admin/Operator.
- Backend derives Tenant from a **trusted source** (resolver: host/subdomain/JWT claim). Client-supplied `tenantId` alone must not authorize credential issuance.
- Fail closed when tenant identity is missing/invalid.

## 4. Recommended design (option A — separation)

| Endpoint | Auth | Returns |
|----------|------|---------|
| `GET /api/widget/config` | none (public) | `enabled`, `ws_url`, `dial_ext`, `brand`, `support_phone`, `ice_servers` with **STUN only** (no `username`/`credential`), `auth` **omitted** |
| `GET /api/widget/session` | visitor session token (short-lived, issued per visitor) | SIP `auth.{username,password}` + short-lived TURN `ice_servers` creds |

Flow:
1. Public widget loads `/api/widget/config` → gets public fields + STUN server only.
2. Visitor initiates a call → frontend calls `POST /api/widget/visitor-session` (new) with visitor context → backend creates visitor session row, derives tenant from resolver, issues short-lived token.
3. Frontend uses token at `GET /api/widget/session` → receives SIP password + TURN credentials (short TTL).

### Option B (auth guard on whole endpoint — not recommended alone)
Adding `auth(["admin","operator"])` breaks the anonymous Visitor Widget unless `widget.js` is updated to authenticate first. Not acceptable as the sole fix for a public widget.

## 5. Side effects / dependencies
- `widget.js` must be updated to the two-endpoint flow (frontend change).
- `cache-control: public` must never be re-added to any credential-bearing endpoint.
- Existing exposed credentials should be **rotated** (PO decision) since they were served publicly.

## 6. Tests (staging / test data; no production brute-force)
- `GET /api/widget/config` anonymous → 200, **no** `username`/`credential`/`password` fields.
- `GET /api/widget/session` with valid short-lived token → 200 with creds.
- `GET /api/widget/session` with invalid/missing token → 401.
- Cross-tenant: tenant A session token must not return tenant B creds.

## 7. Backup / rollback
- Backup `/opt/callcenter-web/backend/server.js` + `backend/public/widget.js` before any write (per protocol #2).
- Rollback: restore pre-change files + `docker compose up -d app` (no db change).

## 8. Production impact
- No restart of active calls needed (0 active calls observed at audit time).
- Widget behavior changes for anonymous visitors (STUN-only until session) — coordinate with PO on UX.

**Status: PLAN ONLY. Awaiting PO approval.**
