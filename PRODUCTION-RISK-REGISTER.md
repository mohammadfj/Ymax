# PRODUCTION-RISK-REGISTER — Ymax 2026-11-28 (updated — P0 complete)

| # | Risk | Severity | Label | Status | Evidence | Mitigation |
|---|------|----------|-------|--------|----------|------------|
| R1 | Anonymous leak of SIP + TURN credentials | Critical | OBSERVED | **RESOLVED** | P0-1 fixed 2026-11-25 | No leak path; per-tenant minting |
| R2 | TURN REST auth failing (65 "Cannot find credentials") | High | OBSERVED | **RESOLVED** | R2 verified live 2026-11-28 | Removed `lt-cred-mech`; `turnutils_uclient -W` ALLOCATE PASS |
| R3 | RTP + TURN relay share 49160-49200 | High | OBSERVED | **RESOLVED** | Port check 2026-11-28 | Asterisk → 50000-51000; coturn keeps 49160-49200 |
| R4 | TURN LAN-only, no public reachability | High | OBSERVED | DEFERRED | No public IP | PO to provide NAT + `CC_TURN_PUBLIC_IP` |
| R5 | Two turnserver PIDs on :3478 | Medium | OBSERVED | **RESOLVED** | Single instance confirmed 2026-11-28 | PID 22666 only; stray killed |
| R6 | `|| 1` tenant fail-open | Medium | OBSERVED | **RESOLVED** | 2026-11-28: `runtime.js:33` (`tenantOfUid`) + `runtime.js:134` (`live.tenantOf`) no longer fall back to tenant 1 — verified live, app restarted healthy | P0-4 DM check + fail-closed tenant lookup; unauthenticated/unresolvable callers now get no tenant room |
| R7 | `;` comment bug in rendered coturn config | Medium | OBSERVED | **RESOLVED** | Clean config verified 2026-11-28 | Template uses `#` comments |
| R8 | MariaDB standby absent | Medium | OBSERVED | MITIGATION-PLANNED | No `db` container; PG only | MARIADB-BACKUP-PLAN.md: PG → MariaDB mirror nightly; standby read-only safety net, never primary; blocked on Docker Hub auth on host |
| R9 | PG restore feasibility untested | Medium | UNKNOWN | **RESOLVED** | `pg_dump` + template restore 2026-11-28 | Restore proven; nightly backup healthy |
| R10 | CentOS 7.9 EOL | Medium | DOCUMENTED | OPEN | No security patches | Long-term OS migration assessment (out of scope) |
| R11 | `max_contacts=1` limits multi-device operator | Low | DOCUMENTED | OPEN | PBX config | PO to confirm |
| R12 | RTP 40 ports ≈ 20 call ceiling | Low | INFERRED | ACKNOWLEDGED | 50000-51000 range | PO to confirm capacity |
