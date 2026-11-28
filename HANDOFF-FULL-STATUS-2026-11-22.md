# HANDOFF-FULL-STATUS — پروژه وای‌مکس (Ymax / voip-centos)
> **تاریخ:** ۲۰۲۶-۱۱-۲۲  
> **سرور:** `mahan.opss.ir` — ۱۹۲.۱۶۸.۲۵۰.۴۲ (LAN) / ۱۰۰.۱۱۲.۱۶۰.۱۷ (Tailscale) / ۴۶.۱۰۰.۵۸.۲۰ (عمومی)  
> **مسیر پروژه روی سرور:** `/opt/callcenter-web/`  
> **مسیر لوکال:** `C:\Users\someBody\Desktop\voip-centos\` (و `voip-centos-z` برای اسناد)  
> **نسخه:** ۳.۰.۱ — target V1 (سینگل-تیننت، production-hardened)  
> **دیتابیس فعال:** PostgreSQL ۱۶ روی هاست (پورت ۵۴۳۲) — MariaDB ۱۰.۱۱ کانتینر DOWN (حذف شد از compose)  
> **وضعیت multi-tenancy:** **غیرفعال (سینگل-تیننت)** — tenants table وجود دارد اما کوئری‌ها scope نشده‌اند

---

## ۱. وضعیت زنده سرور (تایید شده ۲۰۲۶-۱۱-۲۲)

| مورد | وضعیت | جزئیات |
|------|--------|--------|
| سیستم عامل | CentOS 7.9 (EOL ژوئن ۲۰۲۴) | kernel ۳.۱۰.۰-۱۱۶۰.۱۱۹.۱.el7 |
| آپتایم | ~۲۷ ساعت | load ۰.۰۸ |
| رم / دیسک | ۸.۷٪ / ۲۹٪ | نرمال |
| کانتینرهای تولیدی | ۴/۴ healthy | app (۳۰۰۶)، pbx (Asterisk ۲۰.۲۱)، nginx (۸۰/۴۴۳ TLS)، turn (coturn ۳۴۷۸) |
| app container | Up (healthy) | node healthcheck OK،ドルconf از PG |
| PBX container | Up (healthy) | WSS روی :۵۰۶۰، ۵۳ PJSIP endpoints، ARI loaded |
| nginx container | Up (healthy) | TLS cert bound به IP LAN ۱۹۲.۱۶۸.۲۵۰.۴۲ |
| turn container | Up | coturn static credential |
| پورت ۵۴۳۲ | listen | PostgreSQL ۱۶ روی هاست |
| پورت ۳۳۰۶ | DOWN | MariaDB کانتینر حذف شده (db service از compose حذف شد) |
| پورت ۳۰۰۶ | listen | Node app |
| Health API | ✅ | `{"status":"ok","service":"callcenter","version":"3.0.1","db":"ok"}` |
| SSH | سالم | OpenSSH ۷.۴ (LAN)، WAN مسدود (میکروتیک قاعده #۶ غیرفعال) |
| تیل‌اسکال | آنلاین | mahan-centos @ ۱۰۰.۱۱۲.۱۶۰.۱۷ |
| IP عمومی | SSH مسدود | پورت ۲۲ اینترنت از طریق میکروتیک مسدود شده |

---

## ۲. vencedرهای انجام شده (هر سه 시리즈)

### ۲.۱ سرور / اینفراستراکچر
| تاریخ | عامل | کار | وضعیت | بکاپ |
|-------|------|-----|--------|------|
| ۲۰۲۶-۱۱-۱۹ | Claude | ایجاد `.claude/` + `BRIEF-CASE.md` | ✅ | فقط اسناد |
| ۲۰۲۶-۱۱-۱۹ | Claude | آپلود `PROJECT-PROPOSAL.md` | ✅ | فقط اسناد |
| ۲۰۲۶-۱۱-۱۹ | Claude | Audit فایل‌های قدیمی فیرزو + بکاپ ۶ فایل در `.claude/pre-v1-cleanup-*` | ✅ | بکاپ قبل از تغییر |
| ۲۰۲۶-۱۱-۱۹ | Claude | ایجاد `AGENT-PROTOCOL.md` + `CHANGELOG-AGENT-ACTIONS.md` | ✅ | فقط اسناد |
| ۲۰۲۶-۱۱-۱۹ | Claude | کامیت سیستم استمراری ایجنت | ✅ | ۵ فایل .claude |
| ۲۰۲۶-۱۱-۱۹ | Claude | حل O2 (recording = MixMonitor + SFTP) و O3 (Tailscale online) — read-only | ✅ | read-only |
| ۲۰۲۶-۱۱-۱۹ | Claude | ثبت تصمیمات PO: DB=MariaDB (فعلاً PG)، V1=سینگل، Rebrand=کامل | ✅ | فقط ثبت |
| ۲۰۲۶-۱۱-۱۹ | Claude | بستن O1 — سطح مجوز DB least-privilege | ✅ | read-only |
| ۲۰۲۶-۱۱-۱۹ | Claude | V1-۱ JWT fail-fast (`server.js:38`) | Applied، restart معلق | بکاپ قبل از تغییر |
| ۲۰۲۶-۱۱-۱۹ | Claude | V1-۲ RTP ۱۰۰۰۰-۱۰۱۹۹ → ۴۹۱۶۰-۴۹۲۰۰ + reload Asterisk | Applied + reload | — |
| ۲۰۲۶-۱۱-۱۹ | Claude | V1-۳ Rebrand فیرزو → وای‌مکس (۶ فایل) | Applied | — |
| ۲۰۲۶-۱۱-۱۹ | Claude | V1-۴ TLS opción D | ثبت SOP | انجام بعد |
| ۲۰۲۶-۱۱-۲۱ | ZCode/GLM | استقرار آزمایشگاه SIP + برگشت کامل | ✅ | برگشت کامل |
| ۲۰۲۶-۱۱-۲۱ | ZCode/GLM | ریپوی monorepo `mohammadfj/Ymax` + CI | ✅ | فقط GitHub |
| ۲۰۲۶-۱۱-۲۱ | V2 session | نصب PostgreSQL ۱۶، لود ۱۲ جدول، `.env`→DB_PORT=۵۴۳۲، container به PG | ✅ | migration G2.۱–G2.۴ |
| ۲۰۲۶-۱۱-۲۱ | این سشن | بازنویسی `compose.yaml` (حذف db service، رفع خطای YAML) | ✅ | — |
| ۲۰۲۶-۱۱-۲۱ | این سشن | استارت app container (healthy) | ✅ | — |
| ۲۰۲۶-۱۱-۲۱ | این سشن | پاکسازی orphan db container | ✅ | — |
| ۲۰۲۶-۱۱-۲۱ | این سشن | تایید health API + panels (مدیریت v1 / اپراتور v1) | ✅ | — |
| ۲۰۲۶-۱۱-۲۲ | این سشن | پاکسازی `firuzo` از ۲۴ فایل لایو | ✅ | backup داده قبل از تغییر |

### ۲.۲ کد و پیکربندی (تغییرات اعمال شده)
| فایل / مسیر | تغییر | وضعیت |
|-------------|--------|------|
| `backend/server.js:38` | JWT fallback حذف شد؛ fail-fast اگر `CC_JWT` ست نباشد | Applied، restart app معلق (PO) |
| `/etc/asterisk/rtp.conf` | RTP `۱۰۰۰۰-۱۰۱۹۹` → `۴۹۱۶۰-۴۹۲۰۰` | Applied + reload |
| `backend/public/admin/index.html` | نسخه v3 → v1 + برند وای‌مکس | Applied |
| `backend/public/operator/index.html` | نسخه v3 → v1 + برند وای‌مکس | Applied |
| `backend/public/widget.js` | FiruzoCall → YmaxCall | Applied |
| `.env` | `DB_PORT=۵۴۳۲`، `CC_DB_DRIVER=pg`، `BRAND_NAME=وای‌مکس` | Applied |
| `compose.yaml` | حذف db service (MariaDB)، resolve YAML corruption | Applied |
| `lib/db.js` (container) | PG driver با `translateSQL()` | Applied |
| migrations ۰۰۱/۰۰۲/۰۰۳ | جدول tenants + ثبت migration | Recorded |
| `/root/backups/restore-20261121-1223/` | dump MariaDB + env snapshot + کد | بکاپ rollback |

---

## ۳. وضعیت multi-tenancy (پاسخ سوال شما)

**هنوز سینگل-تیننت است. multi-tenancy فعال نشده است.**

ماده ثابت‌شده:
- tenants table وجود دارد (migration ۰۰۱) اما کوئری‌ها `WHERE tenant_id=?` ندارند
- tenantResolver وجود دارد (JWT tenant_id claim → host subdomain → `?tenant=` → default id=1) اما در producción فعال نیست
- صفر کوئری tenant-scoped در کد producció (تایید شده Phase 1B)
- ADR-۰۰۱ (PostgreSQL RLS) در حال حاضر معتبر است چون PG فعال است
- فاز ۲B (معماری نهایی) مسدود است تا قرار DB angenommen شود
- فاز ۳ (DDL چند tenant، RLS) هنوز شروع نشده است

---

## ۴. اشکال‌زدایی اخیر (این سشن)

### ۴.۱ مشکل: app container در حالت Created (DOWN)
**در catalyst:** `compose.yaml` خطای YAML داشت:
- خط ۱۵۲: `volumes:  recording_cache:` (map inline نامعتبر)
- خط ۱۵۰: `networks:` خالی (باید `networks: {}` باشد)

**ر_after:** با `sed` و `python` رفع شد، `docker compose config -q` پاس، `docker compose up -d app` اجرا شد → healthy.

### ۴.۲ مشکل: orphan db container
کانتینر `callcenter-web-db-1` بعد از حذف db service از compose به عنوان orphan باقی می‌ماند. با `--remove-orphans` پاک شد.

### ۴.۳ مشکل: firebase verwijد (firuzo)
۵۴ فایل مرجع فیرزو وجود داشت؛ ۲۴ فایل لایو با `sed 's/firuzo/ymax/g; s/فیرزو/وای‌مکس/g` پاکسازی شد. فقط `backend/lib/db.js.bak_mariadb` (بکاپ) باقی مانده (دست‌نخورده).

