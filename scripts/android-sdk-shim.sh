#!/bin/sh
# Создаёт /tmp/sdk — шов для Android SDK: симлинки на реальные компоненты
# (установленные через brew в /opt/homebrew) + настоящий NDK r27b
# (27.1.12297006), который gradle требует для сборки нативного кода.
# dl.google.com доступен — NDK скачивается при необходимости.
# Запускать после перезагрузки (tmp очищается).
set -e
REAL_SDK=/opt/homebrew/share/android-commandlinetools
SHIM=/tmp/sdk
NDK_VERSION=27.1.12297006
NDK_DIR=android-ndk-r27b
NDK_ZIP=/tmp/$NDK_DIR-darwin.zip
NDK_URL=https://dl.google.com/android/repository/$NDK_DIR-darwin.zip

mkdir -p "$SHIM"
for d in build-tools cmdline-tools emulator licenses platform-tools platforms system-images; do
  if [ ! -e "$SHIM/$d" ] && [ -d "$REAL_SDK/$d" ]; then
    ln -s "$REAL_SDK/$d" "$SHIM/$d"
  fi
done

if [ ! -d "$SHIM/ndk/$NDK_VERSION/toolchains" ]; then
  echo "downloading NDK $NDK_VERSION..."
  curl -sL -o "$NDK_ZIP" "$NDK_URL"
  mkdir -p "$SHIM/ndk"
  rm -rf /tmp/$NDK_DIR "$SHIM/ndk/$NDK_VERSION"
  unzip -q "$NDK_ZIP" -d /tmp
  rm -f "$NDK_ZIP"
  mv /tmp/$NDK_DIR "$SHIM/ndk/$NDK_VERSION"
fi
echo "shim ready at $SHIM (NDK $NDK_VERSION)"