## Android Build & Deploy

This is an Android project running inside a workspace pod with a live emulator.

---

### MANDATORY FINAL STEP — Task is NOT complete until this passes

After making **any** code changes, you MUST run:

```bash
bash /workspaces/sphinx-android-v2/scripts/start-android.sh
```

Then confirm the app is running:

```bash
adb shell pidof chat.sphinx.v2.debug
```

**A numeric PID in the output is the only valid proof of success.** Post that PID in the task chat. No output means the app failed to launch — fix the issue and repeat. Do NOT mark the task complete without a confirmed PID.

---

### Rules — read before running anything

- **Do NOT run `./gradlew` directly.**
- **Do NOT run `adb install` directly.**
- **Do NOT restart or stop any PM2 service** — `emulator`, `android`, `appium`, `scrcpy` must not be touched.
- **Do NOT start a new emulator** — one is already running in the workspace.

---

### What the script does

1. Checks whether the emulator is available; skips gracefully if not (see "Skipped run" section below).
2. Waits for the emulator to be fully ready.
3. Acquires a build lock (`/tmp/android-build.lock`) to prevent concurrent builds.
4. Runs `./gradlew assembleDebug --no-daemon` to build the APK.
5. Installs the APK via `adb install`.
6. Launches the app on the emulator.

---

### Build in progress — wait, don't restart

If `start-android.sh` is already running (e.g. triggered by the `android` PM2 service), **do not invoke it again or restart any PM2 service**. Wait for the in-progress build to finish, then check the PID. The concurrency lock (`/tmp/android-build.lock`) will serialise a second invocation automatically — if you run the script while a build is active, it will block until the first build completes.

---

### Skipped run is NOT a completed build

If the script exits 0 but prints:

```
Emulator not available - skipping build
```

or `adb shell pidof chat.sphinx.v2.debug` returns no output — **the task is not complete**.

You must confirm the emulator is present:

```bash
adb devices
```

If no `emulator-*` line appears, wait 10 seconds and retry (up to ~5 times). Do **not** start a new emulator. Once the emulator is listed, re-run `start-android.sh` and verify the PID.

---

### If `adb devices` shows no device

Wait 10 seconds and retry. Repeat up to ~5 times. If the emulator never appears, report it in the task chat — do NOT start a new emulator and do NOT restart PM2 services.

---

### If the build fails

- The script will exit non-zero immediately with a clear `BUILD FAILED` message.
- Report the **full error output** in the task chat.
- Fix the code issue.
- Re-run `bash /workspaces/sphinx-android-v2/scripts/start-android.sh`.
- Do NOT mark the task complete if the build fails.

---

### App identifiers

| Field            | Value                                       |
|------------------|---------------------------------------------|
| Package name     | `chat.sphinx.v2.debug`                      |
| Main activity    | `chat.sphinx.activitymain.MainActivity`     |
| Build script     | `bash /workspaces/sphinx-android-v2/scripts/start-android.sh` |

---

### PM2 service names (reference only — do not restart)

| Service   | Role                        |
|-----------|-----------------------------|
| `emulator`| Android emulator process    |
| `android` | Runs the build/install loop |
| `appium`  | Appium test server          |
| `scrcpy`  | Screen-mirror helper        |
