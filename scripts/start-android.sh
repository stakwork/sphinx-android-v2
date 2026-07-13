#!/bin/bash
bash /root/sphinx-android-v2/scripts/wait-for-emulator.sh
./gradlew assembleDebug --no-daemon