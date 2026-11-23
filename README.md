# School Fee Manager (Android)

Offline school fee management app converted from `School_Fee_Management.xlsx`.
Kotlin · Jetpack Compose (Material 3) · MVVM · Room (SQLite) · min Android 8.0 (API 26) · target API 35 · currency MMK.

| Document | Contents |
|---|---|
| `docs/01_BUSINESS_RULES_AND_ARCHITECTURE.md` | Excel rules, issues found and decisions, architecture, **database design** |
| `docs/02_USER_GUIDE.md` | How to use every module |
| `docs/03_TEST_RESULTS.md` | Unit tests and the Excel-equivalence comparison |

## Building the APK

> **Status (v1.1):** the app compiles cleanly (`assembleDebug` / `assembleRelease`, no compiler warnings, no lint errors) and all 25 unit tests pass, including the v1 → v2 database migration; 550/550 values match the workbook. New in 1.1: Enroll No., several classes per student (each with its own academic year, start date and period), a Payables tab with multi-select payment, combined receipts, and Excel/CSV import for students, class enrollments, payments and fee rates. To build it yourself in Android Studio:

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
