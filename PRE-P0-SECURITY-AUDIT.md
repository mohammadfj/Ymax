# PRE-P0-SECURITY-AUDIT — گزارش პირველიアムنیتی Ymax
**تاریخ:** 2026-11-24  
**سقف مجاز:** فقط خواندنی (read-only). هیچ تغییر در Production، دیسک، دیتابیس یا کانتینر اعمال نمی‌شود.  
**تایید:** پس از بررسی این سند وоставن سندی است، نیاز به تایید صریح PO است.

---

## ۱. متدولوژی و طبقه‌بندی یافتها

هر یافت با یکی از ये situées نشانه‌گذاری می‌شود:

| برچسب | معنای دقیق |
|--------|-------------|
| **DOCUMENTED** | ثبت شده در اسناد (AGENT-READABLE-STATUS.md / WORKFLOW-NEXT.md / CHANGELOG). هنوز به صورت زنده تایید نشده. |
| **OBSERVED** | در این سشن از کد / فایل罶 روی سرور مشاهده شد. |
| **INFERRED** | استنتاج منطقی از اسناد؛ مستقیماً تایید نشده. |
| **UNKNOWN** | اطلاعات در دسترس نیست؛ نیاز به بررسی زنده. |
| **CONFLICTING** | دو سند با هم متضاد هستند. |

> ⚠️ این سشن accesses زنده به سرور ندارد (دستور ssh توسط کاربر متوقف شد). بیشتر موارد زیر **DOCUMENTED** است نه **OBSERVED**.

---

## ۲. پایه technischen فعلی (Current Technical Baseline)

| مورد | وضعیت | منبع | توضیح |
|------|--------|-------|-------|
| OS | DOCUMENTED | AGENT-READABLE-STATUS | CentOS 7.9 (EOL) @ 192.168.250.42 / mahan.opss.ir / Tailscale 100.112.160.17 |
| دیتابیس فعال | DOCUMENTED | AGENT-READABLE-STATUS | PostgreSQL 16 روی هاست 127.0.0.1:5432، systemd unit فعال |
| `CC_DB_DRIVER` | DOCUMENTED | AGENT-READABLE-STATUS | `pg`، `DB_PORT=5432` در `.env` |
| MariaDB کانتینر | DOCUMENTED | AGENT-READABLE-STATUS | `callcenter-web-db-1` Up healthy روی 3306 به عنوان standby |
| compose.yaml | DOCUMENTED | AGENT-READABLE-STATUS | `db` service حذف شده؛ `network_mode: host` |
| App | DOCUMENTED | AGENT-READABLE-STATUS | Node 20 / Express / Socket.IO، v3.0.1، پورت 3006، health `{"status":"ok","db":"ok"}` |
| PBX | DOCUMENTED | AGENT-READABLE-STATUS | Asterisk 20.21، کانتینر pbx healthy |
| Nginx | DOCUMENTED | AGENT-READABLE-STATUS | 80/443 TLS |
| Coturn | DOCUMENTED | AGENT-READABLE-STATUS | پورت 3478 |
| RTP range | DOCUMENTED | AGENT-READABLE-STATUS | 49160-49200 (تولیدی) |
| SIP Lab | DOCUMENTED | AGENT-READABLE-STATUS | `ymax-sip-lab-asterisk` (منفصل از تولیدی) |
| Bکاپ‌ها | DOCUMENTED | AGENT-READABLE-STATUS | `/root/backups/...`، crontab `/root/cc-cron` |
| Multi-tenancy | DOCUMENTED | AGENT-READABLE-STATUS | فعال نیست (single-tenant)؛ tenantResolver وجود دارد |
| رول‌بک app/pbx | DOCUMENTED | AGENT-READABLE-STATUS | ایمیج‌های `rollback-pre-mahan` |

