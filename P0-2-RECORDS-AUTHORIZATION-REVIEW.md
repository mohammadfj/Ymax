# P0-2 — بررسی مجوز دسترسی `GET /api/records/:uniqueid` (IDOR)

**اولویت:** P0  
**فایل هدف:** `backend/server.js`  
**سقف:** فقط Plan — اجرا تأیید نشده است.

---

## ۱. جریه فعلی (داکومنته نشده)

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Endpoint | `GET /api/records/:uniqueid` | DOCUMENTED |
| Authorization | نامشخص (احتمالاً ندارد) | UNKNOWN |
| `req.user` filling | نامشخص | UNKNOWN |
| Derive tenant | نامشخص | UNKNOWN |
| Ownership check | نامشخص | UNKNOWN |
| 403/404 برای跨-tenant | نامشخص | UNKNOWN |

## ۲. مشکل
اندپوینت records بدون tenant check و بدون بررسی مالکیت → Tenant A می‌تواند rekord Tenant B را با uniqueid بخواند (IDOR).

## ۳. جریان مجوز مورد نیاز (طراحی)

1. **احراز هویت:** JWT از header / cookie → `req.user` (user_id, tenant_id, role).
2. **Derive tenant:** از `req.user.tenant_id` (server-side)، **نه** از query param / client-supplied header.
3. **یافت رکورد:** `uniqueid` → رکورد در DB.
4. **اجازہ:** مالکیت رکورد (tenant_id يتطابق با tenant کاربر) +-role مجاز (admin/operator/owner).
5. **Fail closed:** اگر tenant نامعتبر است یا رکورد نداشته باشد → ۴۰۴ (نه ۴۰۳ برای جلوگیری از enumeration).
6. **تکرار:** سایر endpoint‌های record (playback, download, delete) هم باید audit شوند.

## ۴. Side effects
- تغییر در منطق redirect/404 ممکن است رویcreens موجود تأثیر بگذارد.
- نیاز به index روی `uniqueid` + `tenant_id` برای عملکرد.

## ۵. Dependencies
- حضور `req.user` populate شده (middleware auth)
- qTenant helper (داکومنت شده اما فعال در تولید نیست)

## ۶. Test cases
| # | Test | Expected |
|---|------|----------|
| P0-2-1 | Token tenant A request uniqueid tenant B | ۴۰۴ / ۴۰۳ |
| P0-2-2 | بدون token | ۴۰۱ |
| P0-2-3 | Token tenant A request uniqueid tenant A | ۲۰۰ + داده |
| P0-2-4 | uniqueid ناموجود | ۴۰۴ |

## ۷. Backup / Rollback
- Backup: pre-p0-2 snapshot از server.js
- Rollback: بازگردانی نسخه قبلی

## ۸. تاثیر Production
اگر auth middleware، تغییر risk پایین است. اگر auth middleware فاقد است، نیاز به اضافه کردن قبل از Authorization check.
