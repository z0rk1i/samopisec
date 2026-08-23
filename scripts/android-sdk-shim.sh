#!/bin/sh
# Создаёт /tmp/sdk — шов для Android SDK: симлинки на реальные компоненты
# (установленные через brew в /opt/homebrew) + настоящий NDK r27b
# (27.1.12297006) и CMake 3.22.1, которые gradle требует для сборки.
# Запускать после перезагрузки (tmp очищается). Устойчив к нехватке места.
set -eu
REAL_SDK=/opt/homebrew/share/android-commandlinetools
SHIM=/tmp/sdk
NDK_VERSION=27.1.12297006
NDK_DIR=android-ndk-r27b
NDK_ZIP=/tmp/$NDK_DIR-darwin.zip
NDK_URL=https://dl.google.com/android/repository/$NDK_DIR-darwin.zip
CMAKE_VERSION=3.22.1
CMAKE_ZIP=/tmp/cmake-3.22.1-darwin.zip
CMAKE_URL=https://dl.google.com/android/repository/cmake-3.22.1-darwin.zip

need_ndk() { [ ! -d "$SHIM/ndk/$NDK_VERSION/toolchains" ]; }
need_cmake() { [ ! -x "$SHIM/cmake/$CMAKE_VERSION/bin/cmake" ]; }

mkdir -p "$SHIM"
for d in build-tools cmdline-tools emulator licenses platform-tools platforms system-images; do
  if [ ! -e "$SHIM/$d" ] && [ -d "$REAL_SDK/$d" ]; then
    ln -s "$REAL_SDK/$d" "$SHIM/$d"
  fi
done

# --- NDK ---
if need_ndk; then
  echo "NDK $NDK_VERSION не найден — пробую sdkmanager..."
  if . ./scripts/env.sh >/dev/null 2>&1; then
    if yes | "$SHIM/cmdline-tools/latest/bin/sdkmanager" --install "ndk;$NDK_VERSION" >/tmp/sdkmanager-ndk.log 2>&1; then
      echo "NDK установлен через sdkmanager"
    else
      echo "sdkmanager NDK не удался, пробую curl..."
      cat /tmp/sdkmanager-ndk.log | tail -5
    fi
  fi
fi
if need_ndk; then
  echo "скачиваю NDK $NDK_VERSION через curl (797М, ~3Г на диске нужно)..."
  df -h /tmp | tail -1
  curl -L -o "$NDK_ZIP" "$NDK_URL"
  mkdir -p "$SHIM/ndk"
  rm -rf /tmp/$NDK_DIR "$SHIM/ndk/$NDK_VERSION"
  # проверка места перед распаковкой
  avail=$(df -k /tmp | awk 'NR==2{print $4}')
  if [ "$avail" -lt 3000000 ]; then
    echo "мало места в /tmp ($avail К), чищу кэши..."
    rm -rf /tmp/gradle-home /tmp/m2 /tmp/cmake.zip /tmp/android-ndk-*
    df -h /tmp | tail -1
  fi
  unzip -q "$NDK_ZIP" -d /tmp
  rm -f "$NDK_ZIP"
  mv /tmp/$NDK_DIR "$SHIM/ndk/$NDK_VERSION"
  echo "NDK готов"
fi

# --- CMake ---
if need_cmake; then
  echo "CMake $CMAKE_VERSION не найден — пробую sdkmanager..."
  if . ./scripts/env.sh >/dev/null 2>&1; then
    if yes | "$SHIM/cmdline-tools/latest/bin/sdkmanager" --install "cmake;$CMAKE_VERSION" >/tmp/sdkmanager-cmake.log 2>&1; then
      echo "CMake установлен через sdkmanager"
    else
      cat /tmp/sdkmanager-cmake.log | tail -5
    fi
  fi
fi
if need_cmake; then
  echo "скачиваю CMake $CMAKE_VERSION через curl..."
  curl -L -o "$CMAKE_ZIP" "$CMAKE_URL"
  mkdir -p "$SHIM/cmake/$CMAKE_VERSION"
  rm -rf /tmp/cmake-3.22.1
  unzip -q "$CMAKE_ZIP" -d /tmp
  # sdkmanager кладёт cmake без версии, curl — с префиксом; нормализуем
  if [ -d "/tmp/cmake-3.22.1" ]; then
    rm -rf "$SHIM/cmake/$CMAKE_VERSION"
    mv /tmp/cmake-3.22.1 "$SHIM/cmake/$CMAKE_VERSION"
  fi
  rm -f "$CMAKE_ZIP"
  echo "CMake готов"
fi

# проверка
if [ ! -x "$SHIM/ndk/$NDK_VERSION/ndk-build" ]; then
  echo "ОШИБКА: NDK не установлен в $SHIM/ndk/$NDK_VERSION"
  exit 1
fi
if [ ! -x "$SHIM/cmake/$CMAKE_VERSION/bin/cmake" ]; then
  # fallback: sdkmanager кладёт cmake в $SHIM/cmake без версии
  if [ -x "$SHIM/cmake/bin/cmake" ]; then
    mkdir -p "$SHIM/cmake/$CMAKE_VERSION"
    ln -sf "$SHIM/cmake/bin" "$SHIM/cmake/$CMAKE_VERSION/bin"
    ln -sf "$SHIM/cmake/share" "$SHIM/cmake/$CMAKE_VERSION/share"
  else
    echo "ОШИБКА: cmake не найден"
    exit 1
  fi
fi
echo "shim ready at $SHIM (NDK $NDK_VERSION, CMake $CMAKE_VERSION)"
ls -lh "$SHIM/ndk/$NDK_VERSION/source.properties" "$SHIM/cmake/$CMAKE_VERSION/bin/cmake" | head -5
