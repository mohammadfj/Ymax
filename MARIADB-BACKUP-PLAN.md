# MARIADB-BACKUP-PLAN — PG Mirror (backup-only)
# Ymax — 2026-11-28
# Purpose: MariaDB is ONLY a cold backup/mirror of PostgreSQL.
# Primary DB stays PostgreSQL 16 (CC_DB_DRIVER=pg). MariaDB is never the primary.
# When PG has a problem, this mirror holds PG data in a compatible (restorable) form.

---

## 0. Role / scope (binding)
- MariaDB = backup only. Not in the active read/write path. Not a fallback for the app.
- Goal: an always-available, MariaDB-compatible copy of PG data + schema, so
  "PG is down" does not mean "data is lost / no restore source".
- Same PG schema version mirrored on every sync.

## 1. Blocker (PO action required)
- No MariaDB image on server; `docker pull mariadb:10` → **RBAC access denied**
  (Docker Hub auth not configured on the host).
- PO to: `docker login` on the host (Docker Hub creds), or provide an
  air-gapped image / private registry. Until then this plan is DOC-ONLY, not deployed.

## 2. Sync design (pg_dump → MariaDB-compatible SQL)
Nightly, alongside the existing `pg_dump` cron, add a transform + load step:

| Step | Command (on 192.168.250.42) |
|------|------------------------------|
| a. dump PG | `pg_dump -h 127.0.0.1 -U callcenter -d callcenter --clean --if-exists > /root/backups/pg-raw-$(date +%F).sql` |
| b. convert PG→MariaDB dialect | `./tools/pg2mariadb.sh /root/backups/pg-raw-$(date +%F).sql > /root/backups/pg-mariadb-$(date +%F).sql` |
| c. load into standby | `mysql -h 127.0.0.1 -u callcenter -p callcenter < /root/backups/pg-mariadb-$(date +%F).sql` (standby up) |
| d. tag | keep last 7 daily mirror loads; record `pg2mariadb.lastok` timestamp file |

Type mapping (PG → MariaDB):
- `SERIAL/BIGSERIAL` → `INT/BIGINT AUTO_INCREMENT`
- `TIMESTAMPTZ` → `DATETIME` (store +03:30 or UTC consistently)
- `BOOLEAN` → `TINYINT(1)`
- `JSON` → `JSON`
- `ARRAY` / `ENUM(PG)` → `TEXT` / `ENUM` (MariaDB native enum ok)
- `RETURNING` removed (already translated in app layer via lib/db.js)
- `::type` casts removed
- `current_setting('app.tenant_id')` → drop RLS for the mirror (mirror is data-only, not a live multi-tenant DB)

## 3. Standby container
- Add `db-mirror` service to compose (mariadb:10.11) on a bridge network, port bound to 127.0.0.1:3307 (NOT 3306, to avoid clashing with any future live MariaDB).
- `restart: unless-stopped`, data volume `db_mirror_data`.
- Never in app depends_on. App never points at it.

## 4. Restore-from-Mirror SOP (when PG has trouble)
1. Stop writing to PG; app already failing on pg → set `CC_DB_DRIVER=mysql` only as a last resort for read-only (see note below).
2. Bring up `db-mirror`; confirm last mirror load < 24h old (`pg2mariadb.lastok`).
3. Point a maintenance app instance at mirror (127.0.0.1:3307) for READ-ONLY data recovery / reporting.
4. For full PG recovery, prefer the native `pg_dump` (step a) → `psql` restore. The MariaDB mirror is a DATA SAFETY NET, not a primary swap.

> Note: the app can technically run on MariaDB (CC_DB_DRIVER=mysql) via lib/db.js translation,
> but that path is unverified and out of scope for "backup only". Do not promote the mirror to primary without PO sign-off + a full rollback test.

## 5. Gate
- [ ] PO provides Docker Hub auth (host) or air-gapped mariadb image
- [ ] `pg2mariadb.sh` type-mapping script written + tested on a copy of a real dump
- [ ] Standby up + one nightly load verified
- [ ] `pg2mariadb.lastok` < 24h before a PG incident
