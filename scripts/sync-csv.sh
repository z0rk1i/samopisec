#!/bin/sh
set -eu
# Синхронизирует datapoints.csv с устройства в /tmp/samopisec.csv для Grafana.
# Android: adb (эмулятор/reales device), iOS: симулятор (simctl).
# Usage: ./scripts/sync-csv.sh [--watch]

SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$SRC_DIR/scripts/env.sh" 2>/dev/null || true

DEST="/tmp/samopisec.csv"
WATCH=0
if [ "${1:-}" = "--watch" ]; then WATCH=1; fi

pull_android() {
  if ! command -v adb >/dev/null 2>&1; then return 1; fi
  if ! adb get-state >/dev/null 2>&1; then return 1; fi
  # emulator: root cat, device: run-as
  if adb shell "cat /data/data/com.z0rk1.samopisec/files/datapoints.csv" > "$DEST.tmp" 2>/dev/null; then
    mv "$DEST.tmp" "$DEST"
    echo "android -> $DEST ($(wc -l < "$DEST" | tr -d ' ') lines)"
    return 0
  fi
  if adb shell "run-as com.z0rk1.samopisec cat files/datapoints.csv" > "$DEST.tmp" 2>/dev/null; then
    mv "$DEST.tmp" "$DEST"
    echo "android (run-as) -> $DEST ($(wc -l < "$DEST" | tr -d ' ') lines)"
    return 0
  fi
  return 1
}

pull_ios() {
  if ! command -v xcrun >/dev/null 2>&1; then return 1; fi
  local bid="com.z0rk1.samopisec"
  local container
  container=$(xcrun simctl get_app_container booted "$bid" data 2>/dev/null || true)
  if [ -z "$container" ]; then return 1; fi
  local app_group
  app_group=$(find "$container" -name "datapoints.csv" 2>/dev/null | head -n1 || true)
  if [ -z "$app_group" ]; then
    # App Group container
    local gd
    gd=$(xcrun simctl get_app_container booted "$bid" groups 2>/dev/null | head -n1 || true)
    if [ -n "$gd" ]; then
      app_group=$(find "$gd" -name "datapoints.csv" 2>/dev/null | head -n1 || true)
    fi
  fi
  if [ -n "$app_group" ] && [ -f "$app_group" ]; then
    cp "$app_group" "$DEST"
    echo "ios -> $DEST ($(wc -l < "$DEST" | tr -d ' ') lines) from $app_group"
    return 0
  fi
  return 1
}

sync_once() {
  if pull_android; then return 0; fi
  if pull_ios; then return 0; fi
  echo "no device/simulator csv found; creating empty $DEST"
  echo "id,button_id,ts" > "$DEST"
}

if [ "$WATCH" -eq 1 ]; then
  echo "watching -> $DEST (Ctrl-C to stop)"
  while true; do
    sync_once || true
    sleep 2
  done
else
  sync_once
  ls -lh "$DEST" 2>/dev/null || true
  head -n 5 "$DEST" 2>/dev/null || true
fi
