## 2026-11-26 — PJSIP: Single-Source-of-Truth Spool Generator (Option B)

**Agent:** Agnes (Claude) — session `ymax-callcenter`
**Scope:** DB-driven spool generation + auto-provision on `POST /api/tenants` + host-cron pbx reload. Live tenant 1 preserved byte-identical.

### What was built

- **`backend/make_pbx_config.js`** — DB-driven generator. For every *active* tenant it emits three Asterisk files: `pjsip_tenant.conf` / `extensions_tenant.conf` / `queues_tenant.conf`.
  - **Legacy-block preservation:** existing `; ---- Tenant N` blocks in the shared spool are copied verbatim (t1's live password hashes are never clobbered).
  - **DB-only credentials:** new tenants derive endpoint passwords from `tenants.sip_secret` (random 64-hex, set at create) via `Op<ext>-sha256(<ext>:secret)` / `visitor-<tid> sha256(visitor<tid>:secret)`. No global readable env secret.
  - **CRLF→LF normalize** throughout (Asterisk needs LF section headers).
- **`POST /api/tenants`** now generates a `sip_secret` per tenant, then runs `buildSpoolFiles()` in-process and drops a `.reload-pending` marker (best-effort; never 500s the create).
- **Host cron (`*/1`)** runs **`pbx_reload.sh`**: channels-empty guard → promote `.gen` → shared spool → pbx spool volume (LF) → `docker compose restart pbx` (entrypoint re-seeds `/etc` from spool) → verify endpoints.
  - *Why restart, not `module reload res_pjsip.so`:* Asterisk 20 reports reload-success but does **not** re-parse `pjsip.conf`; only new containers pick up new endpoint sections.
- **`compose.override.yaml`** app service mounts `./pbx-spool` (ro), `./pbx-spool.gen`, and `pbx_reload.sh` (ro).
- **Migration `005_add_sip_secret.sql`** adds `tenants.sip_secret` (backfilled via `openssl rand -hex 32`; recorded as migration id=6).

### End-to-end verification (live)

| Check | Result |
|-------|--------|
| generator preserves t1 pjsip/extensions | byte-identical |
| t15/t16 auto-provisioned via `POST /api/tenants` | endpoints + `from-widget` dialplan + queues live after restart |
| stale tenant removal (deleted 15/16) | blocks stripped, pbx back to t1-only |
| live t1 endpoints after restore | `1000–1005`, `1000-t1…1005-t1`, `visitor`, `visitor-1`, `webrtc-tpl` (16 total) |
| support queue | `support` rrmemory, max 15, present |
| channels-empty guard | aborted test passed (no restart with open calls) |
| health | `{"status":"ok","db":"ok"}` PASS |

### Rollback

1. Generator files (`make_pbx_config.js`, `pbxgen` Dockerfile/image) are additive — remove and no live impact.
2. `server.js` hook — restore `/root/backups/server.js.pre-tenants-hook-*` (last pre-hook).
3. Spool/`/etc` are still the source of truth via the entrypoint; the shared spool now holds t1 only, matching the live pbx. Backups of every spool mutation: `/root/backups/pbx-spool-*`.
4. Migration `005` (sip_secret column) is additive; drop only after confirming no code reads it.

**PJSIP single-source-of-truth closed.** Creating a tenant now auto-generates + loads its spool with no manual edits; existing tenants keep their exact legacy credentials.

---

