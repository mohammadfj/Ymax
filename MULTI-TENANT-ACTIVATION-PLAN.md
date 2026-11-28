# ADR-001 ACTIVATION PLAN — Multi-Tenant Finalization
# Ymax — 2026-11-28
# Status: ADR-001 APPROVED; this is the execution plan.

Activation of multi-tenant production. Ordered steps; each is gated.
R6 fail-open already removed (runtime.js:33/:134). No secrets in this doc.

---

## 0. Preconditions (verify before starting)
| Item | How to verify | Must be |
|------|---------------|---------|
| Active calls | `docker exec callcenter-web-pbx-1 asterisk -rx "core show channels"` | 0 |
| App health | `curl -s http://127.0.0.1:3006/health` | db:ok |
| Backup | `tar -czf /root/backups/pre-mten-$(date +%F).tgz /opt/callcenter-web/backend/` | done |
| PG backup | `pg_dump -h 127.0.0.1 -U callcenter callcenter > /root/backups/pre-mten-pg-$(date +%F).sql` | done |
| ADR-001 | Approval checkboxes all checked | ✅ |

---

## 1. DDL — RLS policies (PG, backup first)
Apply on 127.0.0.1:5432, db `callcenter`, user `callcenter`.
- [ ] Enable RLS on tenant-scoped tables: `calls`, `visitors`, `chat_messages`, `surveys`, `call_events`, `op_events`, `callbacks`, `user_groups`.
- [ ] `CREATE POLICY tenant_isolation ON <t> USING (tenant_id = current_setting('app.tenant_id')::int);` for each.
- [ ] Ensure `tenant_id` indexed on each.
- [ ] `callcenter` DB role: `ALTER ROLE callcenter NOBYPASSRLS;` (so RLS applies to the app's own queries).
- [ ] Leave `postgres`/superuser BYPASS for migrations/admin.
- [ ] Verify: a `SELECT ... WHERE tenant_id=<other>` as `callcenter` with `app.tenant_id` set returns only the set tenant's rows.

---

## 2. Connection pool — SET LOCAL
In `lib/db.js`:
- [ ] On query execute (or transaction start), run `SELECT set_config('app.tenant_id', ?, true)` with the resolved tenant id (transaction-local).
- [ ] Admin/migration paths: superuser role or `set_config('app.tenant_id','')` + bypass.
- [ ] No fallback to tenant 1 (R6 must stay removed).
- [ ] Test: two concurrent tenants → each sees only its rows.

---

## 3. tenantResolver activation
In `middleware/tenantResolver.js` (server.js mount):
- [ ] Resolve order: JWT `tenant_id` claim → subdomain → `?tenant=` → (NO default fallback).
- [ ] Unresolvable tenant → 403 (fail-closed). No `|| 1`.
- [ ] Enable in production; keep feature-flag off by default until step 5 passes.
- [ ] Legacy code paths: audit for implicit tenant-1 assumptions; enumerate and convert to resolved-tenant.

---

## 4. Asterisk namespacing
- [ ] SIP endpoint pattern `<slug>_<ext>`; isolate contexts per tenant.
- [ ] `PJSIP/<slug>_<ext>` registrations; context isolation so cross-tenant internal call is blocked.
- [ ] Rebuild pbx image; verify with 0 active calls + pbx restart.

---

## 5. Provisioning API
- [ ] `POST /api/tenants` → creates tenant row + auto-provisions SIP endpoints + DB seed, <30s.
- [ ] Auth: admin only; audit log entry (op_events).
- [ ] Idempotency by slug.

---

## 6. Test Matrix (all must pass)
| Test | Pass criteria |
|------|---------------|
| Cross-tenant data isolation | Tenant A rows never returned for Tenant B query |
| RLS enforcement | App role cannot read another tenant's rows |
| Tenant provisioning E2E | `POST /api/tenants` → SIP + DB rows <30s |
| Internal call across tenants | Blocked / segregated |
| Unresolvable tenant | 403, no tenant-1 fallback |
| Health per tenant | `/health?tenant=x` correct per-tenant status |

---

## 7. Rollback
| Component | Backup | Restore |
|-----------|--------|---------|
| RLS | pg_dump pre-step-1 | `DROP POLICY tenant_isolation ON <t>;` + re-apply old schema |
| pool SET | backend tgz pre-step-1 | `tar -xzf ... -C /opt/callcenter-web/` |
| resolver flag | — | feature-flag off (revert to single-tenant) |
| PBX namespace | pbx image tag | `docker tag <rollback> callcenter-web-pbx:local && compose up -d pbx` |

---

## 8. Gate summary
Step 6 green + rollback tested → multi-tenant ACTIVATED (phase 2B complete).
Otherwise → revert per step 7; do not proceed to phase 3.
