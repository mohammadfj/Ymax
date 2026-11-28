const fs = require("fs");
const p = process.argv[2] || "/app/server.js";
let s = fs.readFileSync(p, "utf8");

// 1) crypto require
const anchor = 'const bcrypt = require("bcryptjs");';
if (!s.includes(anchor)) { console.error("anchor bcrypt require not found"); process.exit(1); }
if (!s.includes('const crypto = require("crypto");'))
  s = s.replace(anchor, anchor + '\nconst crypto = require("crypto");');

// 2) CFG turn block -> add secret + lifetime
const oldCfg = '  turn: {\n' +
  '    publicIp: process.env.CC_TURN_PUBLIC_IP || "46.100.58.20",\n' +
  '    lanIp: process.env.CC_TURN_LAN_IP || "192.168.100.65",\n' +
  '    user: process.env.CC_TURN_USER || "callcenter",\n' +
  '    pass: process.env.CC_TURN_PASS || "", // P0-TURN: no hardcoded fallback credential\n' +
  '  },';
const newCfg = '  turn: {\n' +
  '    publicIp: process.env.CC_TURN_PUBLIC_IP || "46.100.58.20",\n' +
  '    lanIp: process.env.CC_TURN_LAN_IP || "192.168.100.65",\n' +
  '    user: process.env.CC_TURN_USER || "callcenter",\n' +
  '    pass: process.env.CC_TURN_PASS || "", // P0-TURN: no hardcoded fallback credential\n' +
  '    secret: process.env.CC_TURN_SECRET || "", // Phase 4: TURN REST API HMAC secret; empty = legacy static user\n' +
  '    lifetime: 900 // Phase 4: per-tenant TURN credential TTL (seconds)\n' +
  '  },';
if (!s.includes(oldCfg)) { console.error("CFG turn block drift"); process.exit(1); }
s = s.replace(oldCfg, newCfg, 1);

// 3) widget ICE TURN block -> per-tenant REST-API-aware
const oldIce = '  /* TURN — only exposed if tenant has explicitly set turn credentials */\n' +
'  const turnUser = tcfg.turn.user || "";\n' +
'  const turnPass = tcfg.turn.pass || "";\n' +
'  if (tcfg.turn.publicIp && turnUser && turnPass) {\n' +
'    ice.push({ urls: `turn:${tcfg.turn.publicIp}:3478?transport=tcp`, username: turnUser, credential: turnPass });\n' +
'    ice.push({ urls: `turn:${tcfg.turn.publicIp}:3478?transport=udp`, username: turnUser, credential: turnPass });\n' +
'  }\n' +
'  if (tcfg.turn.lanIp && turnUser && turnPass) {\n' +
'    ice.push({ urls: `turn:${tcfg.turn.lanIp}:3478?transport=udp`, username: turnUser, credential: turnPass });\n' +
'  }';
const newIce = '  /* Phase 4 TURN — per-tenant credentials.\n' +
'   * REST API mode (CFG.turn.secret set): mint a short-lived per-tenant credential\n' +
'   * (username "<ts>:t<tid>", password HMAC-SHA1 of the user-combo over the secret).\n' +
'   * Legacy mode (no secret): fall back to the tenant row turn_user/turn_pass, which\n' +
'   * must match a static coturn user. */\n' +
'  let turnUser = "";\n' +
'  let turnPass = "";\n' +
'  let pubIp = tcfg.turn.publicIp || CFG.turn.publicIp;\n' +
'  let lanIp = tcfg.turn.lanIp || CFG.turn.lanIp;\n' +
'  if (CFG.turn.secret) {\n' +
'    const ts = Math.floor(Date.now() / 1000);\n' +
'    const combo = ts + ":t" + tid;\n' +
'    const hmac = require("crypto").createHmac("sha1", CFG.turn.secret).update(combo).digest("base64");\n' +
'    turnUser = combo;\n' +
'    turnPass = hmac;\n' +
'  } else {\n' +
'    turnUser = tcfg.turn.user || "";\n' +
'    turnPass = tcfg.turn.pass || "";\n' +
'  }\n' +
'  if (pubIp && turnUser && turnPass) {\n' +
'    ice.push({ urls: `turn:${pubIp}:3478?transport=tcp`, username: turnUser, credential: turnPass });\n' +
'    ice.push({ urls: `turn:${pubIp}:3478?transport=udp`, username: turnUser, credential: turnPass });\n' +
'  }\n' +
'  if (lanIp && turnUser && turnPass && lanIp !== pubIp) {\n' +
'    ice.push({ urls: `turn:${lanIp}:3478?transport=udp`, username: turnUser, credential: turnPass });\n' +
'  }';
if (!s.includes(oldIce)) { console.error("widget ICE block drift"); process.exit(1); }
s = s.replace(oldIce, newIce, 1);

fs.writeFileSync(p, s);
console.log("patched", p);
console.log("Phase4 blocks:", (s.match(/Phase 4 TURN/g)||[]).length, "| CFG secret:", (s.match(/CC_TURN_SECRET/g)||[]).length, "| hmac:", (s.match(/createHmac/g)||[]).length);
