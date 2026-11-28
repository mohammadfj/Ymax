# P0-4-SOCKETIO-ISOLATION-PLAN — Ymax 2026-11-27

Mode: plan only. No write executed. Requires explicit PO approval before any code change.

---

## 1. Current state (OBSERVED live)

`runtime.js` Socket.IO:
- `io.use((socket, next) => …)` at line 323 → derives `tid`, joins `tenant:<tid>`, `adminRoom(tid)`, `opsRoom(tid)`, `opChan(tid, ext)` (lines 332-335).
- Rooms are tenant-scoped; events use `io.to(adminRoom(tid))`, `io.to(opChan(tid,ext))`, `socket.to(fullChan(base))` (no bare `io.emit` observed in the sample).
- Latent `|| 1`: ~15 call sites use `c.tenant_id \|\| 1` (e.g. lines 102,153,159,166,177,199,210,212,230,293) — harmless in single-tenant mode today, fail-open into tenant 1 once tenant 2+ exists.
- DM cross-tenant join check deferred to 2B.

## 2. Event map (from code sample)

| Event / target | Scope |
|----------------|-------|
| `admin:t<tid>` | io.to(adminRoom(tid)) |
| `operators:t<tid>` | io.to(opsRoom(tid)) |
| `op:t<tid>:<ext>` | io.to(opChan(tid,ext)) |
| chat `t<tid>:<chan>` | socket.join(fullChan(base)) |
| presence | io.to(opsRoom(tid)).to(adminRoom(tid)) |
| call:new / call:update / call:ended / missed:new / pop:data / queue:members / queue:status | io.to per tenant |

No `io.emit` (global broadcast) observed; all emits are room-scoped by tenant.

## 3. Hardening requirements (for PO approval)
- Remove `|| 1` fail-open on tenant derivation → missing/invalid tenant fails closed.
- Every sensitive event handler enforces tenant + role before emitting into a room.
- Client-triggered events (`chat:join`, `chat:typing`, `presence:ping`) validate `mayUseChat(base)` / tenant membership before room join.
- On disconnect/expired session: leave rooms, require re-auth on reconnect, re-join own rooms only.

## 4. Tests (staging, 2 tenants, test data)
| # | Test | Expected |
|---|------|----------|
| P0-4-1 | Same-tenant event delivery | delivered |
| P0-4-2 | Cross-tenant event (tenant A → tenant B room) | dropped / not delivered |
| P0-4-3 | Invalid credentials | connection refused |
| P0-4-4 | Missing credentials | connection refused |
| P0-4-5 | Unauthorized room join | refused |
| P0-4-6 | Unauthorized event execution | error |
| P0-4-7 | Expired session | disconnect / re-auth |
| P0-4-8 | Reconnection | re-auth + re-join own rooms only |

Note: Socket.IO change disconnects clients — schedule in a maintenance window (0 active calls observed at audit time).

## 5. Backup / rollback
- Backup `backend/runtime.js` before any write.
- Rollback: restore previous runtime.js + `docker compose up -d app`.

**Status: PLAN ONLY. Awaiting PO approval.**
