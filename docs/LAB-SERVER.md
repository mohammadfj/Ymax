# Lab SIP server — quick reference (admin)

Live on `192.168.250.42` since 2026-11-21. Full change log: `/opt/SERVER-CHANGELOG.md` (server) and `voip-centos-z/docs/handover/SERVER-CHANGELOG.md` (workspace).

> **Production context** (from `/opt/callcenter-web/README.fa.md`): the server also runs the
> production call-center **v2.0.3** on `call.ymax.ir` — Asterisk PBX with WSS-only transports,
> extensions **1000-1005 + visitor**, IVR/queue/recording/survey, Node/Express backend with JWT
> API, and the mature WebRTC `widget.js`. The lab below is a separate isolated container;
> lab extensions (1001-1010, port 15060) intentionally do NOT touch production. Do not confuse
> the two numbering ranges — port 15060 always means lab.

| Item | Value |
|---|---|
| Container | `baresip-lab-asterisk` (image `callcenter-web-pbx:local` = Asterisk 20.21, entrypoint bypassed) |
| Files | `/opt/baresip-lab/` (compose + asterisk configs, read-only mounts) |
| SIP | UDP 15060 → container 5060, bound to LAN + Tailscale IPs only |
| RTP | UDP 22000-22100 |
| WebSocket | TCP 8089 → container 8088 (click-to-call) |
| Extensions | 1001-1010 operators (`Lab-Pass-1001`…), 2001 web visitor, 600 echo, 999 audio |
| Chat | SIP MESSAGE routed via dialplan `MessageSend` |
| Rollback | `cd /opt/baresip-lab && docker compose down -v && rm -rf /opt/baresip-lab` |

Status commands:
```bash
docker exec baresip-lab-asterisk asterisk -rx "pjsip show endpoints"
docker exec baresip-lab-asterisk asterisk -rx "pjsip show contacts"   # registered phones
```

Known limitation: Docker Hub and mirrors are 403 from this server (geo-block) — lab reuses the existing production image; building a dedicated image needs an accessible registry later.
