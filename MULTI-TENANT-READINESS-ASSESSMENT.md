# MULTI-TENANT-READINESS-ASSESSMENT — Ymax 2026-11-27

Mode: read-only assessment. Multi-tenancy NOT activated.

---

## 1. Tenancy maturity: PARTIALLY TENANT-AWARE (single-tenant in production)

- `tenants` table exists (migration 001); `tenant_id` columns on tenant-scoped tables (migrations 002/003).
- `tenantResolver` middleware exists: JWT `tenant_id` claim → subdomain → `?tenant=` → default `id=1`. **Not active in production.**
- `qTenant(tid, sql, params)` helper exists in `lib/db.js` — appends `tenant_id=:tid` to WHERE.
- Zero tenant-scoped queries in production code paths (confirmed Phase 1B).
- **Single-tenant fallback `id=1` everywhere** — a missing/invalid tenant silently resolves to tenant 1 (fail-open).

## 2. Component readiness

| Component | Tenant-aware? | Notes |
|-----------|---------------|-------|
| Tenant model (DB) | yes | schema ready |
| tenantResolver | exists, inactive | needs activation |
| JWT `tenant_id` claim | assumed | not re-verified live |
| API query scoping | partial | records done; widget still leaks |
| PostgreSQL RLS | designed (ADR-001), not enabled | awaiting DB decision |
| Socket.IO | rooms tenant-scoped | `|| 1` fallbacks latent |
| Asterisk extensions | namespacing designed (ARCHITECTURE.md §5) | not implemented for 2+ tenants |
| Dialplan contexts | designed per-tenant | not implemented |
| Queues | designed `<slug>_support` | not implemented |
| Recordings | per-tenant spool path designed | not implemented |
| Visitor sessions | designed | not implemented per-tenant |
| TURN credential issuance | per-tenant minting in code | auth not reliably passing (TURN review) |
| Provisioning `POST /api/tenants` | design only (ARCHITECTURE.md §4) | not implemented |

## 3. Fail-open risks
- `req.user.tenant_id || 1` — records (P0-2), Socket.IO (~15 sites), widget (`req.tenantId || 1`) — all resolve to tenant 1 when claim missing. Must become fail-closed before any second tenant is introduced.

## 4. Readiness verdict
Not ready to activate multi-tenancy in production. Pre-requisites (per WORKFLOW-NEXT): P0 complete + DB decision + ADR-001 finalized + `|| 1` fallbacks removed + fail-closed verification.
