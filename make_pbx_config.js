// make-pbx-config.js — DB-driven PJSIP/extensions/queues spool generator.
//
// Strategy: PRESERVE existing per-tenant blocks verbatim (so a live tenant's
// legacy password hashes are never clobbered), and APPEND a freshly generated
// block (with deterministic SHA-256-hashed credentials derived from the DB
// pattern) for any ACTIVE tenant that has no block yet.
//
// Writes three files to process.env.CC_PBX_SPOOL_OUT_DIR (a host bind the app
// can write to):  pjsip_tenant.conf / extensions_tenant.conf / queues_tenant.conf
// A host wrapper copies them into the pbx spool + triggers `core reload`.
//
// Usage:  node make-pbx-config.js
// Exports: buildSpoolFiles() -> {pjsip, extensions, queues}

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { q } = require("./lib/db");

const sha256 = (s) => crypto.createHash("sha256").update(String(s)).digest("hex");

// Per-tenant secret (tenants.sip_secret, random 64-hex, set at tenant creation).
// New tenants derive endpoint hashes from it (DB-only, NOT the global CC_OP_SIP_PASS
// env, which any operator could read). Per-extension distinctness is preserved.
function opPassword(t, ext) {
  const secret = t.sip_secret || `t${t.id}-fallback`; // fallback only if column missing
  return `Op${ext}-${sha256(`${ext}:${secret}`)}`;
}
function visitorPassword(t) {
  const secret = t.sip_secret || `t${t.id}-fallback`;
  return sha256(`visitor${t.id}:${secret}`);
}

function splitTenantBlocks(fileText) {
  // Split on the "; ---- Tenant N" marker lines. Returns { preserved: Map<tid, text>, all: text }
  // Normalize CRLF -> LF up-front so preserved blocks are always LF (Asterisk needs LF).
  const lines = (fileText || "").replace(/\r\n/g, "\n").split("\n");
  const blocks = new Map();
  let cur = null;
  let curTid = null;
  for (const ln of lines) {
    const m = ln.match(/^; ---- Tenant (\d+)/);
    if (m) {
      if (curTid !== null) blocks.set(curTid, cur.join("\n"));
      curTid = m[1];
      cur = [ln];
    } else if (cur !== null) {
      cur.push(ln);
    }
  }
  if (curTid !== null) blocks.set(curTid, cur.join("\n"));
  return blocks;
}

function genTenantPjsip(t) {
  const tid = t.id;
  const lines = [`\n; ---- Tenant ${tid} (per-namespace) ----`,
    `[visitor-${tid}](webrtc-tpl)`,
    `context=from-widget-${tid}`,
    `auth=visitor-auth-${tid}`,
    `aors=visitor-aor-${tid}`,
    ``,
    `[visitor-auth-${tid}]`,
    `type=auth`,
    `auth_type=userpass`,
    `username=visitor-${tid}`,
    `password=${visitorPassword(t)}`,
    ``,
    `[visitor-aor-${tid}]`,
    `type=aor`,
    `max_contacts=30`,
    `remove_existing=no`,
    `qualify_frequency=0`];
  for (let ext = t.sip_ext_start; ext <= t.sip_ext_end; ext++) {
    lines.push(
      ``,
      `[${ext}-t${tid}](webrtc-tpl)`,
      `context=from-internal-${tid}`,
      `auth=auth-t${tid}-${ext}`,
      `aors=aor-t${tid}-${ext}`,
      ``,
      `[auth-t${tid}-${ext}]`,
      `type=auth`,
      `auth_type=userpass`,
      `username=${ext}`,
      `password=${opPassword(t, ext)}`,
      ``,
      `[aor-t${tid}-${ext}]`,
      `type=aor`,
      `max_contacts=1`
    );
  }
  return lines.join("\n") + "\n";
}

