#!/bin/bash

bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

if adb shell pidof chat.sphinx.v2.debug > /dev/null 2>&1; then
  echo "App already running, skipping install and launch"
  exit 1
fi

./gradlew assembleDebug --no-daemon