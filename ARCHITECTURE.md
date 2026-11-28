# ARCHITECTURE.md — Ymax Multi-Tenant (فاز 2B)

**Date:** 2026-11-26  
**Status:** Design — برای تایید PO  
**پیش‌نیاز:** ADR-001 (PostgreSQL + qTenant + RLS)

---

## 1. مدل Tenant

| مفهوم | تعریف |
|-------|-------|
| Tenant | یک مشتری SaaS؛ `tenants.id`؛ slug، domain اختیاری، tier، max_users/extensions |
| Extension | یک داخلی SIP؛ `sip_ext` در بازه `sip_ext_start..sip_ext_end` تیننت |
| User | اپراتور/ادمین؛ `users.tenant_id` + `sip_ext` |
| Visitor | بدون احراز هویت؛ scope فقط `tenant_id` (از host/subdomain) |
| Call | `calls.tenant_id`؛ recordings per-tenant |

## 2. Identity Resolution (server-side only)

```
JWT (authenticated)  → req.user.tenant_id          [trusted]
tenantResolver        → req.tenantId               [public endpoints: host/subdomain/?tenant]
fallback              → tenant 1                   [legacy single-tenant]
```
- هیچ route `tenant` را از body/query client برای authenticated endpoints نمی‌خواند.
- `qTenant(tid, sql, params)` — tid فقط از JWT/resolver، نه از request body.

## 3. API Contracts

| Endpoint | Tenant scope | Auth |
|----------|-------------|------|
| `GET /api/widget/config` | `req.tenantId` (resolver) | none (فقط public fields بعد از P0-1) |
| `POST /api/visitor/*` | `req.tenantId` | none |
| `GET/POST /api/auth/login` | `req.user.tenant_id` | result of login |
| `/api/calls`, `/api/records`, `/api/customers`, `/api/export/*` | `req.user.tenant_id` | admin/operator |
| `POST /api/tenants` (جدید) | — | **super-admin فقط** (`is_super`) |
| `GET /api/tenants/:id/health` (جدید) | per-tenant | admin تیننت |

## 4. Provisioning API (`POST /api/tenants`) — design فقط

```
POST /api/tenants  (super-admin)
{ slug, name, domain?, max_users, max_extensions, turn?{...} }
→ 1. INSERT tenants row (tier, trial, ext range از registry)
→ 2. INSERT users seed (admin تیننت با ext=start)
→ 3. Asterisk: PJSIP endpoint/aor/`<slug>_<ext>` + queue `<slug>_support`
→ 4. TURN: (اختیاری) credential تیننتی
→ 5. response: { id, admin_ext, sip_range }
DELETE /api/tenants/:id → deprovision (soft: active=false، cleanup SIP)
```
**اثرات:** هر مرحله transactional؛ rollback = حذف rows + حذف PJSIP objects.

## 5. Asterisk Namespacing

| شیء | الگو | مثال |
|-----|------|------|
| Endpoint/AOR | `<slug>_<ext>` | `acme_1001` |
| Context | `from-internal-<slug>` | `from-internal-acme` |
| Queue | `<slug>_support` | `acme_support` |
| Recordings | spool path per-tenant | `/var/lib/callcenter/recordings/t<tid>/` |
| Ext ranges | `sip_ext_start..end` در tenants row | 1000-1005 default، tenant2: 2000-2005 |

**عزل:** dialplan `<slug>` context فقط به ext range خود تیننت + 200 (dial-out) route. تماس بین tenants مسدود (به‌جز super route ادمین مرکزی).

## 6. Socket.IO (P0-4 موجود)
- rooms: `admin:t<tid>`, `operators:t<tid>`, `op:t<tid>:<ext>`, chat `t<tid>:<chan>`
- تکمیل فاز 2B: check هم-تیننت بودن DM peers (موکول از P0 review)

## 7. Migration Plan (DDL)

| Migration | محتوا | ریسک |
|-----------|-------|------|
| 004 | indexes: `tenant_id` روی همه جدول‌های tenant-scoped (اگر نباشد) | کم |
| 005 | RLS: `ENABLE ROW LEVEL SECURITY` + policy `tenant_isolation` (ADR-001) | متوسط — تست قبل از فعال‌سازی |
| 006 | `tenants.is_super` + super-admin user row | کم |

هر migration با bکاپ + dry-run روی کپی DB.

## 8. Test Matrix

| Test | Pass criteria |
|------|---------------|
| Isolation | queries tenant A با JWT tenant B → 0 rows (app-level و RLS) |
| Provisioning E2E | POST /api/tenants → rows + PJSIP objects + queue < 30s |
| Cross-tenant call | agent acme → ext globex → مسدود |
| RLS fail-closed | بدون `app.tenant_id` set → 0 rows (نه error) |
| Super-admin | فقط is_super روی POST /api/tenants |

## 9. Risks / Open questions (PO)
1. ext range allocation: static از tenants row یا auto-allocator؟ (پیشنهاد: static در V1)
2. آیا tenants باید domain/subdomain اختصاصی داشته باشند؟ (resolver آماده است)
3. cap هم‌زمانی: max_users/max_extensions enforced کجا؟ (provisioning + login)
4. RLS: فعال‌سازی هم‌زمان با app-level یا phase دوم؟ (پیشنهاد: phase دوم، بعد از تثبیت qTenant)
