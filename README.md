# School Fee Manager (Android + Windows)

Offline school fee management app converted from `School_Fee_Management.xlsx`.
Kotlin · Jetpack Compose (Material 3) · MVVM · Room (SQLite) · min Android 8.0 (API 26) · target API 35 · currency MMK.

| Document | Contents |
|---|---|
| `docs/01_BUSINESS_RULES_AND_ARCHITECTURE.md` | Excel rules, issues found and decisions, architecture, **database design** |
| `docs/02_USER_GUIDE.md` | How to use every module |
| `docs/03_TEST_RESULTS.md` | Unit tests and the Excel-equivalence comparison |

## Building the APK

> **Status (v1.2):** phone/tablet adaptive layout, colours, Night mode and Scale & Layout on Android (see below). **v1.1:** the app compiles cleanly (`assembleDebug` / `assembleRelease`, no compiler warnings, no lint errors) and all 25 unit tests pass, including the v1 → v2 database migration; 550/550 values match the workbook. New in 1.1: Enroll No., several classes per student (each with its own academic year, start date and period), a Payables tab with multi-select payment, combined receipts, and Excel/CSV import for students, class enrollments, payments and fee rates. To build it yourself in Android Studio:

1. Install **Android Studio** (Ladybug 2024.2 or newer) – https://developer.android.com/studio
2. *File → Open* and choose this `SchoolFeeManager` folder. Let Gradle sync (needs internet the first time, ~5–10 min).
3. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
   The APK is at `app/build/outputs/apk/debug/app-debug.apk` (Android Studio shows a *locate* link).
4. Copy the APK to the phone and open it (allow *Install unknown apps* when asked).

Command line alternative (Android SDK installed, `ANDROID_HOME` set):
```
./gradlew test            # 25 unit tests (business rules, import/export, multi-class, migration)
./gradlew assembleRelease # app/build/outputs/apk/release/app-release.apk
```
The release build is signed with the debug key so it installs directly. Before publishing on Google Play, create your own signing key (*Build → Generate Signed App Bundle / APK*).

If the build reports an error, copy the first error message and send it back – it can be fixed quickly.

## Phone and tablet layout (new in 1.2)
The Android app now has the same look as the Windows version and adapts to the screen automatically:
* **Phone** (narrow screen): bottom navigation (Home, Students, Payables, Collection, Reports, Settings), cards and full-screen forms sized for one hand.
* **Tablet** (screen at least 600 dp wide and 480 dp high – most 7–13" tablets, and large phones/foldables in landscape): the Windows layout – school sidebar, wide tables with filters, the Fee Rates table. In portrait the sidebar is compact (icons + short names); in landscape it shows full names. Rotating the tablet switches at once.
* **Scale & Layout** (Settings → Display on a phone, the sidebar button on a tablet): size of everything (80–200 %), text size, screen layout (*Automatic* / *Phone* / *Tablet*), sidebar (*Automatic* / *Full* / *Compact*), 7 school colours, *Day* / *Night* / *Automatic* (follows the Android dark theme, the default). Remembered on the device.

## Windows version
The same app for Windows 10/11 (64-bit), built from the same source code (screens, rules, import/export, database logic).
* **Install:** run `SchoolFeeManager-1.2.0-Setup.exe` (no admin rights needed; installs to `%LOCALAPPDATA%\Programs\School Fee Manager`, with Start-menu and desktop shortcuts). Or unzip `SchoolFeeManager-1.2.0-Windows-portable.zip` anywhere and run `SchoolFeeManager.exe`.
* The installer is not code-signed, so Windows SmartScreen may say *"Windows protected your PC"* – click **More info → Run anyway**.
* **Data:** `%APPDATA%\SchoolFeeManager\school_fees.db` – kept when updating or uninstalling. The database format is identical to the phone's: a backup made on the phone (*Settings → Data → Back up now*) can be restored on the PC and vice versa (a 1.0 phone backup is upgraded automatically).
* **Printing / PDF:** receipts and reports open in the default browser with its print dialog (choose a printer or *Microsoft Print to PDF*). Report *PDF* export writes a PDF file directly.
* **Desktop layout:** school-blue sidebar (Dashboard, Students, Payments, Collection, Reports, Settings), File / View / Help menu bar, wide tables with filters, Tab between fields and Enter to log in, keyboard shortcuts (Ctrl+1…6 pages, Ctrl+P collect payment, Ctrl+N register student, Alt+← back).
* **Scale & Layout** (sidebar button, or View menu / Ctrl+,): size of everything (80–200 %), text size, full or compact sidebar, 7 school colours, Day / Night mode. Applied at once and remembered in `%APPDATA%\SchoolFeeManager\display.properties`. Ctrl+= / Ctrl+– / Ctrl+0 change the size; Ctrl+Shift+D toggles night mode.
* A Java runtime is included (OpenJDK 25 from the `jdk4py` package; licences in `runtime\legal`) – nothing else to install.

Build it (Linux or Windows, needs [NSIS](https://nsis.sourceforge.io) `makensis` on the PATH):
```
./gradlew :desktop:packageWindows   # desktop/build/windows/*.exe and *.zip
./gradlew :desktop:run              # run the desktop app on this computer
./gradlew :desktop:test             # repository + UI tests on the desktop build
```
Code layout: `desktop/` contains only the Windows-specific parts (`JdbcFeeStore` = SQLite via JDBC instead of Room, file dialogs, printing, PDF, window, menu bar and About box in `desktop/ui`); everything else – including the wide layout in `app/src/main/java/.../ui/wide` (sidebar shell, theme, Scale & Layout, table pages, Fee Rates table), which Android tablets use too – is compiled from `app/src/main/java`.

## First login
`admin` / `admin123` – you are forced to change it immediately.

## Project layout
```
app/src/main/java/com/school/feemanager/
  domain/   pure Kotlin business rules (FeeCalculator, ScheduleCalculator, Ledger, Reports, Money, PasswordHasher)
  io/       .xlsx reader/writer, workbook import/export, Excel/CSV module import + templates, receipt & report HTML
  data/     Room entities, DAOs, database + migration, FeeRepository (transactions, import, backup/restore)
  ui/       Compose screens + AppViewModel
  util/     PDF export, printing, app restart
app/src/test/  JUnit tests + the original workbook as a test resource
tools/excel_equivalence/  scripts used to compare the app with the workbook's formulas
```