### یافتهای کلیدی (CONFLICTING / UNKNOWN)
- **MariaDB standby (CONFLICTING):** کانتینر MariaDB هنوز Up است در حالی که सेवा حذف شده از compose. неизвест است که آیا دیتای MariaDB هم‌زمانی با PG دارد یا خیر؛ **فقط وجود کانتینر ثابت‌کننده standby هم‌زمان نیست**.
- **اتصال App→PG (UNKNOWN):** رشته اتصال دقیق (connection string، pooling، SSL) در `.env` بررسی نشده.
- **شبکه کانتینرها (UNKNOWN):** `network_mode: host` در compose؟ نیاز به cat زنده از compose.yaml.
- **کلید JWT (UNKNOWN):** مقدار `JWT_SECRET` وoldown زمان توکن در `.env` نامشخص است (راز – چاپ نشود).

---

## ۳. بررسی چهار مورد P0 (دونها اجرای تغییر)

### P0-1 — `GET /api/widget/config` — افشای credential عمومی
|omération | مقدار |
|----------|-------|
| موقعیت | `backend/server.js` (دokuumented) |
| مشکل | TURN credentials + رمز SIP visitante در پاسخ عمومی |
| راه حل پیشنهادی در WORKFLOW | `auth(["admin","operator"])` |
| خطر | این Garde može ویجت بازدیدکننده ناشناس را شکست دهد. |

**یافتها (یکत्रه):**
- ویجت عمومی باید برای بازدیدکننده بدون احراز هویت کار کند → `auth(["admin","operator"])` مستقیماً نمی‌تواند به کار بیاید بدون جداسازی فیلدهای عمومی از حساس.
- نیاز به بررسی: consumedいた کرنے वाले frontend (widget.js)، تولید TURN credentials (استاتیک یا داینامیک)، reused بین بازدیدکنندگان یا نه.

### P0-2 — `GET /api/records/:uniqueid` — IDOR
|메이션 | مقدار |
|-------|-------|
| مشکل | بدون tenant check / مالکیت رکورد |
| راه حل پیشنهادی | `qTenant(req.user.tenant_id)` + مالکیت |

**یافتها:** Location filling `req.user`، derive tenant، ownership relationship، broadcast endpoints هم‌مسئله — بررسی نشده (UNKNOWN).

### P0-3 — `POST /api/auth/login` — Rate limiting
| 상황 | مقدار |
|-------|-------|
| مشکل | بدون rate-limit / lockout |
| پیشنهادی | ۵ req/min per IP، lockout ۱۵ دقیقه |

**چुनایی‌ها:** Express version، trust proxy، تشخیص IP واقعی پشت Nginx، shared IP، account enumeration — نامشخص (UNKNOWN).

### P0-4 — Socket.IO tenant isolation
|상은 | مقدار |
|-----|-------|
| مشکل | Broadcast بین tenants |
| پیشنهادی | `io.use(...)` + room names `tenant:<id>:<room>` |

**نکته:**だけ `io.use` کافی نیست؛ احراز هویت اتصال،-derived tenant، همه `io.emit`/`broadcast`، room joining، event-level authorization — بررسی نشده.

---

## ۴. 냄새های امنیتی اضافی (แจ้ง)

| # | مورد | کد Path | وضعیت |
|---|------|---------|-------|
| A | Update-then-Insert races (`/api/visitor/announce`, survey upsert) | server.js:240, runtime.js:221 | DOCUMENTED — concurrency risk |
| B | `translateSQL` balanced-paren scanners | lib/db.js | DOCUMENTED — applied |
| C | `max_contacts` 2→1 PJSIP | PBX | DOCUMENTED — impact multi-device unknown |
| D | coturn creds | — | UNKNOWN |

---

## ۵. دستورات ممنوعه در این سسن (AGENT-PROTOCOL)

1. هیچ Write روی سرور بدون تایید PO.
2. Backup قبل از هر delete/write.
3. Restart فقط وقتی تماس فعال نیست.
4. هیچ secret در چت / گزارش.
5. `docker/init.sh` اجرا نشود.
6. برند Ymax حفظ شود.
7. بازیابی / تغییر دیتابیس اجرا نشود.

---

## ۶. deliverables بعدی (پایان این سسن)

فایل‌های ۱-۹ در همین دایرکتوری نوشته می‌شوند. فایل ۱۰ (PRODUCTION-CHANGE-PLAN) Plan نهایی است. سپس توقف و منتظر تایید PO.
