# P0 Security — Final Report (2026-11-26)

## خلاصه اجرایی

| P0 | وضعیت | کار انجام‌شده |
|----|--------|--------------|
| P0-1 Widget Creds | ✅ Fixed + Bug Removed | باگ JSON.parse در tenants table رفع شد؛ Cache-Control روی credentials به no-store تغییر کرد |
| P0-2 Records IDOR | ✅ Verified | بدون تغییر (کد صحح بود)؛ همه تست‌ها سبز |
| P0-3 Rate Limit | ✅ Spoofing Fixed | trust proxy = loopback؛ loginguard IP source = req.ip |
| P0-4 Socket.IO | ✅ Verified (deferred) | rooms tenant-scoped؛ DM cross-join flaw در single-tenant غیرقابل استفاده → فاز 2B |

## تغییرات اعمال‌شده روی سرور

| # | فایل | خط | تغییر | بکاپ |
|---|------|-----|--------|------|
| ۱ | tenants row id=1 | remote_db_config | JSON legacy double-escaped → `{}` | `/root/backups/tenants-pre-fix-2026-11-26_1403.sql` |
| ۲ | `server.js` | ۲۲۴ | `Cache-Control: public, max-age=120` → `no-store` | `server.js.pre-p0-1-cache-2026-11-26_1405` |
| ۳ | `server.js` | ۱۰۶ | `trust proxy = true` → `"loopback"` | `server.js.pre-p0-3-2026-11-26_1408` |
| ۴ | `lib/loginguard.js` | ۱۸ | IP از XFF دستی → `req.ip` | `loginguard.js.pre-p0-3-2026-11-26_1408` |

## تست‌های انجام‌شده

| Test | Expected | Actual |
|------|----------|--------|
| `GET /api/widget/config` بدون token، Host=192.168.250.42 | ۲۰۰ + credentials | ✅ ۲۰۰ |
| `GET /api/records/:uid` بدون token | ۴۰۱ | ✅ ۴۰۱ |
| `GET /api/records/:uid` token admin، uniqueid خود | ۲۰۰ | ✅ ۲۰۰ |
| `GET /api/records/:uid` token admin، uniqueid foreign | ۴۰۴ | ✅ ۴۰۴ |
| `GET /api/export/cdr.csv` token operator | ۴۰۳ | ✅ ۴۰۳ |
| ۷ login failures → 429 | ۴۲۹ بعد از ۶ام | ✅ ۴۲۹ |

## بکاپ‌ها (در /root/backups/)
- `tenants-pre-fix-2026-11-26_1403.sql` — pg_dump جدول tenants (قبل از UPDATE)
- `server.js.pre-p0-1-cache-2026-11-26_1405` — server.js قبل از Cache-Control fix
- `server.js.pre-p0-3-2026-11-26_1408` — server.js قبل از trust proxy fix
- `loginguard.js.pre-p0-3-2026-11-26_1408` — loginguard.js قبل از IP fix

## وضعیت فعلی سرور
- 4 کانتینر healthy (app, pbx, nginx, turn)
- health: `{"status":"ok","db":"ok"}`
- لاگ app: بدون error
- rate-limit lockout تستی: bucket `user:test-p0-3-2026` تا ~15:38 فعال است (خودکار expire می‌شود)

## Rollback (اگر لازم شد)
```bash
cp /root/backups/server.js.pre-p0-3-2026-11-26_1408 /opt/callcenter-web/backend/server.js
cp /root/backups/loginguard.js.pre-p0-3-2026-11-26_1408 /opt/callcenter-web/backend/lib/loginguard.js
cd /opt/callcenter-web && docker compose build app && docker compose up -d app
# برای داده:
# pg_restore /root/backups/tenants-pre-fix-2026-11-26_1403.sql
```

## موارد موکول به فاز 2B
1. P0-4: DM cross-tenant join check (وقتی tenant 2+ وجود دارد)
2. TURN credential rotation (creds فعلی قبلاً در cache عمومی بودند — max-age=120 → در صورت نگرانی، rotation)
3. `trust proxy = loopback` — اگر Nginx روی شبکه‌ای غیر-loopback قرار گرفت، نیاز به به‌روزرسانی
