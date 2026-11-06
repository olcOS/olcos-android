#!/usr/bin/env bash
# Copies what the app carries for the server from the olcos-core submodule
# into the assets: the server installer (srv.sh), the olcOS core patch series
# srv.sh and the server update apply, add-carrier.sh, rotate-key.sh, the
# agent's status scripts and the Go sources of the agent and the bond
# endpoint (the server builds both). None of it is committed here: olcos-core
# is the one source, shared with the iOS app.
#
#   git submodule update --init --recursive
#   bash scripts/sync-core-assets.sh
#
# CI runs it before every build; run it again after moving the submodule.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/olcos-core"
ASSETS="$ROOT/app/src/main/assets"
if [ ! -f "$CORE/server/srv.sh" ] || [ ! -d "$CORE/mobile/agent" ]; then
  echo "error: the olcos-core submodule is missing — run: git submodule update --init --recursive" >&2
  exit 1
fi

rm -rf "$ASSETS/scripts" "$ASSETS/agent" "$ASSETS/bond"
mkdir -p "$ASSETS/scripts/agent-status" "$ASSETS/agent/cmd/olcos-agent" "$ASSETS/bond/cmd/olcos-bond"

cp "$CORE/server/srv.sh" "$CORE/server/olcrtc-core.patch" \
   "$CORE/server/add-carrier.sh" "$CORE/server/rotate-key.sh" "$ASSETS/scripts/"
cp "$CORE"/server/agent-status/*.sh "$ASSETS/scripts/agent-status/"

# A package's Go sources without its tests.
copy_package() {
  local f
  for f in "$1"/*.go; do
    case "$f" in *_test.go) ;; *) cp "$f" "$2/" ;; esac
  done
}
copy_package "$CORE/mobile/agent" "$ASSETS/agent"
cp "$CORE/mobile/agent/cmd/olcos-agent/main.go" "$ASSETS/agent/cmd/olcos-agent/"
copy_package "$CORE/mobile/bond" "$ASSETS/bond"
cp "$CORE/mobile/bond/cmd/olcos-bond/main.go" "$ASSETS/bond/cmd/olcos-bond/"

agent=("$ASSETS"/agent/*.go)
bond=("$ASSETS"/bond/*.go)
echo "assets from olcos-core $(git -C "$CORE" rev-parse --short HEAD 2>/dev/null || echo '?'):" \
  "srv.sh, the core patch, add-carrier.sh, rotate-key.sh, the status scripts," \
  "${#agent[@]} agent and ${#bond[@]} bond sources"
