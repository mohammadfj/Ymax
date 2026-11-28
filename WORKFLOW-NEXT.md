# WORKFLOW-NEXT — مسیر پیشنهادی برای ادامه کار Ymax (۲۰۲۶-۱۱-۲۳)

> این سند ورک‌فلو به‌روزشده بر اساس تمام تغییراتی است که تا ۲۰۲۶-۱۱-۲۳ روی سرور اعمال شده‌اند.
> هدف: مسیر مشخص، قابل اجرا، با criteria gate برای هر مرحله.

---

## مروری بر مسیر تا الان

| فاز | عنوان | وضعیت | نکته کلیدی |
|------|-------|--------|------------|
| ۰ | Baseline audit + IR-۰ | ✅ done | PG روی هاست، MariaDB standby |
| ۱ | V1 hardening (JWT، RTP، Rebrand، compose) | ✅ done | app healthy، health OK |
| ۱b | Mahān QC + SQL translation + PBX fixes | ✅ done | ۴۲/۴۲ QC pass، zero unhandled |
| ۱c | CHANGELOG re-sync | ✅ done | RECONSTRUCTED marker removed |
| ۲ | **P0 Security (CURRENT BLOCKER)** | ⏳ **NEXT** | Prioritized by PO |
| ۳ | تصمیم پایداری DB (PG vs MariaDB) | ⏳ BLOCKED by ۲ | PG currently active |
| ۴ | فاز ۲B معماری نهایی (multi-tenant) | ⏳ BLOCKED by ۳ | ADR-۰۰۱ ready |
| ۵ | فاز ۳-۸ | ⏳ آینده | — |

---

## مرحله فعلی: ۲ — P0 Security Fixes

### اولویت‌ها (۴ مورد)

| ID | Endpoint / Area | Issue | Fix Plan | Test |
|----|-----------------|-------|----------|------|
| **P0-1** | `GET /api/widget/config` | TURN credentials + visitor SIP password در پاسخ public | `auth(["admin","operator"])` guard + visitor creds از config حذف / dynamic token | `curl -I /api/widget/config` → ۴۰۱، با token → ۲۰۰ بدون TURN creds |
| **P0-2** | `GET /api/records/:uniqueid` | IDOR —tenant check ندارد | `qTenant(req.user.tenant_id, ...)` + check owner | cross-tenant uniqueid → ۴۰۳/۴۰۴ |
| **P0-3** | `POST /api/auth/login` | Rate-limit / lockout ندارد | `express-rate-limit` middleware (۵ req/min per IP، lockout ۱۵دقیقه) | brute-force sim → ۴۲۹ after ۵ |
| **P0-4** | `runtime.js` Socket.IO | Broadcast بین tenants | `io.use((socket, next) => { socket.tenant = ...; next(); })` + room names `tenant:<id>:<room>` | دو tenant همزمان → events leak check |

### فایل‌های هدف برای P0
```
backend/server.js       → P0-1, P0-2, P0-3
backend/runtime.js      → P0-4
backend/lib/loginguard.js  (ایجاد/بروزرسانی) → P0-3 middleware
```

### Exit Criteria برای مرحله ۲
- [ ] همه ۴ P0 در production healthy test می‌شوند
- [ ] لاگ app ۲۴ ساعت بدون security alert
- [ ] CHANGELOG با ۴ ورودی P0 آپدیت شده
- [ ] PO sign-off

---

## مرحله بعد: ۳ — تصمیم پایداری DB

### Criteria (باید قبل از فاز ۲B تصمیم بگیریم)
| Option | شرط ادامه | Impact |
|--------|-----------|--------|
| **PG بماند** | `CC_DB_DRIVER=pg` + health OK ۷ روز متوالی | فاز ۲B با RLS (PostgreSQL row-level security) |
| **برگشت MariaDB** | PO explicit approval + rollback test passed | فاز ۲B با app-level tenant scoping (بدون RLS) |

### Rollback Plan (اگر MariaDB)
```bash
# 1. env
sed -i 's/CC_DB_DRIVER=pg/CC_DB_DRIVER=mysql/' /opt/callcenter-web/.env
sed -i 's/DB_PORT=5432/DB_PORT=3306/' /opt/callcenter-web/.env
# 2. docker compose up -d db  (MariaDB standby داریم)
# 3. docker compose up -d app
# 4. health check
curl http://127.0.0.1:3006/health  # باید {"db":"ok"} بدهد
```

