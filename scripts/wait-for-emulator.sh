#!/bin/bash

export PATH=$PATH:/opt/android-sdk/emulator:/opt/android-sdk/platform-tools

echo "Waiting for emulator to be ready..."
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
  sleep 3
done
echo "Emulator ready!"