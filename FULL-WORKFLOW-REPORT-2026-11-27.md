# گزارش کامل وضعیت پروژه Ymax — ۲۷ نوامبر ۲۰۲۶

> این سند برای **دیگه‌ایجنت** قابل خواندن است: تمام مسیر، تصمیم‌ها، فایل‌ها و دستورات مرجع در اینجا گردآوری شده‌اند.

---

## ۱. کجای نقشه راه / ورک فلو هستیم؟

| فاز | عنوان | وضعیت | نکته |
|-----|-------|-------|------|
| ۰ | Baseline audit + IR-۰ | ✅ done | PG روی هاست، MariaDB standby |
| ۱ | V1 hardening (JWT، RTP، Rebrand، compose) | ✅ done | app healthy، health OK |
| ۱b | Mahān QC + SQL translation + PBX fixes | ✅ done | ۴۲/۴۲ QC pass، zero unhandled |
| ۱c | CHANGELOG re-sync | ✅ done | RECONSTRUCTED marker removed |
| **۲** | **P0 Security (CURRENT BLOCKER)** | ⏳ **NEXT** | Prioritized by PO |
| ۳ | تصمیم پایداری DB (PG vs MariaDB) | ⏳ BLOCKED by ۲ | PG currently active |
| ۴ | فاز ۲B معماری نهایی (multi-tenant) | ⏳ BLOCKED by ۳ | ADR-۰۰۱ ready |
| ۵–۸ | فازهای آینده | ⏳ آینده | — |

**موقعیت فعلی:** در **مرحله ۲ (P0 Security Fixes)** هستیم. فاز ۴ (TURN REST) **تکمیل و E2E تایید شده** است.

---

## ۲. تا اینجا چه کارهایی انجام شده؟

### الف) زیرساخت و دیتابیس
- **PostgreSQL 16** روی هاست (127.0.0.1:5432) فعال، `CC_DB_DRIVER=pg`
- MariaDB container حذف از compose، standby container `callcenter-web-db-1` در دسترس (port 3306)
- Migration 001/002/003: `tenants` table + `tenant_id` FKs + seed data اعمال شده
- ۲۷ ساعت uptime، load 0.08، RAM 8.7%، Disk 29%

### ب) کد بک‌اند (backend/server.js, runtime.js, db.js)
| فایل | تغییر کلیدی |
|------|------------|
| `server.js:38` | JWT fail-fast — حذف fallback |
| `server.js:240` | visitor/announce: UPDATE+INSERT (بدون ON DUPLICATE) |
| `server.js:463,521` | INSERT callbacks/chat_messages با `RETURNING id` (PG) |
| `server.js:645` | `/api/customers`: فیکس AND/OR precedence، HAVING→WHERE |
| `runtime.js:221` | Survey upsert: UPDATE→INSERT |
| `lib/db.js` | translateSQL v2: balanced-paren scanners (SUM(bool)، TIMESTAMPDIFF، DATE_SUB، DATE_FORMAT، ROUND(AVG)) + shim `insertId`/`affectedRows` |
| `public/*.html` | Rebrand: FiruzoCall → YmaxCall، نسخه v3→v1 |

### ج) PBX / Asterisk / RTP
- `rtp.conf`: پورت رنج 10000-29999 → **49160-49200** (هم‌گام با TURN relay range)
- PBX image rebuild: `max_contacts` داخلی اپراتور ۲→۱ (رفع 603-Decline / Forked response)
- Asterisk 20.21 در `callcenter-web-pbx-1` healthy