function genTenantExtensions(t) {
  const tid = t.id;
  const qn = t.queue_name;
  const lines = [
    `\n; ---- Tenant ${tid} contexts ----`,
    `[from-widget-${tid}]`,
    `exten => _X.,1,NoOp(T${tid} visitor call)`,
    ` same => n,Set(CHANNEL(language)=fa)`,
    ` same => n,Answer()`,
    ` same => n,MixMonitor(\${UNIQUEID}.wav)`,
    ` same => n,UserEvent(CallStart,UniqueID:\${UNIQUEID},Caller:\${CALLERID(num)},Tid:${tid})`,
    ` same => n,Playback(custom/welcome)`,
    ` same => n,Playback(custom/queue-wait)`,
    ` same => n,Queue(${qn},tT,,,600)`,
    ` same => n,UserEvent(CallEndBridge,UniqueID:\${UNIQUEID},QStatus:\${QUEUESTATUS},Tid:${tid})`,
    ` same => n,Playback(custom/survey-intro)`,
    ` same => n,Read(RATING,beep,1,,2,7)`,
    ` same => n,GotoIf($["x\${RATING}" = "x"]?unrated${tid})`,
    ` same => n,Goto(rated${tid})`,
    ` same => n(unrated${tid}),Set(RATING=0)`,
    ` same => n(rated${tid}),UserEvent(CallSurvey,UniqueID:\${UNIQUEID},Rating:\${RATING},Tid:${tid})`,
    ` same => n,Playback(custom/survey-thanks)`,
    ` same => n,Hangup()`,
    ``,
    `[from-internal-${tid}]`,
    `exten => _${t.sip_ext_start}-${t.sip_ext_end},1,NoOp(T${tid} op dial \${EXTEN})`,
    ` same => n,Dial(PJSIP/\${EXTEN}-t${tid},30)`,
    ` same => n,Hangup()`
  ];
  return lines.join("\n") + "\n";
}

function genTenantQueue(t) {
  const tid = t.id;
  const lines = [
    `\n; ---- Tenant ${tid} support queue (gen) ----`,
    `[${t.queue_name}]`,
    `strategy=${t.queue_strategy || "rrmemory"}`,
    `timeout=${t.queue_timeout ?? 22}`,
    `retry=5`,
    `wrapuptime=${t.queue_wrapuptime ?? 4}`,
    `maxlen=${t.queue_maxlen ?? 15}`,
    `musicclass=fa-relax`,
    `announce-position=no`,
    `announce-holdtime=no`,
    `servicelevel=60`
  ];
  return lines.join("\n") + "\n";
}

async function buildSpoolFiles() {
  // Default to the host-shared spool dir so the running app (which calls this in-process)
  // works without extra env; CC_PBX_SPOOL_IN_DIR/OUT_DIR override for one-shot runs.
  const inDir = process.env.CC_PBX_SPOOL_IN_DIR || "/opt/callcenter-web/pbx-spool";
  const outDir = process.env.CC_PBX_SPOOL_OUT_DIR || "/opt/callcenter-web/pbx-spool.gen";
  const readMaybe = (name) => {
    try { return fs.readFileSync(path.join(inDir, name), "utf8"); } catch { return ""; }
  };
  // Normalize CRLF -> LF everywhere: pjsip.conf / extensions.conf must be LF for
  // Asterisk to parse section headers; the shared spool may carry CRLF.
  const readL = (name) => (readMaybe(name) || "").replace(/\r\n/g, "\n");
  const pjsipExisting = readL("pjsip_tenant.conf");
  const extExisting   = readL("extensions_tenant.conf");

  const pBlocks = splitTenantBlocks(pjsipExisting);
  const eBlocks = splitTenantBlocks(extExisting);

  const active = await q("SELECT * FROM tenants WHERE active=1 ORDER BY id");

  let pjsip = "\n";
  let exts  = "\n";
  let queues= "\n";

  for (const t of active) {
    const tid = String(t.id);
    // Preserve existing block verbatim if present; otherwise generate.
    if (pBlocks.has(tid)) pjsip += pBlocks.get(tid) + "\n";
    else pjsip += genTenantPjsip(t);

    if (eBlocks.has(tid)) exts += eBlocks.get(tid) + "\n";
    else exts += genTenantExtensions(t);

    queues += genTenantQueue(t); // queues are cheap to regenerate for every tenant
  }

  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, "pjsip_tenant.conf"), pjsip);
  fs.writeFileSync(path.join(outDir, "extensions_tenant.conf"), exts);
  fs.writeFileSync(path.join(outDir, "queues_tenant.conf"), queues);

  return { pjsip, exts, queues, tenants: active.map(t => t.id) };
}

if (require.main === module) {
  buildSpoolFiles().then((r) => {
    console.log("make-pbx-config: wrote spool files for tenants", JSON.stringify(r.tenants));
  }).catch((e) => { console.error("make-pbx-config failed:", e); process.exit(1); });
}

module.exports = { buildSpoolFiles };