### ۴.۴-state فعلی کانتینرها
```
callcenter-web-app-1     Up (healthy)
callcenter-web-pbx-1     Up (healthy)
callcenter-web-nginx-1   Up (healthy)
callcenter-web-turn-1    Up
```

---

## ۵. offenen محصولات و Tasks (برنامه‌های بعدی)

### ۵.۱ مسیر A — P0های امنیتی (اولویت فوری) — **تأکید**

| # | مشکل | شدت | جایگاه | اقدام |
|---|------|------|--------|------|
| P0-1 | `/api/widget/config` credentials TURN + visitor SIP password عمومی | P0 | API عمومی | auth guard + حذف یا رموز دинамиک |
| P0-2 | `/api/records/:uniqueid` بدون tenant check (IDOR) | P0 | API | tenant check در endpoint |
| P0-3 | login بدون rate-limit / lockout | P0 | `/api/auth/login` | rate-limit middleware |
| P0-4 | Socket.IO broadcast بین tenants | P0 | `runtime.js` | `io.use` tenant-scoped rooms |

### ۵.۲ مسیر B — تصمیم دیتابیس (مسدود کننده فاز ۲B)

`.env` فعلی: `DB_PORT=۵۴۳۲` / `CC_DB_DRIVER=pg` → PostgreSQL  
سؤال PO: این حالت پایدار بماند یا برگردیم به MariaDB؟

