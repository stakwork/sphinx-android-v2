#!/bin/bash

bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

./gradlew assembleDebug --no-daemon
if [ $? -ne 0 ]; then
  echo "BUILD FAILED - see error above"
  exit 1
fi

adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity

echo "App successfully built, installed and launched"