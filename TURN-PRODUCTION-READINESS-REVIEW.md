# TURN-PRODUCTION-READINESS-REVIEW — Ymax 2026-11-28 (updated)

Status: REST auth VERIFIED WORKING end-to-end. R2/R3/R5 resolved. R4 (public reachability) deferred.

---

## 1. Root cause (R2) — RESOLVED

coturn 4.6.1: `lt-cred-mech` and `use-auth-secret` are **mutually exclusive**.
The template shipped both, breaking REST auth ("Cannot find credentials of user <>").
Fix: removed `lt-cred-mech` from both the container boot template
(`/opt/callcenter-templates/...` and `/opt/callcenter-web/nginx/turnserver.conf.tmpl`)
and the rendered `/etc/coturn/turnserver.conf`. Config is now `use-auth-secret` +
`static-auth-secret` only.

## 2. Verified (OBSERVED live, 2026-11-28)

| Check | Result |
|-------|--------|
| Single coturn instance | PASS — PID 22666 only on 192.168.250.42:3478 UDP/TCP |
| UDP STUN binding | PASS — 88-byte success response |
| REST ALLOCATE (`turnutils_uclient -W`) | PASS — relay ports 49160/49161/49166 in 49160-49200 |
| Relay port range | PASS — coturn min/max-port 49160-49200, bound |
| Firewall | PASS — 3478/tcp+udp, 49160-49200/udp from 192.168.250.0/24 |
| App credential minting | PASS — `HMAC-SHA1(TURN_SECRET,"<ts>:t<tid>")` base64, matches coturn REST check |

> `-W <secret>` makes turnutils derive the key internally; `-w <pass>` uses the legacy
> long-term `SHA1(pass:realm:user)` formula and will fail under REST — do not use `-w`.

## 3. Port separation (R3) — RESOLVED

| Component | Before | After |
|-----------|--------|-------|
| Asterisk RTP (`rtp.conf`) | 49160-49200 | 50000-51000 |
| coturn relay (`min-port`/`max-port`) | 49160-49200 | 49160-49200 (unchanged) |

No overlap. Updated in container template + live volume + host source
(`/opt/callcenter-web/asterisk/conf/rtp.conf`); asterisk restarted at 0 active calls.
RTP ports only bind during active calls; SIP (port 5000) is separate and unaffected.

## 4. Public reachability (R4) — DEFERRED (PO decision)

Currently LAN-only: `PUBLIC_IP==CC_HOST_IP==192.168.250.42`, so render strips
`external-ip` (coturn rejects an identity mapping). Widget ICE uses
`pubIp=lanIp=192.168.250.42`. To serve visitors off-LAN, PO must provide a
public IP + NAT mapping, then set `CC_TURN_PUBLIC_IP` and add an
`external-ip=public/lan` line. Out of scope until decided.

## 5. Credential rotation (P0-1 step 4)

`TURN_SECRET`/`TURN_PASS` unchanged since 2026-11-27. P0-1 leak was the
anonymous `/api/widget/config` body — now fixed at the source (no leak path).
Rotation optional: generate new `TURN_SECRET` in `.env` + re-render; app-minted
creds are short-lived (unix-ts) so no long-lived creds to invalidate. Deferred
until PO confirms a cred was ever actually served to an unauthenticated party.

## 6. Outstanding / not done

- R4 public IP + NAT mapping (needs a public address from PO).
- R8 MariaDB standby: absent; PG retained (restore feasibility proven).
- P0-4 Socket.IO: `|| 1` fail-open in `req.user.tenant_id || 1` remains across
  records/Socket.IO/widget paths — latent until a 2nd tenant; must become
  fail-closed before WS-2B.
