# Releasing updates

The app checks this repo's latest **GitHub Release** each time it opens (and when you tap
*Check for updates*). If the release is newer than the installed version, it offers to download the
APK and Android shows its normal "Update this app?" screen.

## One-time setup

1. Make the GitHub repository **public** (the app reads releases without logging in).
2. In `Updater.kt`, set `REPO` to `"your-username/your-repo"`.
3. Create a signing key: Android Studio → **Build → Generate Signed App Bundle / APK → APK →
   Create new…**. Keep the `.jks` file and its passwords somewhere safe and **never commit it**
   (`.gitignore` already excludes `*.jks` / `*.keystore`).
   Every release must be signed with this same key, or Android refuses to install it over the old app.

## Every release

1. In `app/build.gradle.kts` raise both numbers, for example:
   `versionCode = 2` and `versionName = "1.1"`.
2. **Build → Generate Signed App Bundle / APK → APK**, choose your key, pick **release**.
   The file is `app/release/app-release.apk`.
3. On GitHub: **Releases → Draft a new release**.
    - Tag: `v1.1` (must match `versionName`; the leading `v` is optional)
    - Attach the APK
    - Optional: write release notes (they are shown in the app's update dialog)
    - **Publish release** (drafts and pre-releases are ignored by the app)

Anyone with the app will see the update next time they open it.

## Good to know

- People who installed a **debug** build (from Android Studio's Run button) must uninstall it once
  before installing the signed release APK, because the signing keys differ. Later updates install
  over it normally.
- The first time, the user must allow *Install unknown apps* for this app. The app sends them to that
  setting and asks them to tap Update again.
- Each update still needs one tap on *Install*: Android doesn't allow silent updates outside the Play Store.
- Updating restarts the app, so tap *Start Teleprompter* again afterwards.