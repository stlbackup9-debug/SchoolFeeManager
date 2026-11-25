# Installation Guide

## 1. Android (APK)

1. Copy `release/MyanmarNumberRecord-v1.0.0.apk` to the phone (USB, Telegram, Viber, Google Drive…).
2. Tap the file. If Android asks, allow **Install unknown apps** for the app you opened it from (Files / Chrome / Telegram).
3. Tap **Install**, then **Open**. The app appears as **2D·3D Records**.

Requirements: Android 6.0 or newer, about 15 MB of space. The app needs no internet connection.

> Data is stored inside the app. **Uninstalling the app or using "Clear data" deletes the records**, so make backups regularly (Settings → Backup database → save to Drive/Telegram/Files).
> On Android, *Excel*, *PDF*, *Print* and *Backup* open the share sheet. Choose "Save to Files/Drive", or an app such as Excel or a PDF viewer (print from there). A copy is also stored in `Documents/2D3D/` when the phone allows it.

## 2. Windows / macOS / Linux (desktop)

1. Copy `release/MyanmarNumberRecord-Desktop.html` to a permanent place, for example `Documents\2D3D\`.
2. Double-click it. It opens in your browser; **Google Chrome or Microsoft Edge are recommended**.
3. Optional: create a desktop shortcut to the file.

Everything runs offline inside this single file. Records are saved automatically in the browser's storage for that file:
- Always open the **same file in the same browser**. Another browser, or the file moved to another folder, starts with an empty database. In that case, use *Restore* with a backup file.
- Do not use private/incognito windows, and do not clear "site data" for local files.
- Make regular backups (Settings → Backup database).

### Optional: hosting on a web server / local network
Run `npm run build` and copy the `dist/` folder to any static web server (nginx, IIS, GitHub Pages, etc.). Each device keeps its own data.

## 3. Building from source

Prerequisites: Node.js 20+ (22 recommended). For the APK you also need JDK 17 or 21 and the Android SDK (Android Studio installs both).

```bash
cd MyanmarNumberRecord
```

```bash
npm install
```

```bash
npm test
```

```bash
npm run build
```

```bash
npm run build:single
```

`npm test` runs the unit tests (validation, database, calculations, backup). `npm run build` writes the web build to `dist/`. `npm run build:single` writes the single desktop file `dist-single/index.html`.

### Android APK

With Android Studio: run `npm run android:sync`, then *File → Open* the `android/` folder and choose *Build → Build APK(s)*.

From the command line (set `ANDROID_HOME`, or write `sdk.dir=...` into `android/local.properties`):

```bash
npm run android:apk
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

For Google Play or a signed release, use *Build → Generate Signed App Bundle / APK* in Android Studio with your own key.

### End-to-end tests (optional)
`npm run e2e` drives the built app in Chromium with Playwright. Install it with `npm i -D playwright && npx playwright install chromium` if it isn't available globally.
