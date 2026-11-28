# CRITICAL PRE-EXECUTION AUDIT — Ymax (P0 Readiness Review)

**Date:** 2026-11-27 · **Mode:** read-only inspection (no writes, no restarts, no config/code/DB/docker changes executed)
**Access:** live inspection via Tailscale `root@100.112.160.17` (host 192.168.250.42). Direct LAN SSH (port 22) times out; Tailscale path works.

---

## Executive Summary

The project docs **overstate readiness**. Three of the four "P0 open" claims in `FULL-WORKFLOW-REPORT-2026-11-27.md` are **factually wrong** and must be corrected before any implementation is sequenced:

| Finding | Evidence label |
|---|---|
| `FULL-WORKFLOW-REPORT` (§4 #8) lists P0-1..P0-4 as "Critical ❌ Active blocker / open" | **CONFLICTING** with live code + `P0-SECURITY-FINAL-REPORT` (2026-11-26) |
| Live code shows **P0-2 and P0-3 are ALREADY implemented** (`auth()` guard + `qTenant()` ownership; `loginGuard` middleware) | **OBSERVED** |
| **P0-1 is still open** — `GET /api/widget/config` returns `200` to an anonymous caller and leaks visitor SIP password + TURN credentials | **OBSERVED** |
| `FULL-WORKFLOW-REPORT` §2d claims TURN REST "E2E verified / production-ready" | **CONFLICTING** — live coturn log shows **65 `Cannot find credentials`** auth failures for app-minted usernames |
| Reported relay addr `192.168.250.42:62018` is **outside** the configured relay range `49160-49200` | **CONFLICTING** |
| Asterisk RTP **and** coturn relay are both configured to the **same** UDP range `49160-49200` on the same host → port-contention risk | **OBSERVED** |
| TURN advertises a **private LAN IP** (`relay-ip=192.168.250.42`, no `external-ip`, no NAT mapping in iptables) → **not usable by an internet visitor** | **OBSERVED** |

**Bottom line:** Do **not** treat P0 work as "start P0-1..P0-4 from scratch." P0-2 and P0-3 need *verification* (re-run the test matrix), P0-1 needs *genuine* remediation (it is still leaking), P0-4 is *partially* done (single-tenant, latent fail-open `|| 1` fallbacks). The TURN "verified" claim needs a **controlled retest** and, regardless of outcome, TURN is currently **LAN-only**, so it is not a substitute for public WebRTC media.

**Immediate containment recommended (PO decision, not executed):** the anonymous leak of SIP + TURN credentials from `/api/widget/config` is the single most urgent item. Rotation of any credential that has ever been served from that endpoint should be considered. No action was taken in this audit.

---

## Confirmed Findings (OBSERVED live, 2026-11-27 19:26 UTC)

### Containers / health
- `callcenter-web-app-1` Up 8h (healthy), `callcenter-web-pbx-1` Up 8h (healthy), `callcenter-web-nginx-1` Up 33h (healthy), `callcenter-web-turn-1` Up 8h, `ymax-sip-lab-asterisk` Up 6h. **`callcenter-web-db-1` (MariaDB standby) is NOT in `docker ps` — it is not currently running.** (Contradicts `AGENT-READABLE-STATUS` §1 which lists it "Up ~1h".)
- `GET /health` → `{"status":"ok","service":"callcenter","version":"3.0.1","db":"ok"}`.
- `core show channels` → **0 active calls** (safe to plan restarts; none in flight at audit time).
- App log clean: `[bootstrap] completed successfully`, no error.

### P0-1 (WIDGET) — still OPEN
- `curl -I /api/widget/config` → **`HTTP/1.1 200 OK`** (no auth guard; `Cache-Control: no-store` — the P0-1 cache fix is present).
- `curl -s /api/widget/config` → 200 JSON containing (values redacted):
  - `auth: { username: "visitor", password: "[REDACTED-SIP-PASS]" }` → **visitor SIP credential exposed**
  - `ice_servers[]`: `turn:192.168.250.42:3478?transport=tcp/udp` **and** `turn:192.168.100.65:3478?transport=udp`, each with `username: "<ts>:t1"` and `credential: "[REDACTED-TURN-CRED]"` → **TURN credentials exposed**
- No `auth([...])` guard on the route (`server.js:226`). Code comment claims "P0-1 fix… no longer leaks", but the handler **still returns the sensitive fields**. The "fix" only changed the *source* (tenant row via `getTenantConfig(tid)`) and cache header — it did **not** remove the fields or add a guard.

### P0-2 (RECORDS IDOR) — IMPLEMENTED
- `server.js:474`: `app.get("/api/records/:uniqueid", auth(["admin","operator"]), ...)` → `qTenant(req.user.tenant_id || 1, "SELECT id FROM calls WHERE tenant_id=:tid AND unique_id=? LIMIT 1")` → 404 if not owned. Ownership + tenant scoping present.
- ⚠ Latent: `req.user.tenant_id || 1` — **fail-open to tenant 1** if the claim is missing.

### P0-3 (LOGIN RATE-LIMIT) — IMPLEMENTED
- `server.js:173`: `app.post("/api/auth/login", loginGuard, ...)`; `loginGuard` from `lib/loginguard` (`server.js:19`). `trust proxy` reported as `loopback` in the final report. Re-verify against the test matrix (do **not** brute-force a real admin account).

### P0-4 (SOCKET.IO) — PARTIAL (single-tenant)
- `runtime.js:323` `io.use((socket, next) => …)` → derives `tid`, joins `tenant:<tid>` + `adminRoom(tid)` / `opsRoom(tid)` / `opChan(tid,ext)` (lines 332-335). Rooms are tenant-scoped.
- **Latent fail-open:** ~15 call sites use `c.tenant_id || 1` (e.g. runtime.js:102,153,159,166,177,199,210,212,230,293). In single-tenant mode this is harmless (only tenant 1 exists), but it is a **fail-open** path that will silently cross into tenant 1 the moment a second tenant exists. DM cross-tenant join check deferred to 2B (per final report).

### TURN / coturn
- **Active rendered config** (`/etc/coturn/turnserver.conf` in container): `listening-port=3478`, `listening-ip=192.168.250.42`, `relay-ip=192.168.250.42`, `min-port=49160`, `max-port=49200`, `use-auth-secret`, `static-auth-secret=[REDACTED]`, `realm=192.168.250.42`, `no-tls`, `no-dtls`, `stale-nonce=600`, `fingerprint`, `user-quota=0`, `total-quota=0`. **No `external-ip` value** (only a comment).
- **Config hygiene bug:** coturn uses `#` comments; the rendered file uses `;` comment lines → coturn logs repeated `Bad configuration format: ;`. The `key=value` lines still parse (relay init completes), but this is a defect to fix.
- **Two `turnserver` PIDs on the host netns:** `13363` (`turnserver -c /etc/coturn/turnserver.conf …`, started 15:12) and `17950` (`turnserver -v`, started 16:27). Both hold `:3478` (UDP SO_REUSEADDR allows co-bind). **Which instance is serving allocations is UNRESOLVED — a material unknown for the 62018 discrepancy.**
- **Auth failures:** coturn log has **65 `ERROR: check_stun_auth: Cannot find credentials of user <…>`** for app-minted usernames (`…:t1`, `…:t99`). This directly contradicts "TURN E2E verified." Possible causes (INFERRED): (a) app `.env TURN_SECRET` ≠ coturn `static-auth-secret`; (b) the `<ts>:<suffix>` username format is rejected by coturn's time parsing under `lt-cred-mech`/`stale-nonce`. **Requires a controlled retest** (not executed here).
- **No relay listener currently bound in 49160-49200** (`ss -lnup` shows no UDP listener in that range). This is *expected* (coturn binds relay ports lazily on allocation), so absence of a listener is **not** proof the range is reserved — it just means 0 active allocations.

### Asterisk RTP
- `rtp.conf` (`/etc/asterisk/rtp.conf` in pbx): `rtpstart=49160`, `rtpend=49200`, `icesupport=yes`, `strictrtp=yes`. **Same 40-port UDP range as coturn relay, same host (both use host networking).**

---

## Conflicting Findings (sources disagree)

1. **P0 open vs P0 done.** `FULL-WORKFLOW-REPORT` §4 says all 4 P0 open; `P0-SECURITY-FINAL-REPORT` + live code say P0-2/P0-3 done, P0-1 still leaking, P0-4 partial. → Use the **live-code + final-report** version.
2. **TURN "E2E verified" vs 65 live auth failures.** `FULL-WORKFLOW-REPORT` §2d + memory `turn-rest-e2e-verified` claim success; live coturn log shows 65 failures. → The E2E claim is **not reproducible from current evidence.**
3. **Relay addr 62018 vs configured range 49160-49200.** Report says the successful allocation returned `:62018` — impossible under the active config. → Either the test hit the `-v` instance (default relay range 49152-65535), or the config's min/max were not honored at test time.
4. **MariaDB standby running vs not.** `AGENT-READABLE-STATUS` lists `callcenter-web-db-1` "Up"; it is **absent from `docker ps`** now. → Standby availability is **UNKNOWN / not guaranteed.**

---

## Critical Risks (ranked)

| # | Risk | Severity | Evidence |
|---|------|----------|---------|
| R1 | Anonymous leak of SIP + TURN credentials from `/api/widget/config` | **Critical** | OBSERVED 200 + creds in body |
| R2 | TURN credential/secret mismatch or malformed username → relay unusable (65 auth failures) | **High** | OBSERVED coturn log |
| R3 | Asterisk RTP + coturn share the same 40-port UDP range on one host → allocation collisions as call/relay concurrency rises | **High** | OBSERVED both `49160-49200` |
| R4 | TURN advertises private LAN IP only; internet WebRTC media will not traverse a public TURN | **High (scope)** | OBSERVED no `external-ip`/NAT |
| R5 | Two co-resident `turnserver` PIDs on `:3478`; active allocator ambiguous | **Medium** | OBSERVED PIDs 13363/17950 |
| R6 | P0-2/P0-4 `req.user.tenant_id \|\| 1` fail-open to tenant 1 | **Medium (latent)** | OBSERVED code |
| R7 | `;` comment bug in rendered coturn config (config-hygiene) | **Medium** | OBSERVED log |
| R8 | MariaDB standby not currently running → rollback target unverified | **Medium** | OBSERVED `docker ps` |
| R9 | CentOS 7.9 EOL, no upstream security patches | **Medium (long-term)** | DOCUMENTED |

---

## Recommended Execution Order (requires PO approval per item — nothing executed in this audit)

1. **Contain P0-1** (design-only here): split public config from sensitive creds (see `P0-1-WIDGET-REMEDIATION-PLAN.md`); decide on credential **rotation** for anything ever served.
2. **Resolve the TURN contradiction** (see `TURN-RTP-CONFLICT-INVESTIGATION.md`): confirm single serving instance, reconcile app `.env TURN_SECRET` with coturn `static-auth-secret`, fix the username time-format if needed, and re-run a controlled `turnutils_uclient` ALLOCATE to capture the *actual* relay port and *actual* auth result.
3. **Separate RTP and TURN port ranges** after capacity analysis (do **not** pick a new range yet).
4. **Verify P0-2 & P0-3** against the test matrix (no code change expected; confirm on staging, not real admin accounts).
5. **Harden P0-4** `|| 1` fail-open + add DM cross-tenant check when tenant 2+ is introduced (with 2B).
6. **Re-baseline the DB decision** (see `POSTGRESQL-MARIADB-DECISION-REVIEW.md`): 7-day health alone is insufficient; verify standby viability + restore feasibility.
7. Proceed to multi-tenant 2B only after P0 gate + DB decision (see `CANONICAL-YMAX-ROADMAP.md`).

## Exact Changes Requiring Product Owner Approval

None were made. The following are *proposed and gated* (each needs explicit PO sign-off + backup + no-active-call window before execution):
- P0-1 remediation code (`server.js` widget handler + `widget.js`) + optional credential rotation.
- coturn config: single instance, `#` comment fix, `external-ip`/public reachability decision, dedicated TURN relay range distinct from RTP.
- P0-2/P0-4 fail-open hardening (`|| 1` → fail-closed).
- DB decision + any `CC_DB_DRIVER` switch + ADR-001 RLS activation.

## Questions That Cannot Be Answered From Available Evidence
- Are app `.env TURN_SECRET` and coturn `static-auth-secret` actually equal? (values not printed, per secret policy — needs a PO-sanctioned comparison.)
- Which `turnserver` PID (13363 vs 17950) is authoritative for allocations?
- Does `192.168.100.65` (second ice_server) belong to a different host/TURN, and is it expected?
- Is the MariaDB standby container stopped intentionally, or did it crash?
- What is the intended public-facing TURN endpoint for internet visitors (if any)?
- Expected concurrent-call ceiling (needed before sizing RTP/TURN ranges)?

**STATUS: STOPPED at read-only audit. No code, config, database, container, or firewall change was executed. Awaiting explicit PO approval before any modification.**
