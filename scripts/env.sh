#!/bin/sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/tmp/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export NPM_CONFIG_CACHE=/tmp/npm-cache
export GRADLE_USER_HOME=/tmp/gradle-home
export ANDROID_AVD_HOME=/tmp/avd
export ANDROID_PREFS_ROOT=/tmp/android-preferences
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"