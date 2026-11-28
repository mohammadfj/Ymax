# بررسی پایداری دیتابیس: PostgreSQL vs MariaDB

**اولویت:** P0 مسدودکننده برای مسیر ۳ (تصمیم پایداری DB)  
**سقف:** فقط بررسی و Plan — تغییر دیتابیس / سوئیچ درайور اجرا نمی‌شود.

---

## ۱. PostgreSQL (فعال)

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Connection path | 127.0.0.1:5432 | DOCUMENTED |
| Driver | `pg` (`CC_DB_DRIVER=pg`) | DOCUMENTED |
| systemd unit | `/etc/systemd/system/postgresql-16.service` enabled+started | DOCUMENTED |
| Migration history | ۰۰۱/۰۰۲/۰۰۳ tenants + tenant_id + seed | DOCUMENTED |
| Tables | tenants + tenant_id columns | DOCUMENTED |
| Query compatibility | translateSQL MariaDB→PG در `lib/db.js` | DOCUMENTED |
| Transaction handling | نامشخص | UNKNOWN |
| Insert ID handling | `RETURNING id` (PG-specific) | DOCUMENTED |
| Affected-row handling | shim `insertId`/`affectedRows` | DOCUMENTED |
| Backup frequency | crontab `/root/cc-cron` شبانه `pg_dump` | DOCUMENTED |
| Backup integrity | تست pg_dump ۴۳KB سالم | DOCUMENTED |
| Restore feasibility | نامشخص (تست نشده) | UNKNOWN |

## ۲. MariaDB (standby)

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| وجود کانتینر | `callcenter-web-db-1` Up healthy | DOCUMENTED |
| droughts / sync with PG | نامشخص — **وجود کانتینر ≠ standby هم‌زمان** | UNKNOWN |
| Latest app records | نامشخص | UNKNOWN |
| Rollback target viable | نامشخص — schema compatibility نامشخص | UNKNOWN |
| Port | 3306 | DOCUMENTED |

> ⚠️ MariaDB rollback بدون بررسی sync / schema بسیار خطرناک است. rollback در WORKFLOW-NEXT 금지 شده است تا تایید صریح PO.

## ۳. Update-then-Insert races (بدون ON DUPLICATE KEY)

| Endpoint | فایل | خط | Risk |
|----------|-------|-----|------|
| `/api/visitor/announce` | server.js | ~240 | Race: UPDATE 0 rows → INSERT ممکن است duplicate ایجاد کند در concurrent calls |
| Survey upsert | runtime.js | ~221 | همان 위험 |

## ۴. تصمیم نهایی (Output)
بعد از ۷ روز health PG + تایید PO:
- `.env` lock به `pg`
- ADR-۰۰۱ finalize (RLS برای فاز ۲B)

## ۵. Rollback (ممنوع بدون PO)
دستورهای rollback در WORKFLOW-NEXT मौजूद است اما **اجرا نمی‌شود** در این سشن.

## ۶. Test / Backup
- بررسی Integrity backup PG
- تست restore در محیط isolated (نه Production)
