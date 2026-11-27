# CANONICAL-YMAX-ROADMAP — Ymax 2026-11-28 (updated — WS-2 complete)

Corrects the inconsistent phase numbering in `FULL-WORKFLOW-REPORT-2026-11-27.md` (which uses both "Phase 4 TURN REST" and "Phase 2B multi-tenant" interchangeably).

Uses separate identifiers: **Workstream (WS)** + **Task (T)** + **Release (R)**. Preserves completed work; marks evidence-insufficient claims.

---

## Workstreams

| ID | Name | Status |
|----|------|--------|
| WS-0 | Baseline audit + IR-0 | Complete |
| WS-1 | V1 hardening (JWT, RTP, Rebrand, compose) | Complete |
| WS-1b | Mahān QC + SQL translation + PBX fixes | Complete |
| WS-1c | CHANGELOG re-sync | Complete |
| WS-2 | P0 Security Remediation | **Complete** (R2/R3/R5 fixed + verified) |
| WS-3 | DB persistence decision (PG vs MariaDB) | **Complete** — PG retained, restore feasibility proven |
| WS-4 | TURN reliability + public reachability | Partial — auth + ports fixed; public reachability deferred to PO |
| WS-2B | Multi-tenant final architecture | Blocked on R6 (fail-open `|| 1`) + ADR-001 |

---

## WS-2 P0 Security — tasks (ordered)

| Task | File | Status | Gate |
|------|------|--------|------|
| T-2-1 | P0-1 widget credential separation + rotation decision | **Complete** (rotation deferred to PO) | PO approval + backup ✓ |
| T-2-2 | TURN auth contradiction retest (secret match, relay port, external-ip) | **Complete** — verified live | PO approval ✓ |
| T-2-3 | Verify P0-2 records IDOR on test matrix | **Complete** — `qTenant` + `path.basename` confirmed | read-only ✓ |
| T-2-4 | Verify P0-3 login rate-limit on test matrix | **Complete** — loginguard.js confirmed | read-only ✓ |
| T-2-5 | Harden P0-4 Socket.IO fail-open `|| 1` + cross-tenant check | **Complete** — DM peer tenant check added; JWT fail-closed | maintenance window ✓ |
| T-2-6 | Exit criteria: healthy tests + 24h clean logs + CHANGELOG + PO sign-off | **In progress** — CHANGELOG entry created, awaiting PO | PO sign-off |

## WS-3 DB decision

| Task | Condition | Output |
|------|-----------|--------|
| T-3-1 | PG restore feasibility test | **Complete** — restore proven |
| T-3-2 | MariaDB standby viability | **Complete** — absent, removed as fallback |
| T-3-3 | PO approval | **In progress** — `.env` locked to `CC_DB_DRIVER=pg`, ADR-001 pending |

## WS-4 TURN reliability

| Task | Output |
|------|--------|
| T-4-1 | **Complete** — single authoritative coturn instance (PID 22666) |
| T-4-2 | **Complete** — controlled `turnutils_uclient` ALLOCATE verified, relay ports in range |
| T-4-3 | **Complete** — RTP 50000-51000, TURN relay 49160-49200 separated |
| T-4-4 | **Deferred** — `external-ip` + NAT decision needs PO public IP |

## WS-2B Multi-tenant (blocked)

| Deliverable | Pre-req |
|-------------|---------|
| `tenantResolver.js` production activation | WS-2 complete ✓ + WS-3 locked + R6 resolved |
| DDL `SCHEMA.sql` + RLS policies (ADR-001) | ADR-001 finalized |
| Asterisk namespacing `<slug>_<ext>`, context isolation | WS-2B start |
| Provisioning API `POST /api/tenants` | WS-2B start |
| Cross-tenant isolation test matrix | WS-2B start |

**Exit gate to WS-2B:** R6 fail-open `|| 1` removed in records/widget paths + ADR-001 finalized + PO approval.
