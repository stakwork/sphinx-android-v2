#!/bin/bash

LOCK_FILE=/tmp/android-build.lock

echo "=== start-android.sh called at $(date) ===" >> /tmp/android-build.log

# If emulator not available, skip gracefully
if ! adb devices | grep -q "emulator"; then
  echo "Emulator not available - skipping build"
  echo "=== start-android.sh skipped (no emulator) at $(date) ===" >> /tmp/android-build.log
  exit 0
fi

bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

# Acquire build lock — wait for any in-progress build to finish before proceeding
echo "Acquiring build lock..."
exec 9>"$LOCK_FILE"
flock 9
echo "Build lock acquired at $(date)"

# Release lock on exit (success or failure)
trap 'flock -u 9; exec 9>&-' EXIT

./gradlew assembleDebug --no-daemon
if [ $? -ne 0 ]; then
  echo "BUILD FAILED - see error above"
  echo "=== BUILD FAILED at $(date) ===" >> /tmp/android-build.log
  exit 1
fi

adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity

echo "App successfully built, installed and launched"
echo "=== start-android.sh completed at $(date) ===" >> /tmp/android-build.log