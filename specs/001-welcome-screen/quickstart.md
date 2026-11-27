# Quickstart: validate the Welcome Screen

## Prerequisites

- JDK 21 (`mise install`), Android SDK with platform `android-37.2`
- An emulator or a device (API 28+); API 33+ to check the per-app language setting

## Automated checks

```bash
./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Expected: everything passes, including the string-parity test, the coverage gate and the
accessibility checks of `WelcomeScreenTest`.

## Manual checks

1. `./gradlew installDebug` and open Monica: the welcome screen matches the design (see
   [contracts/welcome-screen.md](contracts/welcome-screen.md) for the element order).
2. Tap every control: nothing happens, the screen stays.
3. Dark theme (`adb shell cmd uimode night yes`): the screen uses the dark palette and stays
   readable.
4. French: Settings > System > Languages > App languages > Monica > Français (API 33+), or set the
   device to French. Every text is French, including "Instance : app.monicahq.com".
5. An unsupported language (German): every text is English.
6. TalkBack: swipe through the screen; order and announcements follow the contract table; the logo
   is skipped.
7. Largest font and display size, then landscape: nothing is cut or overlapping, the buttons are
   reachable by scrolling.
8. Compare with the design at 412 × 892 dp, light and dark (`take-screenshot` skill).
