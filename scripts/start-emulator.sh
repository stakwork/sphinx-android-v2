#!/bin/bash

export PATH=$PATH:/opt/android-sdk/emulator:/opt/android-sdk/platform-tools

# Clear any stale locks from previous runs
rm -f ~/.android/avd/sphinx_avd.avd/*.lock

# Start emulator in background
emulator -avd sphinx_avd -no-audio -no-window -gpu swiftshader_indirect -no-boot-anim -no-snapshot-load -no-snapshot-save &

# Wait for adb to see the device
adb wait-for-device

# Wait for full boot (sys.boot_completed = 1)
echo "Waiting for emulator to fully boot..."
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
  sleep 2
done

# Wait for the launcher to be ready
echo "Waiting for launcher to be ready..."
until adb shell dumpsys window windows 2>/dev/null | grep -q "mCurrentFocus"; do
  sleep 2
done

echo "Emulator ready!"
wait