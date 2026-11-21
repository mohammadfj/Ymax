# Ymax — VoIP client monorepo

Consolidated client-side codebase of the Ymax call-center platform, merged
from upstream mirrors (full git history preserved via subtree merges).

> فارسی: ریپوی جامع کلاینت‌های VoIP پروژه یمکس — ساختار زیر، CI تلفیقی، تاریخچه کامل محفوظ.

## Layout

| Path | What | Upstream |
|---|---|---|
| `core/baresip/` | SIP user-agent core (BSD-3-Clause) | baresip/baresip @ main |
| `android/baresip-studio/` | Android client app (Kotlin+JNI, Android 9+) | juha-h/baresip-studio @ master |
| `android/libbaresip-android/` | NDK cross-compile of baresip/re/rem deps | juha-h/libbaresip-android @ master |
| `archive/baresip-android/` | Stale upstream android repo (2022) — reference only | baresip/baresip-android |

## CI (GitHub Actions)

| Workflow | Produces | Trigger |
|---|---|---|
| `android-apk.yml` | debug + release APKs (3 ABIs), verified via aapt/apksigner | manual or `v*` tags |
| `desktop.yml` | static baresip binaries for Linux/macOS | manual or `v*` tags |
| `windows-static.yml` | static baresip.exe (Windows) | manual or `v*` tags |
| `ios-lib.yml` | iOS simulator static libs (no TLS) — paused per PO | manual only |

## Android build flow

1. `libbaresip-android` downloads and cross-compiles openssl/opus/amr/codec2/g722/g7221/zrtpcpp/re/baresip for each ABI into `android/baresip-studio/distribution/`
2. gradle `assembleDebug` + `assembleRelease` in `android/baresip-studio`
3. APK verification (aapt badging, apksigner on debug) then artifact upload

## Releases & docs

- **Releases**: https://github.com/mohammadfj/Ymax/releases — `v0.1.0-lab` = installable debug APK + Persian user manual (see [RELEASES.md](RELEASES.md))
- [docs/user-manual-fa.html](docs/user-manual-fa.html) — راهنمای کاربر فارسی
- [docs/LAB-SERVER.md](docs/LAB-SERVER.md) — lab SIP server reference (extensions, ports, rollback)
- [docs/ROADMAP.md](docs/ROADMAP.md) — what's done / in progress / next

Note: upstream mirrors (`mohammadfj/baresip`, `baresip-studio`, `libbaresip-android`, `baresip-android`, `zz-wiki-test`) are **archived** — this monorepo is the single source of truth.

## Open items

- Release APK signing key: not configured yet (PO decision pending)
- Web service provisioning integration + `wss://` for click-to-call
- External (out-of-LAN) access: Tailscale-first; MikroTik UDP port-forward needs PO security decision
