# Ymax client roadmap

## Done (2026-11-21)
- ✅ Full baresip extraction (code + wiki + API metadata) and consolidation into this monorepo
- ✅ CI: Android APK (3 ABIs, verified), desktop Linux/macOS binaries; Windows iOS in progress/paused
- ✅ Android app: Ymax auth gate launcher (web login → app redirect, `ymax://` deep link), app label Ymax
- ✅ Lab SIP server live on 192.168.250.42 (11 extensions, chat, WS click-to-call backend)

## In progress
- ⏳ Emulator smoke test job (runs on each APK build)
- ⏳ Windows static build (long compile; needs cache/job split)

## Next
- [ ] Phone field test: install APK, register 1001-1010, calls + chat between devices
- [ ] Release APK signing key (PO decision) → signed release builds
- [ ] Web service integration: provision SIP accounts from the Ymax web panel (Express API)
- [ ] Click-to-call widget: deploy `webcall/` behind `wss://` with domain cert + server TURN
- [ ] External access path: Tailscale-first; optional MikroTik UDP port-forward (PO security decision)
- [ ] Ymax branding pass (icons, colors, Persian strings) and Persian translation
- [ ] iOS: library build validated, app-level work postponed (PO decision)
