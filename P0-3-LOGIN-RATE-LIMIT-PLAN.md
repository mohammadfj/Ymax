# P0-3-LOGIN-RATE-LIMIT-PLAN — Ymax 2026-11-27

Mode: plan only. No write executed. Requires explicit PO approval before any code change.

---

## 1. Current state (DOCUMENTED, not re-verified live)

`POST /api/auth/login`, server.js:173: `app.post("/api/auth/login", loginGuard, ...)` — `loginGuard` from `lib/loginguard`.
- `trust proxy` reported as `loopback` (P0-SECURITY-FINAL-REPORT).
- `lib/loginguard.js` IP source reported as `req.ip` (after earlier fix).
- Rate-limit: reported 5 req/min per IP, lockout 15 min.

These are **DOCUMENTED claims** — live code was not inspected for the middleware in this audit beyond the route presence.

## 2. Design constraints (from audit)
- Do **not** blindly trust `X-Forwarded-For`; `trust proxy` must match the actual Nginx upstream IP, not blanket `true`.
- Shared-IP / NAT environments: per-IP limits may affect multiple legitimate users behind one modem. Complement with per-account failed-attempt tracking.
- Account enumeration: identical generic failure message for wrong username vs wrong password.
- Progressive delay (cumulative backoff) preferred over immediate hard lockout; lockout auto-expires after 15 min.
- Multi-instance: in-memory store is insufficient if app scales beyond one instance — verify current single-instance deployment or use shared store.

## 3. Proposed policy (for PO approval)
| Layer | Value |
|-------|-------|
| Per-IP limit | 5 req/min |
| Per-account failed attempts | 5 before lockout |
| Lockout duration | 15 min, auto-expire |
| Failure message | identical for bad username / bad password |
| IP source | `req.ip` with `trust proxy = <nginx-ip>` |
| Store | single-instance memory acceptable today; evaluate shared store if multi-instance |
| Progressive delay | optional (1s after 3rd, 5s after 4th) |

## 4. Tests (staging, test account — never a real admin account)
| # | Test | Expected |
|---|------|----------|
| P0-3-1 | 5 failed logins from one IP (test account) | 429 on 6th |
| P0-3-2 | Lockout persists 15 min | 429 until expiry |
| P0-3-3 | Wrong username vs wrong password → same message | enumeration prevented |
| P0-3-4 | Valid credentials | 200 |

## 5. Backup / rollback
- Backup `backend/server.js` + `backend/lib/loginguard.js` before any write.
- Rollback: restore previous files + `docker compose up -d app`.

**Status: PLAN ONLY. Awaiting PO approval.**
