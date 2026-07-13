#!/bin/bash
bash /root/sphinx-android-v2/scripts/wait-for-emulator.sh
/home/codespace/nvm/current/bin/appium --address 0.0.0.0 --port 4723 --default-capabilities '{\"appium:automationName\":\"UiAutomator2\"}'
