---
name: take-screenshot
description: Take a screenshot of the app running on an Android emulator or device. Use whenever a task requires taking, inspecting or validating a screenshot of the app. Pull requests do not require one.
---

# Take a screenshot

Screenshots are temporary review artifacts. They MUST stay outside the repository: write them to
`/tmp/<feature>.png`. Attach one to a pull request only when asked (see the `commits` skill).

## 1. Have a device

```bash
adb devices
```

When no device is listed, start an emulator. Create one once if needed (system image for the
latest stable API, x86_64, Google APIs):

```bash
sdkmanager "system-images;android-36;google_apis;x86_64" "emulator"
avdmanager create avd -n monica -k "system-images;android-36;google_apis;x86_64"
emulator -avd monica -no-snapshot -no-audio &
adb wait-for-device
```

## 2. Install the branch's build

```bash
./gradlew installDebug
adb shell am start -n com.monicahq.android/.MainActivity
```

The screenshot MUST show the screen as the branch draws it. Never capture the screen as it was
before the change: reviewers read the diff for that.

## 3. Capture

Navigate to the screen and state to show, then:

```bash
adb exec-out screencap -p > /tmp/<feature>.png
```

- Capture one shot per screen or per state (empty, loading, content, error) the change touches.
- When the change affects dark mode, also capture it:
  `adb shell cmd uimode night yes`, capture, then `adb shell cmd uimode night no`.
- Open the image with the Read tool to check it shows what you expect before using it.
