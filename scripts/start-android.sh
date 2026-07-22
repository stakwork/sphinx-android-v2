#!/bin/bash

echo "=== start-android.sh called at $(date) ===" >> /tmp/android-build.log

bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

./gradlew assembleDebug --no-daemon
if [ $? -ne 0 ]; then
  echo "BUILD FAILED - see error above"
  sleep infinity
fi

adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity

echo "App successfully built, installed and launched"
echo "=== start-android.sh completed at $(date) ===" >> /tmp/android-build.log

sleep infinity