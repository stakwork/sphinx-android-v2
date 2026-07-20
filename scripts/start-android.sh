if adb shell pidof chat.sphinx.v2.debug > /dev/null 2>&1; then
  echo "App already running, skipping install and launch"
  exit 0
fi

#!/bin/bash
bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh
./gradlew assembleDebug --no-daemon

# Install the APK on the emulator
adb install -r sphinx/application/sphinx/build/outputs/apk/debug/sphinx-x86_64-debug.apk

# Launch the app
adb shell am start -n chat.sphinx.v2.debug/chat.sphinx.activitymain.MainActivity