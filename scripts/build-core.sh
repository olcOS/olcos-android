#!/usr/bin/env bash
# Binds the olcOS Go core for Android into one AAR: the olcrtc client, the
# tun2socks glue (tunstack), the bonding client (bond) and the Xray engine.
# The core is the olcos-core submodule (olcOS/olcos-core), the same core the
# iOS app binds; this calls its Android build:
#
#   git submodule update --init --recursive
#   bash scripts/build-core.sh [output .aar]    (default: app/libs/olcoscore.aar)
#
# Needs Go (the version in olcos-core/mobile/go.mod), a JDK (gomobile runs
# javac), the Android SDK and an NDK (ANDROID_HOME, ANDROID_NDK_HOME; the
# newest NDK under the SDK is used when ANDROID_NDK_HOME is not set). Phones
# (arm64) and the emulator (amd64) only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/olcos-core"
if [ ! -f "$CORE/scripts/build-android.sh" ]; then
  echo "error: the olcos-core submodule is missing — run: git submodule update --init --recursive" >&2
  exit 1
fi
exec "$CORE/scripts/build-android.sh" "${1:-$ROOT/app/libs/olcoscore.aar}"