### د) فاز ۴ — TURN REST Auth (✅ **E2E VERIFIED 2026-11-27**)
```
App minting:  username = "<unix_ts>:t<tenantId>"
              credential = base64(HMAC-SHA1(TURN_SECRET, username))
Endpoint:     GET /api/widget/config?tenantId=1 → ice_servers با turn:192.168.250.42:3478?transport=tcp/udp
Coturn:       use-auth-secret + static-auth-secret (REST mode)
              realm=192.168.250.42، listening-port=3478، relay-ip=192.168.250.42
              min-port=49160، max-port=49200، fingerprint، lt-cred-mech، stale-nonce=600
```
**تست زنده:**
```bash
# داخل کانتینر turn:
turnutils_uclient -t -u "1795785459:t1" -w "ErqOveHyyvjcH4cjoDa/R4PKDRU=" \
  -e 8.8.8.8 -r 3478 192.168.250.42
# نتیجه: success، relay addr 192.168.250.42:62018
```
> این یعنی **per-tenant TURN production-ready** است. فایل‌های مرجع:
> - `turn-rest-e2e-verified.md` (memory)
> - `turn-debug-lessons.md` (memory —client-side pitfalls)

### هـ) بکاپ‌ها و Rollback Chain
| بکاپ | مسیر |
|------|------|
| App rollback tag | `callcenter-web-app:rollback-pre-mahan` |
| PBX rollback tag | `callcenter-web-pbx:rollback-pre-mahan` |
| DB backup | `/root/backups/restore-20261121-1223/` (MariaDB dump + env + code) |
| UI backup | `/root/callcenter-deploy-backups/` |
| PG data | `/var/lib/pgsql16/data/PG_VERSION` سالم |

---

## ۳. از اینجا به بعد چه کارهایی باید انجام شود؟

### اولویت ۱: مرحله ۲ — P0 Security Fixes (بلاکер فعلی)
| ID | مسئله | فایل هدف | Fix Plan |
|----|-------|----------|----------|
| **P0-1** | `/api/widget/config` credentials TURN + visitor SIP password عمومی | `server.js` | `auth(["admin","operator"])` guard + visitor creds حذف / dynamic token |
| **P0-2** | `/api/records/:uniqueid` بدون tenant check (IDOR) | `server.js` | `qTenant(req.user.tenant_id, ...)` + check owner |
| **P0-3** | login بدون rate-limit / lockout | `server.js` + `lib/loginguard.js` (جدید) | `express-rate-limit` (۵ req/min per IP، lockout ۱۵دقیقه) |
| **P0-4** | Socket.IO broadcast بین tenants | `runtime.js` | `io.use` tenant-scoped rooms (`tenant:<id>:<room>`) |

**Exit Criteria مرحله ۲:**
- [ ] همه ۴ P0 در production healthy test می‌شوند
- [ ] لاگ app ۲۴ ساعت بدون security alert
- [ ] CHANGELOG با ۴ ورودی P0 آپدیت شده
- [ ] PO sign-off

### اولویت ۲: مرحله ۳ — تصمیم پایداری DB
| Option | شرط ادامه | Impact |
|--------|-----------|--------|
| **PG بماند** | `CC_DB_DRIVER=pg` + health OK ۷ روز متوالی | فاز ۲B با RLS (PostgreSQL row-level security) |
| **برگشت MariaDB** | PO explicit approval + rollback test passed | فاز ۲B با app-level tenant scoping (بدون RLS) |

**Decision Gate:** PO explicit approval + ۷ روز uptime PG (actively monitoring)

### اولویت ۳: مرحله ۴ — فاز ۲B معماری نهایی (Multi-tenant)
**Pre-reqs:** P0ها تمام + تصمیم DB نهایی + ADR-۰۰۱ finalized

| Deliverable | توضیح |
|-------------|-------|
| `ARCHITECTURE.md` | Spec کامل: tenant model، DB strategy، Asterisk namespacing، API contracts |
| `tenantResolver.js` | فعال‌سازی در production |
| DDL multi-tenant | `SCHEMA.sql` — tenants، tenant_id FKs، RLS policies (اگر PG) |
| Asterisk namespacing | `PJSIP/<tenant>_1001` pattern، context isolation |
| Provisioning API | `POST /api/tenants` + auto-provision SIP + DB |

---

## ۴. مشکلات تا اینجا چی‌ها هستن؟

