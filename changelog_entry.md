
### [2026-11-22 ~16:00] V1 hardening و بازسازی PBX (مسیر Ymax)
- عامل/نفر: Agnes (Claude) — سشن اینجا
- دامنه: سرور (تولیدی)
- تغییرات:
  1. بازنویسی compose.yaml: حذف db service (MariaDB)، رفع خطای YAML خط 152، networks: {}
  2. استارت app container (healthy) + حذف orphan db container (--remove-orphans)
  3. نصب PostgreSQL ۱۶ روی هاست + systemd unit (/etc/systemd/system/postgresql-16.service) — enabled و started، پورت ۵۴۳۲
  4. ثبت migrations ۰۰۱/۰۰۲/۰۰۳ در جدول migrations (UNIQUE constraint روی filename اضافه شد)
  5. restart app container → health {"status":"ok","db":"ok"}
  6. V1-JWT fail-fast (server.js:38) — applied + restart شد
  7. V1-Rebrand: firuzo → ymax / فیرزو → وای‌مکس (۲۴ فایل لایو)
  8. V1-RTP: ۱۰۰۰۰-۲۹۹۹۹ → ۴۹۱۶۰-۴۹۲۰۰ در asterisk/conf/rtp.conf و asterisk/bundle/etc-asterisk/rtp.conf؛ PBX image rebuild شد و recreate شد → verify’d روی کانتینر زنده (49160-49200)
  9. panels نسخه v1: admin + operator
  10. درخواست cleanup baresip-lab: response — لاب فعال است، حفظ شد (قرار در ورودی ۱۵:۲۵ قبلی)
- مجوز: درخواست صریح PO («هر کاری که مشکل رو حل کن… بدون کسی که می erkenned…» + «۱ و ۲ را انجام بده» + «RTP را دوباره فیکس کن» + «چنج‌لاگ را اجرا کن»)
- برگشت: rotate container/image/diffdb ergänzt
- اخطار (ثبت، بدون دست): ۳ خطای unhandled در لاگ اپ — sum(boolean) (۱۵ بار), curdate() (۳ بار), syntax error at or near "1" — سازگاری MySQL→PG نامکمل، endpoint‌های آماری خراب. فیکس با هماهنگی PO، دست‌نخورده laisse.

---
