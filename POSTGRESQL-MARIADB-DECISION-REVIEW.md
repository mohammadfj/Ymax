# POSTGRESQL-MARIADB-DECISION-REVIEW — Ymax 2026-11-27

Mode: plan/review only. No driver switch, no config change, no rollback executed.

---

## 1. Current state (OBSERVED live + DOCUMENTED)

| Item | Value | Label |
|------|-------|-------|
| Active driver | PostgreSQL 16 (`CC_DB_DRIVER=pg`, `DB_PORT=5432`) | OBSERVED |
| PG connection | 127.0.0.1:5432, systemd unit enabled+started | OBSERVED |
| PG data dir | `/var/lib/pgsql16/data/PG_VERSION` present | DOCUMENTED |
| Health `db` | `"ok"` | OBSERVED |
| Migrations | 001/002/003 tenants + tenant_id + seed | DOCUMENTED |
| SQL translation | MariaDB→PG in `lib/db.js` (translateSQL v2) | DOCUMENTED |
| MariaDB standby `callcenter-web-db-1` | **absent from `docker ps`** (CONFLICTS with AGENT-READABLE-STATUS §1) | OBSERVED |
| PG backup | nightly `pg_dump` via `/root/cc-cron`, last pg_dump 43 KB reported healthy | DOCUMENTED |
| PG restore feasibility | **not tested** | UNKNOWN |
| MariaDB sync with PG | unknown | UNKNOWN |

---

## 2. Decision matrix

| Option | Continue condition | Impact on 2B | Risk |
|--------|-------------------|--------------|------|
| **PG stays** | `CC_DB_DRIVER=pg` + health OK + restore test passes + standby viability assessed | RLS (ADR-001) available | restore feasibility unknown |
| **Rollback MariaDB** | PO explicit approval + rollback test passed | app-level scoping only (no RLS) | schema sync unknown; container not running |

**Finding:** A running MariaDB container is not, by itself, standby readiness. The container is currently absent. 7 days of health checks alone is insufficient proof of DB stability — restore feasibility and (if keeping MariaDB as fallback) sync/schema compatibility must be verified.

## 3. What must be verified before locking the decision (PO-gated)
1. `pg_dump` restore to an isolated PG copy succeeds (backup integrity).
2. If MariaDB is to remain a rollback target: container brought up, schema compared against active app, sync status determined.
3. `.env` locked to `CC_DB_DRIVER=pg` + ADR-001 finalized (if PG stays).

## 4. Rollback commands (documented only — NOT executed)
`sed -i 's/CC_DB_DRIVER=pg/CC_DB_DRIVER=mysql/' /opt/callcenter-web/.env` → `DB_PORT=3306` → `docker compose up -d db` → `docker compose up -d app` → `curl /health`. **Not executed in this audit.**

## 5. Recommendation
Provisionally continue with PG (it is active and healthy), but do not treat the decision as final until restore feasibility is tested and MariaDB standby status is resolved. Do not switch drivers during this audit.
