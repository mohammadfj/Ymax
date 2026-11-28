# P0-2-RECORDS-AUTHORIZATION-PLAN — Ymax 2026-11-27

Mode: plan only. No write executed. Requires explicit PO approval before any code change.

---

## 1. Current state (OBSERVED live)

`GET /api/records/:uniqueid`, server.js:474:
```js
app.get("/api/records/:uniqueid", auth(["admin","operator"]), async (req, res) => {
  const own = await qTenant(req.user.tenant_id || 1,
    "SELECT id FROM calls WHERE tenant_id=:tid AND unique_id=? LIMIT 1",
    [req.params.uniqueid]).catch(() => []);
  if (!own.length) return res.status(404).json({ error: "recording not found" });
  ...
});
```
- Auth guard `auth(["admin","operator"])` present.
- Tenant scoping via `qTenant` + ownership check on `calls.tenant_id = :tid` — 404 if not owned (fail-closed, no enumeration).
- Latent risk: `req.user.tenant_id || 1` — fail-open to tenant 1 if the claim is missing.

## 2. Problem (from audit)
P0-2 appears **implemented** in code, but needs verification against the live staging/test matrix. The `|| 1` fallback is a latent cross-tenant leak once a second tenant exists.

## 3. Required verification (test matrix — staging only, test data)
| # | Test | Expected |
|---|------|----------|
| P0-2-1 | Token tenant A → uniqueid tenant B | 404 (not 200) |
| P0-2-2 | No token | 401 |
| P0-2-3 | Token tenant A → uniqueid tenant A | 200 + data |
| P0-2-4 | Non-existent uniqueid | 404 |
| P0-2-5 | Missing `req.user.tenant_id` claim | fail-closed (401/404), never tenant-1 leak |

Do not run against real customer data or real admin accounts.

## 4. Proposed hardening (if PO approves)
- Remove `|| 1` fail-open → treat missing tenant_id as unauthenticated (401).
- Audit sibling endpoints: `/api/calls/:id`, playback/download/delete of recordings, `/api/export/cdr.csv` — confirm same tenant scoping.
- Index on `(tenant_id, unique_id)` for performance (already implied by query).

## 5. Backup / rollback
- Backup `backend/server.js` before any write.
- Rollback: restore previous server.js + `docker compose up -d app`.

**Status: PLAN ONLY. Verification first; no code change in this audit.**
