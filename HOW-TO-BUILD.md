# Pitaka for Android: how to build and install

## Easiest: GitHub builds it for you (nothing to install)

This project lives in the public repository **github.com/yoshipaxti/pitaka**. Only the app's code is there. Your records stay on your phone and are never uploaded.

Every time a change is pushed to `main`, GitHub builds a new APK and publishes it on the repository's **Releases** page as `pitaka.apk`, with a version number that goes up by one each build (1.0.7, 1.0.8, …).

### Updates inside the app

Once you have version 1.0.7 or later installed, Pitaka checks for a newer release each time it opens. When one exists, **Overview** shows **Update available** with an **Update now** button. You can also check from **Settings › App version › Check for updates**.

1. Tap **Update now**. The new APK downloads.
2. The first time only, Android asks you to allow Pitaka to install apps. Turn on **Allow from this source**, go back, and tap **Install**.
3. Android shows its own "Do you want to update this app?" screen. Tap **Update**.

Your records stay, because Android keeps an app's storage when it is updated with an APK signed by the same key. Android refuses any APK signed with a different key, so a tampered download can't replace Pitaka.

### Getting an APK by hand

Download `pitaka.apk` from the newest release on the **Releases** page, or from a terminal on this PC:

```bash
gh release download --repo yoshipaxti/pitaka --pattern pitaka.apk
```

Each build is also kept for 90 days under **Actions › Build APK › Artifacts › pitaka-apk**.

Every APK is signed with the same key, which is stored as the repository secret `DEBUG_KEYSTORE_B64`. That's why each new APK installs over the old one and keeps your data. Don't delete that secret. A copy of the key is also in `android-app\signing\` on this PC, which is never uploaded.

To rebuild after changing the app: edit `pitaka.html`, run `build-android.ps1`, then commit and push the `android-app` folder.

## Build it yourself instead: what to install (one time)

| What | Why | Where |
|---|---|---|
| **Android Studio** (latest stable, free) | Turns the project into an APK. It includes Java and the Android SDK, so you don't need to install those separately. | developer.android.com/studio |
| Node.js | Already on this PC (v22). Only needed if you change the app and run `build-android.ps1` again. | nodejs.org |

Android Studio is about a 1 GB download. With the Android SDK it uses 6–8 GB of disk space.
When you first open it, pick **Standard** setup and let it download everything it asks for.

## Build the APK

1. Open Android Studio, choose **Open**, and select the folder `android-app\android`.
2. Wait for **Gradle sync** to finish (progress bar at the bottom). The first time takes 5–15 minutes while it downloads build tools.
3. In the menu, choose **Build › Generate App Bundles or APKs › Generate APKs**.
   Older versions call it **Build › Build Bundle(s) / APK(s) › Build APK(s)**.
4. When it says the build succeeded, click **locate**. Your app is:
   `android-app\android\app\build\outputs\apk\debug\app-debug.apk`

## Put it on your phone

**Option A, no cable:** send `app-debug.apk` to yourself (Google Drive, Messenger or email), open it on the phone and tap **Install**.
- If Android asks, allow **Install unknown apps** for Drive, Files or whichever app you opened it from.
- Play Protect may say the developer is not recognized. Tap **More details › Install anyway**. This happens because you built the app yourself.

**Option B, USB cable:** on the phone, turn on **Developer options › USB debugging**, plug it in, and press the green ▶ **Run** button in Android Studio.

## Where your data goes

- Every change is saved to **Documents › Pitaka › pitaka-data.json**.
- One copy per day is kept in **Documents › Pitaka › backups**. The last 30 days are kept, and older copies are deleted automatically.
- You can see these files in your phone's **Files** app under **Documents › Pitaka**.
- Sample data is never written to the folder. Tap **Start fresh** on the Home tab to begin saving your own records.
- **Settings › Save to Drive** still works and sends a copy to Google Drive.

## Things to know

- **Uninstalling the app does not delete the Pitaka folder.** Your files stay on the phone.
- **After a reinstall**, Android (version 11 and newer) treats the old files as belonging to a different app, so the new install can't load them by itself. To get your data back:
  open **Settings › Restore from Drive or phone**, go to **Documents › Pitaka**, and pick `pitaka-data.json`.
- **Updating the app:** after changing `pitaka.html`, run `build-android.ps1` again and rebuild the APK.
  Installing the new APK over the old one keeps your data, as long as you build on the same PC.
  A build from a different PC can't install over the old app. In that case, uninstall it first, then use Restore.
- **Fonts** come from Google the first time the app is online. Offline, the app uses your phone's own font, and everything still works.
