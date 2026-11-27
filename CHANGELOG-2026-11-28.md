## [3.0.2] - 2026-11-28

### Security (P0)
- **P0-1**: Widget `/api/widget/config` no longer leaks TURN or visitor SIP credentials; per-tenant TURN credentials minted via `HMAC-SHA1(TURN_SECRET, "<ts>:t<tid>")`; visitor SIP password tenant-scoped, no global fallback.
- **P0-2**: `/api/records/:uniqueid` IDOR fixed — uses `qTenant` scoping and `path.basename` guard.
- **P0-3**: Login rate-limit active — per-IP 20/5min, per-user 6/10min, 429 lockout via `lib/loginguard.js`.
- **P0-4**: Socket.IO fail-open `|| 1` hardened — chat history DM access validates peer exists in caller's tenant; JWT auth middleware fail-closed; tenant rooms per-tenant namespaced.

### TURN Reliability
- **R2**: TURN REST auth contradiction resolved — removed `lt-cred-mech` (mutually exclusive with `use-auth-secret` in coturn 4.6.1); verified `turnutils_uclient -W <secret>` ALLOCATE succeeds with relay ports in 49160-49200 range.
- **R3**: Separated Asterisk RTP (50000-51000) from coturn relay (49160-49200) to eliminate port conflict; `rtp.conf` updated in both template and live volume; asterisk restarted.
- **R5**: Confirmed single authoritative coturn instance (PID 22666) on 192.168.250.42:3478 UDP/TCP.

### Database
- **WS-3**: PostgreSQL restore feasibility verified — `pg_dump` produces valid 50KB backup; schema-validated template restore works. No MariaDB standby present; PG retained as primary.

### Operations
- All changes backed up to `/root/backups/pre-audit-2026-11-27_2329-*` before any write.
- Zero active calls confirmed before each restart.
- Firewall rules verified: 3478/tcp+udp, 49160-49200/udp from 192.168.250.0/24.
