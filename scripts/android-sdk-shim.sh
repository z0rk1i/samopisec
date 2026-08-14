#!/bin/sh
# Создаёт /tmp/sdk — шов для Android SDK: симлинки на реальные компоненты
# (установленные через brew в /opt/homebrew) + заглушки NDK с source.properties.
# Нужно потому что dl.google.com недоступен для скачивания NDK.
# Запускать после перезагрузки (tmp очищается).
set -e
REAL_SDK=/opt/homebrew/share/android-commandlinetools
SHIM=/tmp/sdk
mkdir -p "$SHIM"
for d in build-tools cmdline-tools emulator licenses platform-tools platforms system-images; do
  if [ ! -e "$SHIM/$d" ] && [ -d "$REAL_SDK/$d" ]; then
    ln -s "$REAL_SDK/$d" "$SHIM/$d"
  fi
done
for v in 27.0.12077973 27.1.12297006; do
  mkdir -p "$SHIM/ndk/$v"
  printf 'Pkg.Revision=%s\n' "$v" > "$SHIM/ndk/$v/source.properties"
done
echo "shim ready at $SHIM"