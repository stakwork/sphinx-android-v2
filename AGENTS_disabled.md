## Android Build & Deploy

This is an Android project running inside a workspace pod with a live emulator.

### IMPORTANT: After making ANY code changes you MUST run the build command

```bash
bash /workspaces/sphinx-android-v2/scripts/start-android.sh
```

This is MANDATORY. Do NOT skip this step under any circumstances.
Do NOT run `./gradlew` directly.
Do NOT run `adb install` directly.
Do NOT consider the task complete until this script has run successfully.

### What the script does
1. Waits for the emulator to be ready
2. Runs `./gradlew assembleDebug` to build the APK
3. Installs the APK on the emulator via `adb install`
4. Launches the app on the emulator

### Verifying the build succeeded
After running the script, verify the app is running:
```bash
adb shell pidof chat.sphinx.v2.debug
```
A number returned = app is running successfully ✅
No output = app failed to launch ❌

### If the build fails
- Report the FULL error output in the task chat
- Fix the code issue
- Run `bash /workspaces/sphinx-android-v2/scripts/start-android.sh` again
- Do NOT mark the task complete if the build fails

### Emulator rules
- The emulator is already running — do NOT start or restart it
- Do NOT restart or stop any PM2 services (`emulator`, `android`, `appium`, `scrcpy`)
- If `adb devices` shows no device, wait 10 seconds and retry — do NOT start a new emulator
- The app package name is `chat.sphinx.v2.debug`
- The main activity is `chat.sphinx.activitymain.MainActivity`