| # | مشکل | شدت | حل شده؟ | نکته |
|---|------|-----|---------|------|
| 1 | Turn crash-loop: unresolved `__TURN_USER__`/`__TURN_PASS__` placeholders | Critical | ✅ | Fixed via `__TURN_AUTH__` template + render patch + image rebuild |
| 2 | UDP binding/allocate timeouts | High | ✅ | Wrong magic cookie `0x1A0D9600` → fixed to RFC 5389 `0x2112A442` |
| 3 | MI variants returned 0x0113 | High | ✅ | Root causes: ERROR-CODE parsing، NONCE type (coturn=0x0015 not 0x0004)، two-step dance، HMAC zero-suffix |
| 4 | Hand-rolled Python USERNAME attr=0x0001 | Medium | ✅ | Fixed to 0x0006 (MAPPED-ADDRESS vs USERNAME) |
| 5 | Step2 included NONCE attr → 438 "wrong nonce" | Medium | ✅ | Omit NONCE in step2 |
| 6 | ALLOCATE missing REQUESTED_TRANSPORT=0x0019 → 400 | Medium | ✅ | Added attr 0x0019 value 0x11 (UDP) |
| 7 | HMAC over pre+fingerprint instead of pre alone | Medium | ✅ | Fixed per RFC 5389 §15.3 |
| 8 | **P0-1..P0-4 security holes** | **Critical** | ❌ | **Active blocker** — needs PO approval to proceed |
| 9 | PG 7-day uptime test pending | Medium | ⏳ | Deferred — needs monitoring |
| 10 | t1 SIP password convention alignment | Low | ⏳ | Deferred |

---

## ۵. مشکلات احتمالی و قابل پیش‌بینی

| ریسک | احتمال | تأثیر | تدبیر پیشگیرانه |
|------|--------|-------|------------------|
| **PG stability issue در ۷ روز آینده** | Medium | High (بلاک فاز ۲B) | MariaDB standby در دسترس، rollback plan تست شده |
| **P0 fixes breaking existing flows** | Medium | High | Backup قبل از هر write، health check بعد از هر deploy |
| **TURN relay port exhaustion (49160-49200 فقط ۴۰ پورت)** | Low | Medium | مانیتورینگ `turnutils_uclient`، افزایش range در صورت نیاز |
| **Coturn REST secret rotation بدون downtime** | Low | Medium | coturn `static-auth-secret` reload نیاز به restart دارد — plan برای rotation |
| **Asterisk context isolation leak بین tenants** | Medium | High | تست cross-tenant call در فاز ۲B matrix |
| **RLS policy performance در مقیاس بالا** | Medium | Medium | Indexes روی `tenant_id` (migration 004)، EXPLAIN ANALYZE |
| **CentOS 7.9 EOL — security patches** | High | Medium | Migration plan به Rocky/AlmaLinux در روڈمپ بلندمدت |
| **Super-admin credential compromise** | Low | Critical | MFA، audit log، limited scope |

---

## ۶. چه تغییراتی در ورک فلو ایجاد شده؟

### از WORKFLOW-NEXT.md (2026-11-23) تا امروز:
1. **فاز ۴ (TURN REST) اضافه و تکمیل شد** — در workflow اصلی نبود، به عنوان "Phase 4" در CHANGELOG ثبت شده
2. **مرحله ۳ (DB قرار) صریح‌تر شد** — criteria ۷ روز uptime + PO approval
3. **P0 Security به عنوان CURRENT BLOCKER مشخص شد** — قبلاً "Phase 2" بود، الان اولویت ۱
4. **Multi-tenancy (فاز ۲B) صریحاً BLOCKED by DB decision** — پیش از این وابستگی مشخص نبود
5. **Rollback plan برای MariaDB documented** — در `WORKFLOW-NEXT.md` و `AGENT-READABLE-STATUS.md`
6. **Memory files برای TURN debugging ایجاد شده** — `turn-rest-e2e-verified.md`، `turn-debug-lessons.md`

