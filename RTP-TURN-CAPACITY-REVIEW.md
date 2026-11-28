# بررسی RTP / TURN و ظرفیت PBX

**اولویت:** P0 مسدودکننده برای ارزیابی ظرفیت تماس همزمان  
**سقف:** فقط بررسی — restart / rebuild Asterisk اجرا نمی‌شود.

---

## ۱. RTP range

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| RTP تولیدی | 49160-49200 (40 port) | DOCUMENTED |
| RTP لاب | 22000-22100 | DOCUMENTED |
| RTCP | نامشخص (باشد RTP+1 معمولاً) | UNKNOWN |

**تحلیل ظرفیت:** 40 رنج RTP = حداکثر ۲۰ تماس همزمان (هر تماس ۲ رنج RTP/RTCP). این عدد **استنتاجی** است و باید از PO تایید شود (هرچه workload بیشتر باشد، رنج باید بزرگتر شود).

## ۲. TURN / coturn

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Coturn port | 3478 | DOCUMENTED |
| Relay port range | نامشخص | UNKNOWN |
| UDP/TCP/TLS | نامشخص | UNKNOWN |
| TURN auth / expiration | نامشخص | UNKNOWN |
| Public/private IP handling | نامشخص | UNKNOWN |
| Firewall rules | نامشخص | UNKNOWN |

## ۳. PBX / PJSIP

| مورد | مقدار | طبقه‌بندی |
|------|-------|-----------|
| Asterisk version | 20.21 | DOCUMENTED |
| `max_contacts` operator | 2→1 | DOCUMENTED |
| Impact multiple browser sessions | نامشخص | UNKNOWN |
| Impact multiple devices per operator | نامشخص | UNKNOWN |

> تغییر `max_contacts` ۲→۱ ریشه باگ 603-Decline را حل می‌کند اما ممکن است多设备 registration را محدود کند.

## ۴. نیازهای بررسی (UNKNOWN)
- Expected concurrent calls (از PO)
- WebRTC media routing path
- ICE / NAT behavior
- TURN relay port range و firewall opening
- RTCP behavior

## ۵. Side effects
- رنج کوچک RTP → تماس‌های همزمان زیاد ممکن است media allocation失败 کند.
- `max_contacts=1` → operator با多 browser sessions ممکن است مشکل داشته باشد.

## ۶. Test / Backup
- تست RTP allocation در laboratories (تولیدی disrupt نمی‌شود)
- Bکاپ قبل از هر تغییر rtp.conf / pjsip
