# P0-4 — بررسی جداسازی Tenant در Socket.IO (`backend/runtime.js`)

**اولویت:** P0  
**فایل هدف:** `backend/runtime.js` (+ event handlers مرتبط)  
**سقف:** فقط Plan.

---

## ۱. stato فعلی (نامشخص)

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Connection auth | نامشخص | UNKNOWN |
| JWT validation on connect | نامشخص | UNKNOWN |
| Session expiration | نامشخص | UNKNOWN |
| Derive tenant | نامشخص | UNKNOWN |
| `io.emit` / broadcast list | نامشخص | UNKNOWN |
| Room creation / join | نامشخص | UNKNOWN |
| Client-triggered events | نامشخص | UNKNOWN |
| Event-level authorization | نامشخص | UNKNOWN |
| Disconnect / revocation | نامشخص | UNKNOWN |

## ۲. مشکل
Broadcast بین tenants ممکن است → داده / رویداد Tenant A برای Tenant B قابل مشاهده باشد.

## ۳. لایه‌های مورد نیاز (طراحی، بدون اجرا)

| لایه | عمل | notes |
|------|-----|-------|
| Connection authentication | `io.use((socket, next) => {...})` | Validate JWT، derive tenant_id از server-side |
| Tenant derivation | `socket.tenant = verifiedTenantId` | هرگز client-supplied tenant را trusting نکن |
| Room naming | `tenant:<tenantId>:<room>` | rooms server-assigned |
| Join authorization | socket join فقط rooms مجاز برای tenant آن | |
| Broadcast scope | `io.to(tenantRoom).emit(...)` به جای `io.emit` | |
| Event authorization | هر event حساس بررسی tenant + role | |
| Disconnect | leave rooms، معتبر session cleanup | |
| Reconnection | re-authenticate، re-derive tenant | |

## ۴. Test cases
| # | Test | Expected |
|---|------|----------|
| P0-4-1 | Same-tenant event delivery | received |
| P0-4-2 | Cross-tenant event isolation | dropped / not delivered |
| P0-4-3 | Invalid credentials | connection refused |
| P0-4-4 | Missing credentials | connection refused |
| P0-4-5 | Unauthorized room join | refused |
| P0-4-6 | Unauthorized event | error |
| P0-4-7 | Expired session | disconnect / re-auth |
| P0-4-8 | Reconnection | re-auth + re-join own rooms only |

## ۵. Side effects
- تغییر در naming rooms ممکن است روی client-side event listeners تأثیر بگذارد.
- نیاز به هماهنگی با frontend (Socket.IO client).

## ۶. Backup / Rollback
- Backup: pre-p0-4 snapshot از runtime.js
- Rollback: بازگردانی نسخه قبلی + recreate app

## ۷. تاثیر Production
اگر Socket.IO در حال استفاده است، تغییر باید در زمان Maintenance window انجام شود (قطع اتصال clienst‌ها).
