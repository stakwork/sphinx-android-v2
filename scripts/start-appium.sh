if lsof -i :4723 | grep -q LISTEN; then
  echo "Appium already running, skipping"
  exit 0
fi

#!/bin/bash
bash /workspaces/sphinx-android-v2/scripts/wait-for-emulator.sh
/home/codespace/nvm/current/bin/appium --address 0.0.0.0 --port 4723 --default-capabilities '{"appium:automationName":"UiAutomator2"}'