### فایل‌های تغییر یافته در این بازه:
- `WORKFLOW-NEXT.md` — آپدیت شده با فاز ۴ و گیت‌های صریح
- `AGENT-READABLE-STATUS.md` — وضعیت کامل برای ایجنت‌های جدید
- `CHANGELOG-AGENT-ACTIONS.md` — ورودی Phase 4 اضافه شده
- `.env` — `TURN_SECRET` و `CC_TURN_SECRET` ست شده
- `docker/render-config.py` — turn block پر می‌کند `__TURN_AUTH__`
- `nginx/turnserver.conf.tmpl` — single placeholder `__TURN_AUTH__`

---

## ۷. مرحله بعدی چیست؟

### **مرحله بعدی بلافاصله: P0-1 و P0-2 (Widget Config + Records Authorization)**

**اقدامات مورد نیاز (نیاز به PO approval):**

```bash
# 1. Backup قبل از شروع
ssh root@192.168.250.42 "
  tar -czf /root/backups/pre-p0-$(date +%F).tgz /opt/callcenter-web/backend/
  pg_dump -h 127.0.0.1 -U callcenter callcenter > /root/backups/pre-p0-pg-$(date +%F).sql
"

# 2. P0-1: Guard /api/widget/config
# backend/server.js: افزودن auth(["admin","operator"]) قبل از برگرداندن credentials
# حذف visitor SIP password از response

# 3. P0-2: Tenant check در /api/records/:uniqueid
# استفاده از qTenant(req.user.tenant_id, ...) + ownership verification

# 4. Deploy و تست
curl -s http://192.168.250.42:3006/api/widget/config        # باید 401 بدهد
curl -s -H "Authorization: Bearer <token-A>" \
  http://192.168.250.42:3006/api/records/<uniqueid-B>      # باید 403/404 بدهد
```

**پس از P0-1/2:** P0-3 (rate-limit) و P0-4 (Socket.IO isolation) به ترتیب.

---

## ۸. کل ورک فلو — باید چی باشد و الان چیست؟

### ورک فلو هدف (Ideal Workflow)

