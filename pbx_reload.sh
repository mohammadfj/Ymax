#!/bin/bash
# pbx_reload.sh — deploy generated spool into the pbx + restart to load new tenants.
# Called by host cron after POST /api/tenants drops .reload-pending, and by operator.
# Safety: refuses to restart if any channel is open (no active calls).
#
# NOTE: on Asterisk 20 `module reload res_pjsip.so` does NOT re-parse
# pjsip.conf; a clean container restart (entrypoint re-seeds /etc from spool) is
# the reliable path to load new tenant sections.
set -eu

PBX=callcenter-web-pbx-1
VOLDIR=/var/lib/docker/volumes/9832012f0a9e2aabb11235c36450b704e53cd3596b083d3f5540caf6c79edd46/_data
SPPOOL=/opt/callcenter-web/pbx-spool          # host shared dir = source of truth
GEN=/opt/callcenter-web/pbx-spool.gen

echo "=== channels-empty check (no active calls before restart) ==="
CH=$(docker exec "$PBX" asterisk -rx "core show channels concise" 2>/dev/null || true)
if echo "$CH" | grep -qE "channel"; then
  echo "WARN: active channels present; aborting (retry when idle):"; echo "$CH"; exit 1
fi
echo "no active channels"

echo "=== promote generated spool -> shared dir (source of truth) ==="
for f in pjsip_tenant.conf extensions_tenant.conf queues_tenant.conf; do
  [ -f "$GEN/$f" ] || continue
  # LF-normalize before promotion (pjsip/extensions must be LF)
  sed 's/\r$//' "$GEN/$f" > "$SPPOOL/$f"
done

echo "=== shared spool -> pbx live spool volume ==="
for f in pjsip_tenant.conf extensions_tenant.conf queues_tenant.conf; do
  [ -f "$SPPOOL/$f" ] || continue
  cp "$SPPOOL/$f" "$VOLDIR/$f"
done

echo "=== restart pbx (entrypoint re-seeds /etc/asterisk from spool) ==="
cd /opt/callcenter-web
docker compose restart pbx >/dev/null
sleep 12

echo "=== verify tenant endpoints loaded ==="
docker exec "$PBX" asterisk -rx "pjsip show endpoints" 2>/dev/null | grep -oE "Endpoint: +[a-z0-9-]+" | sed 's/^ *//' | sort | head -60
echo "OK pbx_reload"
