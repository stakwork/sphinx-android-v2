#!/bin/bash
bash /root/sphinx-android-v2/scripts/wait-for-emulator.sh
./gradlew assembleDebug --no-daemon

# Install the APK on the emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n io.sphinx/.activity.MainActivity