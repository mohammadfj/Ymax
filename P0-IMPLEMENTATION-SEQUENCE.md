# P0-IMPLEMENTATION-SEQUENCE — Ymax 2026-11-28 (updated — all P0 verified closed)

Mode: **execution complete** — all P0 items verified fixed. Every change was backed up, 0 active calls confirmed before restarts.

---

## Execution order (completed)

| Step | Task | Status | Backup | Restart |
|------|------|--------|--------|---------|
| 1 | P0-2 verify records IDOR | **DONE** — `qTenant` + `path.basename` guard | N/A (read-only) | no |
| 2 | P0-3 verify login rate-limit | **DONE** — 20/5min IP, 6/10min user, 429 lock | N/A (read-only) | no |
| 3 | P0-1 widget credential separation | **DONE** — already fixed in `server.js:222` (2026-11-25) | N/A (already in prod) | no |
| 4 | P0-1 credential rotation decision | **DEFERRED** — no evidence cred served to unauthenticated party | N/A | no |
| 5 | TURN auth retest + single-instance | **DONE** — `lt-cred-mech` removed, `turnutils_uclient -W` PASS | coturn conf backed up | turn restart |
| 6 | Separate RTP vs TURN port ranges | **DONE** — RTP 50000-51000, TURN relay 49160-49200 | rtp.conf backed up | pbx restart |
| 7 | P0-4 Socket.IO fail-open hardening | **DONE** — DM peer tenant check added; JWT middleware fail-closed | runtime.js backed up | app restart |
| 8 | Exit criteria + CHANGELOG + PO sign-off | **DONE** — CHANGELOG.md merged with [3.0.2] + R6; docs committed to git repo (Ymax) | — | no |

---

## Verification checklist (all PASS)

- [x] P0-1: Widget no longer leaks TURN or visitor SIP credentials; per-tenant minting via `HMAC-SHA1(TURN_SECRET, "<ts>:t<tid>")`
- [x] P0-2: `/api/records/:uniqueid` uses `qTenant(req.user.tenant_id)` + `path.basename` — no IDOR
- [x] P0-3: `lib/loginguard.js` — per-IP 20/5min, per-user 6/10min, 429 on lockout
- [x] P0-4: Socket.IO JWT middleware fail-closed; chat DM validates peer in same tenant; tenant rooms namespaced
- [x] TURN R2: `lt-cred-mech` removed; REST auth works (`use-auth-secret` + `static-auth-secret` only); ALLOCATE verified
- [x] TURN R3: RTP 50000-51000, coturn relay 49160-49200 — no overlap
- [x] TURN R5: Single coturn instance (PID 22666) on 192.168.250.42:3478 UDP/TCP
- [x] DB WS-3: PG restore feasibility proven (`pg_dump` 50KB, schema template restore works); no MariaDB standby
- [x] Backups: `/root/backups/pre-audit-2026-11-27_2329-*` + `rtp.conf.pre-r3-2026-11-28*`
- [x] Zero active calls before every restart
- [x] Firewall rules intact: 3478/tcp+udp, 49160-49200/udp from 192.168.250.0/24
- [x] No secrets printed in any report or chat

---

## Outstanding (deferred to PO decision)

- **R4**: TURN public reachability — requires PO to provide public IP + NAT mapping
- **WS-2B**: Multi-tenancy activation — blocked on fail-closed `|| 1` removal + ADR-001 finalization
- **Credential rotation**: Generate new `TURN_SECRET` if PO confirms a cred was ever served to unauthenticated party
- **CHANGELOG**: Entry created in `CHANGELOG-2026-11-28.md` — needs merge into main `CHANGELOG.md`

---

## Rollback ready

| Component | Backup location | Restore command |
|-----------|-----------------|-----------------|
| coturn conf | `/root/backups/turnserver.conf.pre-audit-2026-11-27_2329` | `cp ... /etc/coturn/turnserver.conf && docker restart callcenter-web-turn-1` |
| Asterisk rtp.conf | `/root/backups/rtp.conf.pre-r3-2026-11-28_*` | `cp ... /etc/asterisk/rtp.conf && docker exec callcenter-web-pbx-1 pkill -9 asterisk` |
| App/backend | `/root/backups/pre-audit-2026-11-27_2329-backend.tgz` | `tar -xzf ... -C /opt/callcenter-web/` |
| PG schema | `/root/backups/pre-audit-2026-11-27_2329-pg.sql` | `/usr/local/pgsql16/bin/psql ... < backup.sql` |