# ADR-001 — Multi-Tenant DB Strategy + PostgreSQL RLS

**Date:** 2026-11-26  
**Status:** Proposed — awaiting PO approval  
**Owner:** Agnes (Claude)

---

## 1. Context

- Ymax single-tenant در حال حاضر است؛ `tenants` table + `tenant_id` columns موجود (migrations 001-003)
- `qTenant(tid, sql, params)` helper در `lib/db.js` — `tenant_id=:tid` به WHERE اضافه می‌کند
- `tenantResolver` middleware موجود ولی در production **فعال نیست** (single-tenant fallback)
- PostgreSQL 16 فعال — `RLS` (Row-Level Security) برای tenant isolation قابل استفاده

## 2. Decision

| مورد | تصمیم | دلیل |
|------|-------|------|
| DB | PostgreSQL 16 | PO decision 2026-11-23 |
| Tenant isolation | **Application-level (qTenant) + RLS fallback** | RLS برای defense-in-depth؛ app-level برای performance |
| RLS policy | `tenant_id = current_setting('app.tenant_id')::int` | هر connection، tenant_id را set می‌کند |
| Connection pool | `pgBouncer` یا app-level `SET LOCAL app.tenant_id = ?` | بدون pool → `SET` در هر query |
| Rollback | RLS policies DROP + `app.tenant_id` unset | |

## 3. RLS Design

### 3.1 Enable RLS on all tenant-scoped tables
```sql
ALTER TABLE calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE visitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE surveys ENABLE ROW LEVEL SECURITY;
ALTER TABLE call_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE op_events ENABLE ROW LEVEL SECURITY;
-- etc
```

### 3.2 RLS Policy
```sql
CREATE POLICY tenant_isolation ON calls
  USING (tenant_id = current_setting('app.tenant_id')::int);
```

### 3.3 Connection-level tenant setting
در `lib/db.js`، قبل از هر `qTenant` call:
```js
await q("SELECT set_config('app.tenant_id', $1, true)", [String(tenantId)]);
```
یا در connection pool، `SET LOCAL` قبل از هر transaction.

### 3.4 Super-user bypass
`postgres` superuser RLS را bypass می‌کند — برای migrations و admin queries.

## 4. Impact

| مورد | Impact |
|------|--------|
| Performance | RLS overhead negligible (index on `tenant_id`) |
| Security | Defense-in-depth: app-level + RLS |
| Migration | Existing data unaffected (tenant_id=1) |
| Rollback | RLS policies DROP → app-level only |

## 5. Alternatives

| Option | Pros | Cons |
|--------|------|------|
| **App-level only (current)** | Simple, no RLS overhead | No DB-level enforcement |
| **RLS only** | DB-level enforcement | Performance, complexity |
| **Schema-per-tenant** | Strong isolation | Migration complexity, cross-tenant queries hard |

## 6. Approval

- [ ] PO approval for RLS design
- [ ] PO approval for `SET LOCAL app.tenant_id` in connection pool
- [ ] Test plan for cross-tenant isolation
