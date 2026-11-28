# بررسی بکاپ، Rollback و Watchdog

**اولویت:** P0 مسدودکننده برای هر تغییر Production  
**سقف:** فقط بررسی — restore / تغییر watchdog اجرا نمی‌شود.

---

## ۱. بکاپ‌ها (داکومنت‌شده)

| بکاپ | مسیر | توضیح | طبقه‌بندی |
|------|------|-------|-----------|
| Rollback chain | HEAD `2e92d6337dfb` | فعلی | DOCUMENTED |
| DB backup | `/root/backups/restore-20261121-1223/` | dump MariaDB + env snapshot + کد | DOCUMENTED |
| UI backup | `/root/callcenter-deploy-backups/` | قبل از rebrand | DOCUMENTED |
| V1 harden backup | `/opt/callcenter-web/.claude/v1-harden-backup-20261119-*` | — | DOCUMENTED |
| PG data | `/var/lib/pgsql16/data/PG_VERSION` | سالم روی دیسک | DOCUMENTED |
| Pre-PG backup | `/root/backups/mahan-pre-qc-20261122/` | کد ۶.۸MB +دامپ PG + compose/env/crontab | DOCUMENTED |

## ۲. Rollback chain (App / PBX)

```bash
# App rollback
docker tag callcenter-web-app:rollback-pre-mahan callcenter-web-app:local
docker compose up -d app

# PBX rollback
docker tag callcenter-web-pbx:rollback-pre-mahan callcenter-web-pbx:local
docker compose up -d pbx
```

**وضعیت:** تحلیلی است — باید در محیط تست تایید شود (تغییر تولیدیodynamic نیست).

## ۳. Crontab `/root/cc-cron`

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Nightly backup | MariaDB → pg_dump (PG16 هاست) | DOCUMENTED |
| Delete small dumps | <۱۰KB حذف می‌شوند | DOCUMENTED |
| Watchdog | `docker restart` مستقیم app (بدون سرویس db) | DOCUMENTED |

## ۴. Watchdog risk

`docker restart app` می‌تواند:
- Cut active Socket.IO connections
- Disrupt operator interfaces
- Interrupt ongoing call operations (اگر app در وسط پردازش تماس باشد)

**توصیه:** watchdog را فقط در Maintenance window اجرا کنید؛ قبل از restart بررسی تماس فعال (CORE show channels).

## ۵. PostgreSQL backup integrity

- تست pg_dump: ۴۳KB سالم (داکومنت‌شده)
- restore feasibility: **تست نشده** (UNKNOWN)

## ۶. Side effects / Dependencies
- بکاپ‌ها باید با state فعلی کد و DB همخوانی داشته باشند.
- Rollback images باید با migrations فعلی سازگار باشند.

## ۷. Test / Backup (before any write)
- Backup قبل از هر delete/write (AGENT-PROTOCOL #۲)
- Restore تست در isolated environment (نه Production)
