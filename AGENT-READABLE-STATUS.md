# AGENT-READABLE-STATUS — پروژه وای‌مکس (Ymax / voip-centos)

> تاریخ:`2026-11-23`  
> هدف: هر ایجنت جدید می‌تواند بدون depended کردن به چت قبلی، وضعیت فعلی را بخواند و کار را از همین نقطه ادامه دهد.

---

## ۱. سرور (تولیدی) — IP: ۱۹۲.۱۶۸.۲۵۰.۴۲ / mahan.opss.ir

| مورد | مقدار | وضعیت |
|------|-------|--------|
| OS | CentOS 7.9 (EOL) | سالم |
| CPU/load | ۰.۰۸ | نرمال |
| رم / دیسک | ۸.۷٪ / ۲۹٪ | نرمال |
| آپتایم | ~۲۷ ساعت | — |
| SSO/SSH | OpenSSH ۷.۴ LAN، WAN مسدود (میکروتیک #۶) | سالم |
| تیل‌اسکال | mahan-centos @ ۱۰۰.۱۱۲.۱۶۰.۱۷ | آنلاین |

### دیتابیس
| مورد | مقدار | وضعیت |
|------|-------|--------|
| Driver فعال | PostgreSQL ۱۶ (۱۲۷.۰.۰.۱:۵۴۳۲) | ✅ فعال |
| `.env` | `CC_DB_DRIVER=pg`، `DB_PORT=۵۴۳۲` | ست شده |
| MariaDB کانتینر | حذف از compose، پورت ۳۳۰۶ DOWN | ✅ حذف شده |
| MariaDB standby | `callcenter-web-db-1` | Up ~۱ساعت (healthy) — بازیابی‌شده توسط ساب‌ایجنت v2-migration-prep |
| PG systemd | `/etc/systemd/system/postgresql-16.service` | enabled + started |
| migrations ۰۰۱/۰۰۲/۰۰۳ | tenants + tenant_id + seed | ثبت شده در جدول migrations |

### کانتینرها (۴/۵ healthy + ۱ standby)
| نام | وضعیت | پورت |
|-----|--------|------|
| `callcenter-web-app-1` | Up ۱۹ساعت (healthy) | ۳۰۰۶ |
| `callcenter-web-pbx-1` | Up ۱۸ساعت (healthy) | Asterisk ۲۰.۲۱ |
| `callcenter-web-nginx-1` | Up ۲۱ساعت (healthy) | ۸۰/۴۴۳ TLS |
| `callcenter-web-turn-1` | Up ۲۶ساعت | coturn ۳۴۷۸ |
| `callcenter-web-db-1` | Up ~۱ساعت (healthy) | ۳۳۰۶ (standby MariaDB) |
| `ymax-sip-lab-asterisk` | Up ۲۴ساعت (healthy) | ۱۵۰۶۰/۸۰۸۹/۲۲۰۰۰-۲۲۱۰۰ (منفصل از تولیدی) |

### Health API
```
GET http://۱۹۲.۱۶۸.۲۵۰.۴۲:۳۰۰۶/health
→ {"status":"ok","service":"callcenter","version":"3.0.1","db":"ok"}
```
لاگ app: بدون خطای error/sql/syntax. تاریخچه crash-loop db.js:119 رفع شده.

---

## ۲. کد (backend) — وضعیت اعمال‌شده

| فایل | تغییر | عامل | تاریخ | وضعیت |
|------|-------|------|-------|--------|
| `backend/server.js:38` | JWT fail-fast — حذف fallback | Agnes | ۲۰۲۶-۱۱-۲۱ | Applied |
| `backend/server.js:240` | visitor/announce: UPDATE+INSERT (بدون ON DUPLICATE) | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/server.js:463` | INSERT callbacks با `RETURNING id` (PG) | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/server.js:521` | INSERT chat_messages با `RETURNING id` (PG) | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/server.js:645` | `/api/customers`: فیکس AND/OR precedence، HAVING→WHERE | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/runtime.js:221` | Survey upsert: UPDATE→INSERT (بدون ON DUPLICATE) | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/public/operator/index.html:84` | `dialInternal`: `SIP.Inviter(ua, uri, opts)` اصلاح شده | mahan | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/lib/db.js` | translateSQL v2: balanced-paren scanners (SUM(bool)، TIMESTAMPDIFF، DATE_SUB، DATE_FORMAT، ROUND(AVG)) | mahan + Agnes | ۲۰۲۶-۱۱-۲۲ | Applied |
| `backend/public/admin/index.html` | نسخه v3→v1 + برند وای‌مکس | Agnes | ۲۰۲۶-۱۱-۲۱ | Applied |
| `backend/public/operator/index.html` | نسخه v3→v1 + برند وای‌مکس | Agnes | ۲۰۲۶-۱۱-۲۱ | Applied |
| `backend/public/widget.js` | FiruzoCall → YmaxCall | Agnes | ۲۰۲۶-۱۱-۲۱ | Applied |
| `compose.yaml` | حذف db service (MariaDB)، `networks: {}`، رفع YAML خط ۱۵۲ | Agnes | ۲۰۲۶-۱۱-۲۱ | Applied |

### RTP (PBX)
| مسیر | مقدار قبل | مقدار بعد | وضعیت |
|------|----------|----------|--------|
| `asterisk/conf/rtp.conf` | ۱۰۰۰۰-۲۹۹۹۹ | ۴۹۱۶۰-۴۹۲۰۰ | Applied + PBX rebuild + verify |
| `asterisk/bundle/etc-asterisk/rtp.conf` | ۱۰۰۰۰-۲۹۹۹۹ | ۴۹۱۶۰-۴۹۲۰۰ | Applied + verify روی کانتینر زنده |

---

## ۳. تغییرات Mahān (۲۰۲۶-۱۱-۲۲) —詳ی

1. **`backend/lib/db.js` (۷ لکه comment `mahan — 2026-11-22 QC fix`)**:
   - `rewriteSumBoolean`: `SUM(bool)` با اسکن متوازن پرانتز (رفع مشکل `IN ('a','b')` که regex قبلی نمی‌فهمید)
   - `rewriteTimestampDiff`: `TIMESTAMPDIFF` با اسکن متوازن (رفع conك-with-order و خراب‌شدن COALESCE)
   - `DATE_SUB` برای فرم‌های `CURRENT_TIMESTAMP`/`CURRENT_DATE` (قوانین قبلی مرده بودند)
   - `DATE_FORMAT('%H:00')` → `TO_CHAR`
   - `ROUND(AVG(<double>),n)` → `::numeric`
   - shim `insertId`/`affectedRows` برای سازگاری mysql2
2. **`backend/server.js`**:
   - `/api/visitor/announce`: 앱-surface upsert (UPDATE با WHERE، اگر affectedRows=0 INSERT)
   - `/api/callbacks`: `RETURNING id` برای PG
   - `/api/chat`: `RETURNING id` برای PG
   - `/api/customers`: فیکسAND/OR precedence (was leaking cross-tenant rows)
3. **`backend/runtime.js`**: survey upsert بدون `ON DUPLICATE KEY`
4. **`public/operator/index.html`**: `dialInternal` — فراخوانی صحیح `SIP.Inviter(ua, uri, opts)`
5. **PBX image**: `max_contacts` داخلی اپراتور ۲→۱ (رفع باگ 603-Decline / Forked response)
6. **`/root/cc-cron`**: بک‌آپ شبانه MariaDB→`pg_dump` PG۱۶، حذف dump‌های <۱۰KB، watchdog `docker restart` مستقیماً app

---

## ۴. Multi-tenancy — فعال نیست (سینگل-تیننت)

- tenants table وجود دارد (migration ۰۰۱)
- tenantResolver وجود دارد (JWT tenant_id claim → subdomain → `?tenant=` → default id=۱) اما در producción فعال نیست
- صفر کوئری tenant-scoped در کد producción (تایید شده Phase ۱B)
- ADR-۰۰۱ (PostgreSQL RLS) معتبر است چون PG فعال است
- فاز ۲B مسدود است تا تصمیم DB بگیرнд

---

## ۵. P0های امنیتی (pending — تأکید PO)

| # | مشکل | شدت | جایگاه | اقدام |
|---|------|------|--------|------|
| P0-1 | `/api/widget/config` credentials TURN + visitor SIP password عمومی | P0 | API عمومی | auth guard + حذف یا رموز دинамик |
| P0-2 | `/api/records/:uniqueid` بدون tenant check (IDOR) | P0 | API | tenant check در endpoint |
| P0-3 | login بدون rate-limit / lockout | P0 | `/api/auth/login` | rate-limit middleware |
| P0-4 | Socket.IO broadcast بین tenants | P0 | `runtime.js` | `io.use` tenant-scoped rooms |

---

## ۶. بکاپ‌ها و rollback chain

| بکاپ | مسیر | توضیح |
|------|------|-------|
| rollback chain | HEAD `2e92d6337dfb` | فعلی |
| DB backup | `/root/backups/restore-20261121-1223/` | dump MariaDB + env snapshot + کد |
| UI backup | `/root/callcenter-deploy-backups/` | قبل از rebrand |
| V1 harden backup | `/opt/callcenter-web/.claude/v1-harden-backup-20261119-*` | — |
| PG data | `/var/lib/pgsql16/data/PG_VERSION` | سالم روی دیسک |

Rollback app:
```bash
docker tag callcenter-web-app:rollback-pre-mahan callcenter-web-app:local
docker compose up -d app
```
Rollback pbx (همانπα싯):
```bash
docker tag callcenter-web-pbx:rollback-pre-mahan callcenter-web-pbx:local
docker compose up -d pbx
```

---

## ۷. MCP sessions linked

| نام | session_id | وضعیت |
|-----|-----------|--------|
| v2-migration-prep | local_65e0f485-5756-44a2-85e4-2cab8a4e9b18 | done (PR #1) |
| centos-project-fork | local_69eadaa3-273c-437e-a0ae-b8c15746fbfb | idle |

---

## ۸. Constraints (AGENT-PROTOCOL)

1. هیچ Write روی سرور بدون تایید PO
2. Backup قبل از هر delete/write
3. Restart فقط وقتی تماس فعال نیست
4. هیچ secret در چت
5. re-run `docker/init.sh` ممنوع
6. برند: Ymax / وای‌مکس

---

## ۹. مسیر پیشنهادی PO

مسیر A (P0ها) → مسیر B (تصمیم DB پایدار) → مسیر C (فاز ۲B).
