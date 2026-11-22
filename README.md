# School Fee Manager (Android)

Offline school fee management app converted from `School_Fee_Management.xlsx`.
Kotlin · Jetpack Compose (Material 3) · MVVM · Room (SQLite) · min Android 8.0 (API 26) · target API 35 · currency MMK.

| Document | Contents |
|---|---|
| `docs/01_BUSINESS_RULES_AND_ARCHITECTURE.md` | Excel rules, issues found and decisions, architecture, **database design** |
| `docs/02_USER_GUIDE.md` | How to use every module |
| `docs/03_TEST_RESULTS.md` | Unit tests and the Excel-equivalence comparison |

## Building the APK

> **Status:** the business logic (fees, schedules, balances, reports, Excel import/export) is compiled and tested – 18/18 unit tests pass, and 550/550 values match the workbook. The Android app itself has **not yet been compiled**: the build machine had no access to Google's Android repositories. Building it takes one step in Android Studio:

1. Install **Android Studio** (Ladybug 2024.2 or newer) – https://developer.android.com/studio
2. *File → Open* and choose this `SchoolFeeManager` folder. Let Gradle sync (needs internet the first time, ~5–10 min).
3. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
   The APK is at `app/build/outputs/apk/debug/app-debug.apk` (Android Studio shows a *locate* link).
4. Copy the APK to the phone and open it (allow *Install unknown apps* when asked).

Command line alternative (Android SDK installed, `ANDROID_HOME` set):
```
./gradlew test            # 18 business-rule tests
./gradlew assembleRelease # app/build/outputs/apk/release/app-release.apk
```
The release build is signed with the debug key so it installs directly. Before publishing on Google Play, create your own signing key (*Build → Generate Signed App Bundle / APK*).

If the build reports an error, copy the first error message and send it back – it can be fixed quickly.

## First login
`admin` / `admin123` – you are forced to change it immediately.

## Project layout
```
app/src/main/java/com/school/feemanager/
  domain/   pure Kotlin business rules (FeeCalculator, ScheduleCalculator, Ledger, Reports, Money, PasswordHasher)
  io/       .xlsx reader/writer, workbook import/export, receipt & report HTML
  data/     Room entities, DAOs, database, FeeRepository (transactions, import, backup/restore)
  ui/       Compose screens + AppViewModel
  util/     PDF export, printing, app restart
app/src/test/  JUnit tests + the original workbook as a test resource
tools/excel_equivalence/  scripts used to compare the app with the workbook's formulas
```
