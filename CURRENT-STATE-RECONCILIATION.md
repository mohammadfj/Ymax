# CURRENT-STATE-RECONCILIATION — Ymax 2026-11-27

Evidence labels: OBSERVED (live read-only), DOCUMENTED (project files), INFERRED (logic), UNKNOWN, CONFLICTING.

Access route: Tailscale `root@100.112.160.17` → host `192.168.250.42`. Direct LAN SSH port 22 times out.

---

## Infrastructure (OBSERVED live 2026-11-27 19:26 UTC)

| Item | Value | Label |
|------|-------|-------|
| OS | CentOS 7.9 (EOL) | DOCUMENTED |
| Host IP | 192.168.250.42 (ens192) | OBSERVED |
| Tailscale IP | 100.112.160.17 (mahan-centos) | OBSERVED |
| SSH LAN port 22 | TIMEOUT (no banner) | OBSERVED |
| SSH Tailscale | OK | OBSERVED |
| App container | `callcenter-web-app-1`, Up 8h, healthy | OBSERVED |
| PBX container | `callcenter-web-pbx-1`, Up 8h, healthy | OBSERVED |
| Nginx container | `callcenter-web-nginx-1`, Up 33h, healthy | OBSERVED |
| Turn container | `callcenter-web-turn-1`, Up 8h | OBSERVED |
| SIP Lab container | `ymax-sip-lab-asterisk`, Up 6h (isolated from prod) | OBSERVED |
| MariaDB standby `callcenter-web-db-1` | **ABSENT** — confirmed absent on 2026-11-28 (`docker ps -a | grep -i db` empty); PG only | OBSERVED |
| Health API | `{"status":"ok","service":"callcenter","version":"3.0.1","db":"ok"}` | OBSERVED |
| App log | clean; `[bootstrap] completed successfully` | OBSERVED |
| Active calls | `core show channels` → 0 active | OBSERVED |
| PG 16 | `CC_DB_DRIVER=pg`, `DB_HOST=127.0.0.1`, `DB_PORT=5432` (key names only) | OBSERVED |
| CPU/load | ~0.08 | DOCUMENTED |
| RAM / disk | 8.7% / 29% | DOCUMENTED |

---

## Application / code (OBSERVED live + DOCUMENTED)

| Item | Status | Evidence |
|------|--------|----------|
| `server.js` version | v3.0.1 | OBSERVED `/health` |
| JWT fail-fast (no fallback) | applied | DOCUMENTED (AGENT-READABLE-STATUS §2) |
| SQL translate v2 (MySQL→PG) | applied | DOCUMENTED |
| `lib/db.js` shims (`insertId`/`affectedRows`) | applied | DOCUMENTED |
| Brand | Ymax / وای‌مкс | OBSERVED (`/health`, widget JSON) |
| `GET /api/widget/config` | **public, no auth; returns visitor SIP password + TURN creds** | OBSERVED (HTTP 200 + body) |
| `GET /api/records/:uniqueid` | `auth(["admin","operator"])` + `qTenant` ownership → 404 if not owned | OBSERVED (server.js:474-484) |
| `POST /api/auth/login` | `loginGuard` middleware | OBSERVED (server.js:173) |
| `trust proxy` | reported `loopback` (final report) | DOCUMENTED (not re-verified live) |
| Socket.IO rooms | `tenant:<tid>`, `adminRoom(tid)`, `opsRoom(tid)`, `opChan(tid,ext)` | OBSERVED (runtime.js:332-335) |
| Socket.IO latent `|| 1` | ~15 sites use `c.tenant_id \|\| 1` | OBSERVED (runtime.js) |
| migrations | 001/002/003 tenants + tenant_id + seed | DOCUMENTED |

---

## Asterisk RTP (OBSERVED live)

| Item | Value | Label |
|------|-------|-------|
| RTP range | `rtpstart=49160`, `rtpend=49200` | OBSERVED (`/etc/asterisk/rtp.conf`) |
| RTP capacity (inferred) | 40 UDP ports ≈ 20 concurrent calls (RTP+RTCP each) | INFERRED |
| PBX version | Asterisk 20.21 | OBSERVED |
| max_contacts | 2→1 (operator) | DOCUMENTED |

---

## coturn / TURN (OBSERVED live)

| Item | Value | Label |
|------|-------|-------|
| Container | `callcenter-web-turn-1`, Up 8h | OBSERVED |
| Rendered config path | `/etc/coturn/turnserver.conf` | OBSERVED |
| listening-port / -ip | 3478 / 192.168.250.42 | OBSERVED |
| relay-ip / min-port / max-port | 192.168.250.42 / 49160 / 49200 | OBSERVED |
| Auth mode | `use-auth-secret`, `static-auth-secret=<REDACTED>` | OBSERVED |
| realm | 192.168.250.42 | OBSERVED |
| fingerprint / lt-cred-mech / no-tls / no-dtls | set | OBSERVED |
| stale-nonce | 600 | OBSERVED |
| external-ip | **none** (comment only) | OBSERVED |
| user-quota / total-quota | 0 / 0 | OBSERVED |
| Comment format bug | `;` lines (coturn wants `#`) → `Bad configuration format: ;` spam | OBSERVED (log) |
| turnserver PIDs | 13363 (`-c ...`), 17950 (`-v`) | OBSERVED |
| coturn auth failures | **65** `Cannot find credentials of user <…>` | OBSERVED (log) |
| relay listener bound in 49160-49200 | none currently (`ss -lnup` empty there) | OBSERVED (expected — lazy bind) |
| firewall INPUT zone ports | `dhcpv6-client ssh` only (3478 not in firewalld list) | OBSERVED |

---

## Contradictions vs FULL-WORKFLOW-REPORT-2026-11-27.md

| Claim in report | Reconciled reality | Label |
|-----------------|--------------------|-------|
| P0-1..P0-4 all open blocker | P0-2/P0-3 implemented in code; P0-1 still leaking; P0-4 partial | CONFLICTING |
| TURN REST "E2E verified / production-ready" | coturn log: 65 auth failures; 62018 relay port outside 49160-49200 | CONFLICTING |
| MariaDB standby "Up ~1h" | container absent from `docker ps` | CONFLICTING |
| firewall accepts 192.168.250.0/24 only on 3478/49160-49200 | `firewall-cmd` shows only `dhcpv6-client ssh`; INPUT policy ACCEPT | CONFLICTING / UNKNOWN (host netns vs zone docs) |

---

## Assessment by layer

| Layer | Maturity | Gap |
|-------|----------|-----|
| Single-tenant app | Stable | P0-1 credential leak open |
| Multi-tenancy | Schema ready; resolver exists; **not active** | fail-open `|| 1` fallbacks |
| TURN / WebRTC | LAN-only; auth not reliably passing | need controlled retest + external reachability |
| DB decision | PG active; MariaDB standby unverified | restore feasibility unknown |
| Rollback chain | image tags + pg_dump documented | restore not tested |

**Verified-safe-to-proceed (read-only, executed):** nothing in production was modified.