```
┌─────────────────────────────────────────────────────────────────┐
│                    YMAX PRODUCTION ROADMAP                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  PHASE 0: Baseline Audit + IR-0              ✅ DONE           │
│  ├─ Server inventory, DB topology, backup chain                │
│  └─ Security baseline                                           │
│                                                                 │
│  PHASE 1: V1 Hardening                         ✅ DONE           │
│  ├─ JWT fail-fast, RTP port alignment, Rebrand (Ymax)          │
│  ├─ Compose cleanup, SQL translation (MySQL→PG)                │
│  ├─ Mahān QC (42/42 pass), PBX fixes (max_contacts, 603)      │
│  └─ CHANGELOG reconstruction                                    │
│                                                                 │
│  PHASE 2: P0 Security Fixes              🔴 CURRENT BLOCKER    │
│  ├─ P0-1: Widget config auth guard + cred removal              │
│  ├─ P0-2: Records endpoint tenant check (IDOR fix)             │
│  ├─ P0-3: Login rate-limit (5 req/min, 15min lockout)         │
│  ├─ P0-4: Socket.IO tenant-scoped rooms                        │
│  └─ Gate: 24h clean logs + PO sign-off                         │
│                                                                 │
│  PHASE 3: DB Persistence Decision             ⏳ PENDING       │
│  ├─ Option A: PG stays → 7-day uptime + health OK              │
│  ├─ Option B: Rollback MariaDB → PO explicit + rollback test   │
│  └─ Gate: PO approval + ADR-001 finalize                       │
│                                                                 │
│  PHASE 4: TURN REST Auth                      ✅ DONE (E2E)   │
│  ├─ coturn use-auth-secret + static-auth-secret                │
│  ├─ App mints per-tenant HMAC-SHA1 credentials                 │
│  ├─ Verified: turnutils_uclient ALLOCATE success               │
│  └─ Relay range 49160-49200 aligned with RTP                   │
│                                                                 │
│  PHASE 2B: Final Multi-tenant Architecture     ⏳ BLOCKED      │
│  ├─ Pre-reqs: P0 done + DB decision + ADR-001                  │
│  ├─ ARCHITECTURE.md spec complete                              │
│  ├─ tenantResolver.js production activation                    │
│  ├─ DDL: SCHEMA.sql + RLS policies (if PG)                     │
│  ├─ Asterisk namespacing: <slug>_<ext>, context isolation      │
│  ├─ Provisioning API: POST /api/tenants                        │
│  └─ Test Matrix: cross-tenant isolation, provisioning E2E      │
│                                                                 │
│  PHASES 3-8: Future (DB Arch, Security, SaaS, SDK, Deploy)    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### وضعیت فعلی (Current State) — خلاصه یک‌خطی

| مولفه | وضعیت |
|--------|-------|
| **Server** | Healthy (CentOS 7.9, PG 16, 4/5 containers up) |
| **Database** | PG active, MariaDB standby ready |
| **App** | v3.0.1, JWT fail-fast, SQL translate v2, Ymax brand |
| **PBX** | Asterisk 20.21, RTP 49160-49200, max_contacts=1 |
| **TURN** | ✅ REST auth live + E2E verified |
| **Security** | ❌ 4 P0 holes open (blocker) |
| **Multi-tenant** | Schema ready, resolver inactive, blocked by P0+DB |
| **Docs** | Complete for handoff (see below) |

---

## ۹. تمام اسناد برای ایجنت بعدی (Documentation Index)

### فایل‌های هسته (Must Read اول)
| فایل | مسیر | توضیح |
|------|------|-------|
| `AGENT-READABLE-STATUS.md` | `C:\Users\someBody\Desktop\voip-centos\` | وضعیت کامل سرور، کد، DB، بکاپ، constraints |
| `WORKFLOW-NEXT.md` | `C:\Users\someBody\Desktop\voip-centos\` | نقشه راه با گیت‌ها، test scripts، rollback commands |
| `ARCHITECTURE.md` | `C:\Users\someBody\Desktop\voip-centos\` | Spec فاز ۲B: tenant model، API contracts، Asterisk namespacing |
| `ADR-001-MULTI-TENANT-DB.md` | `C:\Users\someBody\Desktop\voip-centos\` | PostgreSQL RLS design decision |

### اسناد P0 Security
| فایل | مسیر |
|------|------|
| `P0-1-WIDGET-CREDENTIAL-REMEDIATION-PLAN.md` | root |
| `P0-2-RECORDS-AUTHORIZATION-REVIEW.md` | root |
| `P0-3-LOGIN-RATE-LIMIT-PLAN.md` | root |
| `P0-4-SOCKETIO-ISOLATION-REVIEW.md` | root |
| `P0-SECURITY-FINAL-REPORT.md` | root |
| `P0-SECURITY-TEST-MATRIX.md` | root |
| `PRE-P0-SECURITY-AUDIT.md` | root |

### اسناد زیرساخت و عملیات
| فایل | مسیر |
|------|------|
| `PRODUCTION-CHANGE-PLAN.md` | root |
| `BACKUP-AND-ROLLBACK-REVIEW.md` | root |
| `RTP-TURN-CAPACITY-REVIEW.md` | root |
| `POSTGRESQL-MARIADB-RELIABILITY-REVIEW.md` | root |
| `PHASE2B-P2B-E2E-RESULTS.md` | root |
| `HANDOFF-FULL-STATUS-2026-11-22.md` | root |
| `changelog_new.md` | root |

### Memory Files (دسترسی در این session)
| نام | محتوای کلیدی |
|-----|-------------|
| `turn-rest-e2e-verified` | Phase 4 TURN REST auth verified live — credentials formula، coturn config، uclient test |
| `turn-debug-lessons` | Hand-rolled STUN/TURN client pitfalls: attr numbers، transport، nonce، MI HMAC |

### اسکریپت‌های مرجع (Runnable)
| اسکریپت | کاربرد |
|---------|-------|
| `check_turn.sh` | چک turn container: port mapping، status، active conf، logs |
| `p04_audit.sh` | P0-4 Socket.IO audit |
| `p04_verify.sh` | P0-4 verify |
| `audit_turn.py` | TURN config audit |
| `patch_rendercfg.py` / `patch_rendercfg2.py` | Render config patches |
| `patch_server_turn.js` | Server TURN patch |
| `make_pbx_config.js` | PBX config generator |

### دستورات حیاتی (Runbook)

```bash
# Health Check کامل
ssh root@192.168.250.42 "
  docker ps --format '{{.Names}} {{.Status}}' | grep -E 'callcenter|ymax'
  curl -s http://127.0.0.1:3006/health | jq
  docker logs callcenter-web-app-1 --tail 10
