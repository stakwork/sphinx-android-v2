#!/bin/bash

echo "=== android-service.sh called at $(date) ===" >> /tmp/android-build.log

bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh

bash /workspaces/sphinx-android-v2/scripts/start-android.sh