if lsof -i :3000 | grep -q LISTEN; then
  echo "ws-scrcpy already running, skipping"
  exit 0
fi

#!/bin/bash
bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh
node /opt/ws-scrcpy/dist/index.js