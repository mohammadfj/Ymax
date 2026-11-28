# PRODUCTION-CHANGE-PLAN — Ymax P0 Security Remediation

**تاریخ:** 2026-11-24  
**وضعیت:** Plan نهایی — اجرا تایید نشده است. نیاز به تایید صریح PO قبل از هر Write.

---

## ۱. prerequisite (قبل از هر P0)

| # | podmینه | وضعیت |
|---|--------|-------|
| ۱ | بکاپ کامل `/opt/callcenter-web/backend/` + PG dump | بایستی انجام شود |
| ۲ | تایید صریح PO برای هر فایل목표 | بایستی انجام شود |
| ۳ | 휴 Maintenance window (بدون تماس فعال) | بایستی تایید شود |
| ۴ | تست در Staging / isolated env | بایستی قبل از Production انجام شود |

## ۲. ترتیب اجرا (preferential)

| مرحله | کار | فایل | T |
|------|-----|-------|----|
| ۱ | P0-3 rate-limit middleware (سند P0-3) | server.js + loginguard.js | баست بر PO |
| ۲ | P0-2 records authorization (سند P0-2) | server.js | پس از P0-3 |
| ۳ | P0-1 widget credential split (سند P0-1) | server.js + widget.js | پس از P0-2 |
| ۴ | P0-4 Socket.IO isolation (سند P0-4) | runtime.js | آخرین (قطع clienst‌ها) |

> ترتیب قابل تغییر است بر اساسlook PO. P0-3 اولین است چون incident surface کمترین را دارد.

## ۳. بکاپ قبل از هر Write (اجباری)

```bash
ssh root@192.168.250.42 "
  ts=$(date +%F_%H%M)
  tar -czf /root/backups/pre-p0-$ts.tgz /opt/callcenter-web/backend/
  pg_dump -h 127.0.0.1 -U callcenter callcenter > /root/backups/pre-p0-pg-$ts.sql
"
```

## ۴. RollbackProcedure (هر P0)

```bash
# App rollback (قبل از restart app)
docker tag callcenter-web-app:rollback-pre-p0-<X> callcenter-web-app:local
docker compose up -d app
```

فایل rollback قبل از هر تغییر save شود.

## ۵. restart constraint (AGENT-PROTOCOL #۳)
Restart فقط وقتی تماس فعال نیست. قبل از restart:
```bash
asterisk -rx "core show channels concise"   # یا معادل
```
اگر Channel فعال وجود دارد، restart معطل شود.

## ۶. Exit Criteria برای مرحله P0 (تمام ۴ مورد)

- [ ] همه ۴ P0 در Production healthy test می‌شوند (test matrix)
- [ ] لاگ App ۲۴ ساعت بدون security alert
- [ ] CHANGELOG با ۴ ورودی P0 آپدیت شده
- [ ] PO sign-off

## ۷.gate به مسیر ۳ (تصمیم DB)
پس از P0 sign-off + ۷ روز uptime PG → `.env` lock + ADR-۰۰۱ finalize.

## ۸. ممنوعات در این سسن
- هیچ Write سرور بدون تایید PO.
- هیچ secret در گزارش / چت.
- `docker/init.sh` اجرا نشود.
- Brute-force روی Production ممنوع است.
