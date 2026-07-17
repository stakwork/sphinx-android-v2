#!/bin/bash
bash /root/sphinx-android-v2/scripts/wait-for-emulator.sh
./gradlew assembleDebug --no-daemon

# Install the APK on the emulator
adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk

# Launch the app
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity