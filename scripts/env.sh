#!/bin/sh
# Окружение для сборки samopisec: JDK 17, Android SDK shim, кэши в /tmp
# Использование: . ./scripts/env.sh  (source, не sh)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/tmp/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.1.12297006"
export NPM_CONFIG_CACHE=/tmp/npm-cache
export GRADLE_USER_HOME=/tmp/gradle-home
export ANDROID_AVD_HOME=/tmp/avd
export ANDROID_PREFS_ROOT=/tmp/android-preferences
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmake/3.22.1/bin:$PATH"
# Проверка
if [ ! -x "$JAVA_HOME/bin/java" ]; then echo "WARN: JAVA_HOME не найден: $JAVA_HOME" >&2; fi
if [ ! -d "$ANDROID_HOME/platform-tools" ]; then echo "WARN: ANDROID_HOME не готов — запусти ./scripts/android-sdk-shim.sh" >&2; fi
