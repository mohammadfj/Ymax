# TURN-RTP-CONFLICT-INVESTIGATION — Ymax 2026-11-27

Mode: read-only. No config/port/code change executed.

---

## 1. The contradiction

`FULL-WORKFLOW-REPORT-2026-11-27.md` §2d claims:
- Coturn relay range: `49160-49200` (min-port/max-port)
- Verified `turnutils_uclient` ALLOCATE returned relay `192.168.250.42:62018`

**62018 is outside 49160-49200.** If coturn was actually using `min-port=49160 max-port=49200`, a verified relay address of `:62018` is impossible. One or more of: the test hit a different coturn instance, the min/max were not honoured at test time, or the reported relay address is inaccurate.

---

## 2. Evidence gathered (live read-only)

### Active rendered coturn config (container `callcenter-web-turn-1`, `/etc/coturn/turnserver.conf`)
- `listening-port=3478`, `listening-ip=192.168.250.42`
- `relay-ip=192.168.250.42`
- `min-port=49160`, `max-port=49200`
- `use-auth-secret`, `static-auth-secret=<REDACTED>`
- No `external-ip` value
- Comment lines use `;` → coturn logs `Bad configuration format: ;`

### coturn process state (host netns, `network_mode: host`)
- **Two `turnserver` processes:**
  - PID 13363 — `turnserver -c /etc/coturn/turnserver.conf --proc-user=turnserver --proc-group=turnserver` (started 2026-11-27 15:12) ← reads the rendered conf
  - PID 17950 — `turnserver -v` (started 2026-11-27 16:27) ← no conf; **defaults apply** (default UDP relay range is **49152-65535**)
- Both bind `:3478` UDP/TCP on the host interfaces (SO_REUSEADDR allows co-bind).

### coturn log
- Relay init completes on 192.168.250.42.
- **65 `ERROR: check_stun_auth: Cannot find credentials of user <…>`** — allocations are being attempted but rejected.
- No `success` / relay-address line found in the sampled log.

### Host listeners (`ss -lnup`)
- `:3478` UDP/TCP listening (turnserver).
- **No UDP listener in 49160-49200** (expected — coturn binds relay ports lazily on successful allocation).
- No evidence 62018 was bound at audit time.

### Asterisk RTP
- `rtpstart=49160`, `rtpend=49200` → **identical 40-port UDP range as coturn relay**, same host netns (`network_mode: host`).

### Firewall
- `firewall-cmd --list-ports` → `dhcpv6-client ssh` only. INPUT default `ACCEPT` (policy ACCEPT, no explicit 3478/49160-49200 ACCEPT rule observed). Whether traffic actually reaches 3478 from LAN/WAN is **UNKNOWN** (no drop counters observed for those ports).

---

## 3. Likely root cause of the 62018 discrepancy (INFERRED)

The default `turnserver -v` instance (PID 17950, no config file) uses coturn's **default relay range 49152-65535**, which would produce a relay port such as `:62018`. If the original E2E test targeted that instance (or the host routed to it), the relay address would be outside 49160-49200. The configured instance (PID 13363) limits relay to 49160-49200.

**This cannot be confirmed without a controlled retest** (out of scope for this read-only audit).

---

## 4. Recommended port-allocation design (do NOT set values yet)

Before choosing new ranges, require from PO:
- Expected concurrent-call ceiling.
- Whether Asterisk media (RTP) and coturn relay must share the same host (both `network_mode: host` now).

Candidate design principles:
1. **Separate** Asterisk RTP from coturn relay ranges to avoid collisions as concurrency rises.
2. Keep both inside the existing host firewall acceptance zone; add explicit ACCEPT rules for both ranges if firewall policy is tightened.
3. Do not pick production ranges without capacity + network analysis (per audit constraints).

Suggested starting point for PO analysis (informational only, not approved):
- Asterisk RTP: keep 49160-49200 OR resize based on expected concurrent calls (40 ports ≈ 20 calls).
- Coturn relay: a distinct range (e.g., 49201-49299) sized to expected concurrent relays, on the same host IP.

---

## 5. Dependency table

| Decision | Owner | Required before |
|----------|-------|-----------------|
| Confirm authoritative turnserver PID | PO + infra | any port-range change |
| Controlled `turnutils_uclient` ALLOCATE retest (capture real relay port + auth result) | PO + infra | declaring TURN verified |
| Choose separated RTP vs TURN ranges | PO | production port changes |
| Add firewall ACCEPT for 3478 + relay range | PO + infra | WAN/LAN TURN reachability |
| Decide `external-ip` / public NAT mapping | PO | internet WebRTC readiness |