- **PG بماند** → Phase 2B با RLS (توصیه agent)
- **برگشت به MariaDB** → `.env` تغییر به `DB_PORT=۳۳۰۶` / `CC_DB_DRIVER=mysql` + restart app + rewind PG migration

### ۵.۳ مسیر C — فاز ۲B معماری نهایی (بعد از B)

- DDL چند tenant
- Tenant resolver فعال‌سازی
- Asterisk namespacing (`PJSIP/<tenant>_1001`)
- ARCHITECTURE.md نهایی

### ۵.۴ فاز ۳–۸ (pending)

| فاز | عنوان | وضعیت |
|------|-------|--------|
| ۳ | دیتابیس Architektur (SCHEMA.sql، RLS، مهاجرت) | ⏳ pending |
| ۴ | امنیت و احراز هویت (TURN creds، rate limit، audit log) | ⏳ pending |
| ۵ | منطق کسب‌وکار SaaS (provisioning، trial، feature gate) | ⏳ pending |
| ۶ | Backend و API (OpenAPI، Socket.IO events) | ⏳ pending |
| ۷ | معماری SDK | ⏳ pending |
| ۸ | پیاده‌سازی، تست، استقرار | ⏳ آینده |

---

## ۶. پسوردها و اعتبارنامه‌ها (در چت نیستند — فقط در سرور)

