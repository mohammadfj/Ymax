# P0-1 — Widget Configuration Remediation (باگ کشف‌شده + بررسی)

**تاریخ:** 2026-11-26  
**وضعیت:** **باگ فعال روی سرور — `/api/widget/config` در حال حاضر 503 برمی‌گرداند.**  
**سقف:** فقط بررسی و Plan.

---

## ۱. وضعیت فعلی (OBSERVED 2026-11-26)

### Endpoint
| مورد | مقدار |
|------|-------|
| Path | `GET /api/widget/config` (server.js:223) |
| بدون host معتبر | `503 {"error":"tenant config unavailable"}` |
| با Host = 192.168.250.42 | `503 {"error":"tenant config unavailable"}` |

### ریشه (root cause)
- `getTenantConfig(1)` در `lib/db.js:741` در حال حاضر **crash می‌کند**: `Expected property name or '}' in JSON at position 1`.
- خطای `JSON.parse` در یکی از ستون‌های `remote_db_config` یا `settings` رخ می‌دهد (دو‌ستون JSON-string).
- `tenant row id=1` مقدار `remote_db_config` یا `settings` دارای JSON ناسالم است.
- `server.js` خطای `getTenantConfig` را با `.catch(() => null)` catch می‌کند → endpoint به جای 500، 503 برمی‌گرداند.

### وضعیت قبل از باگ
- `widget/config` از خط ۲۲۳ تا ۲۶۵ در `server.js` (نسخه ۲۰۲۶-۱۱-۲۵) را می‌خواند و credentials را از tenant row می‌سازد.
- `cache-control: public, max-age=120` روی endpoint تنظیم شده است.

## ۲. باگ فعلی — نیاز به رفع (BLOCKER)

```bash
# برای رفع: بررسی مقادیر JSON در tenant row
PGPASS=$(grep '^DB_PASS' /opt/callcenter-web/.env | cut -d= -f2)
export PGPASSWORD=$PGPASS
/usr/local/pgsql16/bin/psql -h 127.0.0.1 -U callcenter -d callcenter \
  -c "SELECT remote_db_config, settings FROM tenants WHERE id=1;"
```

پس از رفع JSON ناسالم، endpoint باید 200 برگرداند.

## ۳. بعد از رفع باگ — بررسی security posture

### Fیلد credential که در پاسخ endpoint قرار می‌گیرد
| Field | منبع | Sensitivity |
|-------|------|-------------|
| `turn.user` | tenant row | حساس (TURN credential) |
| `turn.pass` | tenant row | حساس (TURN credential) |
| `turn.publicIp` | tenant row | حساس (media endpoint) |
| `turn.lanIp` | tenant row | حساس (media endpoint) |
| `sip.visitorPass` | tenant row | حساس (SIP authenticate password) |

### آیا endpoint در حال حاضر public است؟
- endpoint **بدون auth guard** است (`auth(["admin","operator"])` روی آن قرار ندارد).
- پس از رفع باگ، هر کسی با ارسال Host header درست می‌تواند credentials را دریافت کند.

## ۴. طرح remediation (بدون اجرا)

### گزینه A — جداسازی فیلدهای عمومی از حساس (پیشنهادی)
1. endpoint `GET /api/widget/config` فقط فیلدهای عمومی را برگرداند:
   - `ws_url`, `dial_ext`, `brand`, `support_phone`
   - `ice_servers` بدون `username`/`credential`
2. credentials (TURN + SIP) در یک endpoint جدا و scoped به token:
   - `GET /api/widget/session-credentials` با token کوتاه‌مدت
3. `widget.js` را به‌روزرسانی کنید تا با token جدید به endpoint حساس دسترسی داشته باشد.

### گزینه B — Auth guard روی کل endpoint (ریسک‌پذیر)
- `auth(["admin","operator"])` روی `/api/widget/config`
- **ریسک:** ویجت عمومی برای بازدیدکننده ناشناس قطع می‌شود (نمی‌تواند credentials را بگیرد)
- فقط در صورتی قابل قبول است که `widget.js` به‌روزرسانی شده باشد.

### گزینه C — Credentials dynamic (بهترین برای multi-tenant)
- سرور private key را نگه می‌دارد
- در `POST /api/widget/visitor-session` (endpoint جدید)، یک token محدود lifespan صادر می‌شود
- TURN credentials در همان پاسخ token (short TTL)
- SIP password با TTL کوتاه و scoped به یک بازدیدکننده/tenant

## ۵. Side effects / Dependencies
- `widget.js` باید با endpoint جدید هماهنگ شود (تغییر در frontend).
- `cache-control: public, max-age=120` روی endpoint عمومی credentials **ممنوع** است (120s cache credentials در public!).

## ۶. Test cases
| # | Test | Expected |
|---|------|----------|
| P0-1-A | `GET /api/widget/config` بعد از رفع باگ، بدون token | ۲۰۰، فقط fields عمومی، credentials خالی |
| P0-1-B | `GET /api/widget/config` با token admin | ۲۰۰، credentials کامل |
| P0-1-C | `GET /api/widget/session-credentials` با token کوتاه | ۲۰۰، TURN creds + SIP pass |
| P0-1-D | ویجت ناشناس — credentials از endpoint عمومی | ۴۰۱ / credentials خالی |

## ۷. Backup / Rollback
- Backup قبل از تغییر: `tar -czf /root/backups/pre-p0-1-$(date +%F).tgz /opt/callcenter-web/backend/`
- Rollback: بازگردانی server.js قبلی + recreate app

## ۸. اقدامات فوری (قبل از P0-1)
- **رفع باگ JSON.parse** — این باگ endpoint را قطع کرده است و باید قبل از هر security remediation رفع شود.
- بعد از رفع، security posture را طبق گزینه A یا C طراحی کنید.
