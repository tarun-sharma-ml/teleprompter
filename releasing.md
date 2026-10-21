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

1. In `app/build.gradle.kts`, increment `versionCode` and set `versionName` to a version greater
   than the version installed by users, for example `versionCode = 7` and `versionName = "1.4.2"`.
   Keep `versionCode` increasing for every APK; Android uses it to reject downgrades.
2. **Build → Generate Signed App Bundle / APK → APK**, choose your key, pick **release**.
   The file is `app/release/app-release.apk`.
3. On GitHub: **Releases → Draft a new release**.
    - Tag: `v1.4.2` (must match the APK's `versionName`; the leading `v` is optional)
    - Attach the APK
    - Optional: write release notes (they are shown in the app's update dialog)
    - **Publish release** (drafts and pre-releases are ignored by the app)

Anyone with the app will see the update next time they open it.

## Version ordering and the v1.2/v1.3 clash

The updater asks GitHub for the latest published release and compares its tag with the installed
APK's `versionName`. It only offers the APK when the release version is numerically higher. It does
not offer every intermediate release, and release tags do not change the version already installed
on a phone.

In the recent version clash, the phone reported **1.3**, while the project was changed to **1.2.1**.
Because `1.2.1` is lower than `1.3`, it cannot update that phone; **v1.2 was skipped for the same
reason**. The next update for a phone on 1.3 must be higher than 1.3, such as **v1.4**, with a
higher Android `versionCode`. The project is now set to **v1.4.2** (`versionCode = 7`).

Deleting a release does not roll back installed apps. It removes that release from GitHub's latest
release lookup, which can make the updater fall back to an older published tag. At the time this
issue was checked, GitHub's latest-release endpoint returned **v1.1**. A phone already on 1.3 will
not see an update from v1.1; publish a newer release (for example v1.4.2) with a matching signed
APK instead. Do not delete a release to make an older version install as an update.

Build and sign the v1.4.2 APK with the same release key, then publish it as **v1.4.2**.

## Good to know

- People who installed a **debug** build (from Android Studio's Run button) must uninstall it once
  before installing the signed release APK, because the signing keys differ. Later updates install
  over it normally.
- The first time, the user must allow *Install unknown apps* for this app. The app sends them to that
  setting and asks them to tap Update again.
- Each update still needs one tap on *Install*: Android doesn't allow silent updates outside the Play Store.
- Updating restarts the app, so tap *Start Teleprompter* again afterwards.