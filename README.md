# Teleprompter (Android)

A floating teleprompter that sits **on top of your phone's camera app**. Tap *Start Teleprompter* and the script panel appears over whatever app you open next, including the camera.

## Features

- Floating panel over other apps: current line highlighted, reading-line markers, progress bar
- On-panel buttons: hide, ±5 s, play/pause, slower/faster, text size
- Drag the small bar at the top to move the panel; drag on the text to scrub
- In-app editor to type or paste your script
- Separate Script, Settings, and Help screens for easier navigation
- Animated startup intro: the reading line draws in, pulses, reveals script markers, then settles into the app screen
- Light and dark app themes, with your choice saved between launches
- Adjustable scrolling speed (WPM) and font size, applied live to a running panel
- Built-in updater: checks GitHub Releases and installs new versions from inside the app

## Install

**From a release (no Android Studio needed):** download the latest `.apk` from the
[Releases](../../releases) page and open it on your phone. Allow *Install unknown apps* for your browser when asked.
After that, the app updates itself.

**From source:**

1. Install [Android Studio](https://developer.android.com/studio) and open this project folder. Let Gradle sync.
2. Plug in your phone (USB debugging on) and press **Run**, or run `./gradlew assembleDebug` and install the APK.

Requires Android 8.0+ (API 26).

## First run

1. Open the app, tap **Grant "Display over other apps"** and allow it.
2. Type or paste your script in the text box.
3. Tap **Start Teleprompter**. The panel appears immediately.
4. Open your camera app and record while reading the floating prompt.

## Notes

- The panel is a separate window: it is **not recorded** by the camera app. Only you see it on screen.
- Drag the top bar to place the panel near your camera lens for better eye contact.
- Some phones stop background apps. If the panel disappears, set this app's battery usage to *Unrestricted*.

## Updates and releases

The updater compares the latest published GitHub release tag with the version installed on the
phone. A release must be newer than the installed version: for example, **v1.2.1 cannot update
v1.3**. Deleting a release does not downgrade phones and can make GitHub's latest-release lookup
fall back to an older tag. See [releasing.md](releasing.md) for versioning guidance and the recent
v1.2/v1.3 clash.

## Project layout

```
app/src/main/java/com/example/telepromptertg/
  MainActivity.kt          script, speed and size settings, start/stop, update check
  OverlayService.kt        foreground service keeping the floating overlay active
  Overlay.kt               floating window, scrolling logic and on-screen controls
  Prefs.kt                 saved script text and settings
  Updater.kt               checks GitHub Releases and installs the update
  InstallResultReceiver.kt receives the installer's result
```