### Decision Gate
- **Input**: PO explicit approval + ۷ روز uptime PG (actively monitoring)
- **Output**: `.env` locked + ADR-۰۰۱ finalize

---

## مرحله ۴: فاز ۲B — معماری نهایی (Multi-tenant)

### Pre-reqs (باید قبل از شروع انجام شود)
- [ ] P0ها تمام (مرحله ۲)
- [ ] تصمیم DB نهایی (مرحله ۳)
- [ ] ADR-۰۰۱ finalized

### Deliverables
| مورد | توضیح | Owner |
|------|--------|-------|
| `ARCHITECTURE.md` | Spec کامل: tenant model، DB strategy، Asterisk namespacing، API contracts | Lead agent |
| `backend/middleware/tenantResolver.js` | فعال‌سازی در production (چالش: legacy code paths) | Backend agent |
| DDL multi-tenant | `SCHEMA.sql` — tenants، tenant_id FKs، RLS policies (اگر PG) | DB agent |
| Asterisk namespacing | `PJSIP/<tenant>_1001` pattern، context隔離 | PBX agent |
| Provisioning API | `POST /api/tenants` + auto-provision SIP + DB | Backend agent |

### Test Matrix
| Test | Pass Criteria |
|------|---------------|
| Cross-tenant data isolation | Tenant A data never returned for Tenant B queries |
| Tenant provisioning E2E | `POST /api/tenants` → SIP endpoints + DB rows created < ۳۰s |
| Internal call across tenants | Blocked / segregated by context |
| Health per tenant | `/health?tenant=x` per-tenant status |

---

## مرحله ۵-۸: فازهای آینده (outline فقط)

| فاز | عنوان | Dependency |
|------|-------|------------|
| ۳ | Database Architektur (SCHEMA.sql، RLS، مهاجرت) | فاز ۲B complete |
| ۴ | امنیت و احراز هویت (TURN creds، rate limit، audit log) | فاز ۲B + P0 |
| ۵ | منطق کسب‌وکار SaaS (provisioning، trial، feature gate) | فاز ۴ |
| ۶ | Backend و API (OpenAPI، Socket.IO events) | فاز ۵ |
| ۷ | معماری SDK | فاز ۶ |
| ۸ | پیاده‌سازی، تست، استقرار | همه فازها |

---

## دستورات مرجع برای ایجنت‌های بعدی

### Health Check تمام
```bash
ssh root@192.168.250.42 "
  docker ps --format '{{.Names}} {{.Status}}' | grep -E 'callcenter|ymax'
  curl -s http://127.0.0.1:3006/health | jq
  docker logs callcenter-web-app-1 --tail 10
"
```

### P0 Test Script
```bash
# P0-1
curl -s http://192.168.250.42:3006/api/widget/config
# باید 401 بدهد (بدون token)

# P0-2
curl -s -H "Authorization: Bearer <token-tenant-A>" \
  http://192.168.250.42:3006/api/records/<uniqueid-tenant-B>
# باید 403/404 بدهد

# P0-3
for i in {1..6}; do curl -s -X POST http://192.168.250.42:3006/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}'; done
# ۶ام باید 429 بدهد

# P0-4
# دو browser/tab با tenant مختلف → Socket.IO events leak check
```

### Backup Before P0 Work
```bash
ssh root@192.168.250.42 "
  tar -czf /root/backups/pre-p0-$(date +%F).tgz /opt/callcenter-web/backend/
  pg_dump -h 127.0.0.1 -U callcenter callcenter > /root/backups/pre-p0-pg-$(date +%F).sql
"
```

---

## CHANGELOG Update Template (برای هر P0)
```markdown
### [YYYY-MM-DD HH:MM] P0-X: <title>
- عامل/نفر: <Agent name>
- دامنه: سرور (تولیدی)
- تغییرات:
  1. <file:line> — <what changed>
  2. ...
- تست: <test command + expected output>
- مجوز: PO explicit
- برگشت: <exact rollback commands>
```

---

## Gate Summary

| Gate | Condition | Blocker |
|------|-----------|---------|
| شروع P0 | همیشه | — |
| شروع تصمیم DB | P0ها ۱۰۰٪ pass + PO sign-off | P0 incomplete |
| شروع فاز ۲B | تصمیم DB finalized + ADR-۰۰۱ done | DB decision pending |
| فاز ۳ | فاز ۲B complete + test matrix green | فاز ۲B incomplete |

---

<small>Last updated: 2026-11-23. Next review after P0-1/P0-2 deployment.</small>