"

# P0 Test Matrix
# P0-1
curl -s http://192.168.250.42:3006/api/widget/config          # → 401
# P0-2
curl -s -H "Authorization: Bearer <token-A>" \
  http://192.168.250.42:3006/api/records/<uniqueid-B>        # → 403/404
# P0-3
for i in {1..6}; do curl -s -X POST http://192.168.250.42:3006/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}'; done
# → 6ام 429
# P0-4: دو browser/tab با tenant مختلف → Socket.IO events leak check

# TURN E2E Retest (بعد از هر تغییر)
curl -s http://127.0.0.1:3006/api/widget/config?tenantId=1 | jq -r '.iceServers[0].urls,.iceServers[0].username,.iceServers[0].credential'
# سپس داخل turn container:
turnutils_uclient -t -u "<username>" -w "<credential>" -e 8.8.8.8 -r 3478 192.168.250.42

# Rollback App
docker tag callcenter-web-app:rollback-pre-mahan callcenter-web-app:local
docker compose up -d app

# Rollback PBX
docker tag callcenter-web-pbx:rollback-pre-mahan callcenter-web-pbx:local
docker compose up -d pbx

# Rollback DB (MariaDB)
sed -i 's/CC_DB_DRIVER=pg/CC_DB_DRIVER=mysql/' /opt/callcenter-web/.env
sed -i 's/DB_PORT=5432/DB_PORT=3306/' /opt/callcenter-web/.env
docker compose up -d db
docker compose up -d app
curl http://127.0.0.1:3006/health  # باید {"db":"ok"} بدهد
```

---

## ۱۰. Constraints (محدودیت‌های سخت — PO Mandated)

1. **هیچ Write روی سرور بدون تایید PO**
2. **Backup قبل از هر delete/write**
3. **Restart فقط وقتی تماس فعال نیست**
4. **هیچ secret در چت/رپورت** (TURN_SECRET، DB passwords، JWT keys)
5. **re-run `docker/init.sh` ممنوع**
6. **برند: Ymax / وای‌مکس** (هرگز FiruzoCall، Furizo، و غیره)

---

## ۱۱. Next Action برای تو (Agent بعدی)

> **منتظر PO approval برای شروع P0-1/2 هستیم.**

وقتی PO approval آمد:
1. `mcp__ccd_turn__update_plan` با scope `continuation` برای P0-1
2. Backup pre-P0 (commands بالا)
3. Edit `backend/server.js` برای P0-1 guard
4. Deploy، health check، test P0-1
5. CHANGELOG entry
6. Repeat برای P0-2، P0-3، P0-4

**فایل‌های کلیدی برای ویرایش P0:**
- `backend/server.js` (lines ~237-261 widget config, ~records endpoint)
- `backend/runtime.js` (Socket.IO middleware)
- `backend/lib/loginguard.js` (جدید برای P0-3)

---

*Generated: 2026-11-27 | For agent handoff | All paths absolute from project root `C:\Users\someBody\Desktop\voip-centos\`*