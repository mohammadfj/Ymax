# P0 — تست‌های امنیتی (matrice)

**قاعده:** تست در staging / isolated environment. Production به عنوان محیط تست عمومی استفاده نمی‌شود. brute-force روی administrator واقعی ممنوع است.

---

## P0-1 — `/api/widget/config`
| # | Test | Method | Expected | Env |
|---|------|--------|----------|-----|
| P0-1-1 | فاقد creds در پاسخ عمومی | GET | ۲۰۰، fields عمومی فقط | Staging |
| P0-1-2 | creds با token admin | GET + token | ۲۰۰، creds کامل | Staging |
| P0-1-3 | ویجت ناشناس تماس می-start کند | UI simulate | تماس berhasil | Staging |

> `curl -I` (HEAD) معتبر نیست؛ استفاده از `curl -s` (GET) برای valid کردن پاسخ واقعی.

## P0-2 — `GET /api/records/:uniqueid`
| # | Test | Expected | Env |
|---|------|----------|-----|
| P0-2-1 | Token Tenant A → uniqueid Tenant B | ۴۰۴ / ۴۰۳ | Staging (test data isolated) |
| P0-2-2 | بدون token | ۴۰۱ | Staging |
| P0-2-3 | Token Tenant A → uniqueid Tenant A | ۲۰۰ + داده | Staging |
| P0-2-4 | uniqueid ناموجود | ۴۰۴ | Staging |

## P0-3 — `POST /api/auth/login`
| # | Test | Expected | Env |
|---|------|----------|-----|
| P0-3-1 | ۵ درخواست ناموفق از یک IP | ۴۲۹ از ۶ام | Isolated (test account) |
| P0-3-2 |Lockout ۱۵ دقیقه | ۴۲۹ تا expiracy | Isolated |
| P0-3-3 | همان پیام برای username نادرست / password نادرست | account enumeration Prevention | Staging |
| P0-3-4 | Credentials درست | ۲۰۰ | Staging |

> brute-force روی حساب administrator واقعی ممنوع است.

## P0-4 — Socket.IO tenant isolation
| # | Test | Expected | Env |
|---|------|----------|-----|
| P0-4-1 | Same-tenant event delivery | delivered | Staging (2 clients same tenant) |
| P0-4-2 | Cross-tenant isolation | dropped | Staging (2 tenants) |
| P0-4-3 | Invalid credentials | connection refused | Staging |
| P0-4-4 | Missing credentials | connection refused | Staging |
| P0-4-5 | Unauthorized room join | refused | Staging |
| P0-4-6 | Unauthorized event execution | error | Staging |
| P0-4-7 | Expired session | disconnect / re-auth | Staging |
| P0-4-8 | Reconnection | re-auth + re-join own rooms only | Staging |

## Exit Criteria
همه تست‌ها در Staging سبز. Production تست فقط با test data و timezone Maintenance.
