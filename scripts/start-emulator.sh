#!/bin/bash
# Start emulator in background
emulator -avd sphinx_avd -no-audio -no-window -gpu swiftshader_indirect -no-boot-anim &

# Wait for adb to see the device
adb wait-for-device

# Wait for full boot (sys.boot_completed = 1)
echo "Waiting for emulator to fully boot..."
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
  sleep 2
done

echo "Emulator ready!"
wait