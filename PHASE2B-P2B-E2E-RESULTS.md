# Phase 2B — Cross-Tenant Isolation + Provisioning E2E + PJSIP (t3) Results

**Date:** 2026-11-26  
**Status:** Executed — live on PBX  
**Tenant under test:** id=3 `e2e-tenant3` (E2E only, non-super)

---

## 1. Cross-tenant isolation (Step 4.1)
| Test | Expected | Result |
|------|----------|--------|
| Tenant 1 record query with tenant-2 JWT | 404 fail-closed | ✅ |
| Tenant 2 sees only own call | T1 data = 0 rows | ✅ |
| Non-super `POST /api/tenants` | 403 | ✅ `{"error":"only super-admin tenants can provision sub-tenants"}` |

## 2. AMI tenant scoping (Step 4.2)
- `addEvent(callId,event,payload,tid)` → `qTenant(tid,"INSERT INTO call_events (tenant_id,...) VALUES (:tid,...)")`
- `onMissedCall` callbacks INSERT → `qTenant(call.tenant_id||1, "INSERT INTO callbacks (tenant_id,...)")`
- runtime.js rebuilt + app recreated; health OK, AMI connected, no errors.

## 3. Provisioning E2E (Step 4.3)
- `POST /api/tenants` (super-admin JWT, tenant 1) → id=3, trial +30d, `GET /api/tenants` lists all, DB row correct.
- **Finding:** `tenants_id_seq` was out-of-sync (is_called state) → first POST hit a duplicate-key; `setval(...,max(id),true)` fixed. Sequence now tracks max id.

## 4. PJSIP execution on live PBX (Step 5)
### Mechanism (critical — was the root of earlier confusion)
- `render-config.py` (called on every pbx start) **regenerates** `pjsip.conf` / `extensions.conf` / `queues.conf` from the **DB `tenants` table** via `fetch_tenants()`.
- **BUT** the pbx container has **no docker CLI / no /var/run/docker.sock** → `fetch_tenants()` returns `[]` → `append_tenant_sections` skips with "no active tenants, skipping".
- So the **live persistence path is the entrypoint spool-append loop**: `/var/spool/asterisk/*_tenant.conf` → copied into `/etc/asterisk/<base>.conf` on every start (after stripping `; ---- Tenant` marker lines).
- **Conclusion:** to add a tenant's PJSIP objects, write them to the **spool source files**, not `/etc/asterisk` directly.

### Tenant 3 objects (all live)
| Object | Source | Status |
|--------|--------|--------|
| PJSIP `3000-t3`..`3005-t3` + `visitor-3` | spool `pjsip_tenant.conf` | ✅ loaded |
| queue `support-3` | spool `queues_tenant.conf` | ✅ live (rrmemory, maxlen 15) |
| `from-internal-3` `Dial(PJSIP/${EXTEN}-t3,30)` | spool `extensions_tenant.conf` | ✅ |
| `from-widget-3` → `Queue(support-3...)`, `Tid:3` | spool `extensions_tenant.conf` | ✅ |
| users `e2e-t3-admin`(3000), `e2e-t3-op`(3001) | DB `users` tenant 3 | ✅ login 200 |
| tenant 3 DB row: ext 3000–3005, `support-3` | DB `tenants` | ✅ |

### Pass convention
- `visitor_sip_pass` = `T3Vis2026!m3n8p`; per-op `T3Op<ext>!k9f7q2`
- (tenant 1 legacy uses sha256-hex passwords; t3 uses plaintext in spool — **inconsistent with t1**, see §6)

## 5. App-side
- health OK, AMI reconnected after pbx recreate, no runtime errors.
- Dundi `0.0.0.0:4520` bind error + ALSA noise = pre-existing host-env noise, harmless (no dial-out configured).

## 6. Open items / risks
1. **Pass-hash inconsistency:** t1 PJSIP passwords are `sha256hex` (render template `__OP_PASS_N__`); t3 spool block uses plaintext `T3Op*`/`T3Vis*`. For a real tenant, use the same hashing as t1. t3 is test-only.
2. **`render-config.py` DB-fetch is dead** in the current container (no docker CLI). PJSIP persistence relies **solely** on the spool-append loop. If docker CLI is ever mounted into pbx, the fetch path would double-generate and collide with the spool files. Decide one source of truth.
3. **Tenant 2 `isolation-test` still active** with ext 1000–1005 (overlaps tenant 1). Should be deactivated/cleaned.
4. **Test data to clean up:** tenant 3 + its users (12,13) + spool t3 blocks + `support-3`.

## 7. Rollback (tenant 3)
```bash
# spool (recreate pbx to drop t3)
docker exec callcenter-web-pbx-1 sh -c "
  grep -v -E '^; ---- Tenant 3|^-t3|t3-|visitor-3' /var/spool/asterisk/pjsip_tenant.conf > /tmp/p; mv /tmp/p /var/spool/asterisk/pjsip_tenant.conf
  ... "
# DB
DELETE FROM users WHERE id IN (12,13);
DELETE FROM tenants WHERE id=3;
# pbx recreate
cd /opt/callcenter-web && docker compose up -d --force-recreate pbx
```
