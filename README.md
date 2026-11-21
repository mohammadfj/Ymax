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

## Open items

- Release APK signing key: not configured yet (PO decision pending)
- SIP server: lab on Ymax server pending PO password/permission (see SERVER-CHANGELOG in voip-centos-z)