| اعتبارنامه | مسیر |
|-----------|------|
| SSH root | `.env` سرور / `HANDOFF-2026-11-21.md` |
| Admin / Operator | `server .env` (`ADMIN_PASS` / `OPERATOR_PASS`) |
| DB_PASS / JWT_SECRET / TURN | `/opt/callcenter-web/.env` |
| GitHub token | `voip-centos-z/secrets/TT.txt` |

دستور بازیابی روی سرور:
```bash
ssh root@192.168.250.42 "grep -E 'ADMIN_PASS|OPERATOR_PASS|DB_PASS|JWT_SECRET|CC_TURN|CC_JWT' /opt/callcenter-web/.env"
```

---

## ۷. بکاپ‌ها وrollback chain

```
HEAD: 2e92d6337dfb (فعلی)
     ← c21699599fd7
     ← 5f991578bbb9
     ← 4946daa43110
```

بکاپ‌های دیتابیس: `/root/backups/restore-20261121-1223/`  
بکاپ‌های UI: `/root/callcenter-deploy-backups/`  
بکاپ قبل از V1 hardening: `/opt/callcenter-web/.claude/v1-harden-backup-20261119-*`  
بکاپ قبل از rebrand: `.claude/pre-v1-cleanup-20261119-*`  
دیتای PostgreSQL روی دیسک سالم است: `/var/lib/pgsql16/data/PG_VERSION`

---

## ۸. MCP sessions Linked

| نام | session_id | وضعیت |
|-----|-----------|--------|
| v2-migration-prep | local_65e0f485-5756-44a2-85e4-2cab8a4e9b18 | done (PR #1) |
| centos-project-fork | local_69eadaa3-273c-437e-a0ae-b8c15746fbfb | idle |

---

## ۹. laws و constraints (AGENT-PROTOCOL)

- No writes to server without PO approval
- Backup before every delete/write
- Restart only when no active calls
- No secrets in chat
- No re-running `docker/init.sh`
- Brand: Ymax / وای‌مکس

---

## ۱۰. next step decision (PO)

مشاوره من: مسیر A (P0ها) → سپس مسیر B (تصمیم DB) → سپس مسیر C (فاز ۲B).

چیک-list برای شروع مسیر A:
1. `/api/widget/config` auth guard
2. `/api/records/:uniqueid` tenant check
3. `/api/auth/login` rate-limit
4. `runtime.js` tenant-scoped rooms

---

<small>تهیه‌شده ۲۰۲۶-۱۱-۲۲. هیچ Write جدیدی روی سرور در این سشن انجام نشده است (فقط Read و بررسی).</small>
