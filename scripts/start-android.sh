#!/bin/bash

echo "=== start-android.sh called at $(date) ===" >> /tmp/android-build.log

# Quick check - if emulator not available in 30s, skip gracefully
# This handles the case where we're called during PUT /latest before emulator starts
QUICK_TIMEOUT=30
ELAPSED=0
echo "Checking if emulator is available..."
until adb devices | grep -q "emulator"; do
  if [ $ELAPSED -ge $QUICK_TIMEOUT ]; then
    echo "Emulator not available after ${QUICK_TIMEOUT}s - skipping build"
    echo "=== start-android.sh skipped (no emulator) at $(date) ===" >> /tmp/android-build.log
    sleep infinity
  fi
  sleep 5
  ELAPSED=$((ELAPSED + 5))
done

echo "Emulator detected, waiting for full boot..."
bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

./gradlew assembleDebug --no-daemon
if [ $? -ne 0 ]; then
  echo "BUILD FAILED - see error above"
  echo "=== BUILD FAILED at $(date) ===" >> /tmp/android-build.log
  sleep infinity
fi

adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity

echo "App successfully built, installed and launched"
echo "=== start-android.sh completed at $(date) ===" >> /tmp/android-build.log

sleep infinity