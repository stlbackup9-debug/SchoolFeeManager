# Test Results

Date: 2026-11-25 · Node 22 · Chromium (Playwright)

## Unit tests: `npm test` (Vitest, real SQLite engine)

```
 ✓ tests/validation.test.ts  (8 tests)
 ✓ tests/repo.test.ts        (9 tests)
 ✓ tests/i18n.test.ts        (2 tests)
 Test Files  3 passed (3)
      Tests  19 passed (19)
```

Covered:
- 2D numbers 00–99 and 3D numbers 000–999; wrong lengths, letters, negatives and decimals are rejected; Myanmar digits are accepted.
- Amounts: thousands separators, zero, negative, decimal, `1e5`, empty and over-limit values.
- Dates, including leap years and invalid days. Reverse (R) and 3D permutations, including repeated digits. Multi-number parsing.
- Add / edit / delete with history log, and restoring a deleted record.
- A batch with one invalid row saves nothing (transaction rollback).
- SQLite CHECK constraints reject bad rows inserted directly.
- Duplicate detection (same / different customer / session, excluding the edited row).
- **Exact totals**: per day, 2D/3D, Morning/Evening, by number, by session, monthly, by customer.
- 60 days of sample data: the SQLite totals equal independent JavaScript sums (total = 2D + 3D, 2D = AM + PM, Σ by number = 2D total).
- Filters: exact or contains number search, type, session, date range, customer, paging.
- Backup bytes pass validation and restore to identical totals; a non-database file is rejected.
- Every translation key used in the UI exists.

## End-to-end tests: `npm run e2e` (the built app in Chromium)

```
  ✓ dashboard starts empty
  ✓ 2D bulk entry with R (reverse) saves 4 records
  ✓ duplicate entry is detected and can be cancelled
  ✓ invalid number and amount are rejected
  ✓ 3D entry saves
  ✓ dashboard totals are correct
  ✓ records: search, filter, edit, delete, undo
  ✓ records export to Excel
  ✓ daily report shows correct totals; Excel + PDF export
  ✓ other reports render
  ✓ backup, sample data, clear all, restore
  ✓ restore rejects a non-database file
  ✓ Myanmar language and dark mode
  ✓ data persists after reload (autosave)
  ✓ mobile layout has no horizontal page scroll
  ✓ single-file desktop build works from file:// and persists
  ✓ no JavaScript errors

17 passed, 0 failed
```

Exported Excel files were read back and checked: row counts, totals (8,500), leading zeros ("07") and the number of sheets. PDF files were checked for a valid header and inspected visually, including the Myanmar-language version (`docs/screenshots/sample-daily-report-myanmar.pdf`).

## Android build
`./gradlew assembleDebug` succeeds: package `com.mm.numberrecord`, minSdk 23 (Android 6.0), targetSdk 35. The APK contains the web app, the SQLite WASM engine and the Myanmar font.
The APK has **not been run on a phone or emulator** (the build machine has no emulator support). Its UI is the same build tested above in Chromium, the engine behind Android's WebView. Only the Android-specific parts are untested on a device: the share sheet used for exports and backup, the file picker used for restore, and the hardware back button.
