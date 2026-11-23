# Test Results – 23 Nov 2026 (version 1.1)

## What was tested, and how

| Layer | How | Result |
|---|---|---|
| Fee calculation, schedules, payment allocation, balances, dashboard, all 10 reports, receipts, password hashing | 15 JUnit tests in `app/src/test` (`BusinessRulesTest`) compiled with Kotlin 2.0.21 and run on JVM 21 | **15 / 15 pass** |
| Excel import of the real `School_Fee_Management.xlsx`, export → re-import round trip (now incl. a student in two classes, a combined receipt, Enroll No., academic year, start date, period), import validation / duplicate detection | 3 JUnit tests (`WorkbookTest`) | **3 / 3 pass** |
| App calculations vs the workbook's own formulas | 50 test students (every class × Day/Hostel × New/Old × Myanmar/Intl, discounts 0–33.3 %, both terms) + 60 cash rows written into a copy of the workbook, recalculated by LibreOffice, then compared cell by cell with the app engine (`tools/excel_equivalence`) | **550 / 550 cells identical** (with the two approved formula fixes) |
| Multiple classes per student, per-class start date / period, payables, combined receipts, Enroll No. search, Excel/CSV module import (all 4 modules, templates, CSV quoting, aliases), student matching | 6 JUnit tests (`MultiClassTest`) | **6 / 6 pass** |
| Database migration v1 → v2 | `MigrationTest`: builds a v1 database from Room's exported schema with students, fees, schedules, receipts (incl. voided and a receipt-less transaction), runs the migration SQL on real SQLite, compares every table/column/key/index with a fresh v2 database and checks every migrated value | **1 / 1 pass** |
| Build | `./gradlew test lintDebug assembleDebug assembleRelease` with Android SDK 35, AGP 8.7.3, Kotlin 2.0.21 | **Success** – no compiler warnings, 0 lint errors |
| Android UI, Room repository on a device, printing, backup/restore | **Not run** – no Android emulator/device is available in the build environment (no hardware virtualisation). The repository code (payments, imports) compiles and was reviewed but is not covered by an automated test here. | Pending – try on a phone |

## Specification test scenarios

| # | Scenario | Covered by | Result |
|---|---|---|---|
| 1 | Register a new Day student | `scenario1_newDayStudent` (G-3 New Day Myanmar = 550,000) | Pass |
| 2 | Register a new Hostel student | `scenario2_…` (KG New Hostel Intl = 2,118,000 incl. KG hostel fee) | Pass |
| 3 | Register an Old student | `scenario3_…` (no entrance fee) | Pass |
| 4 | Change book type Myanmar → International | `scenario4_…` (G-5 Day 36,000 → 44,000; Hostel 39,000 → 48,000) | Pass |
| 5 | Apply discounts | `scenario5_…` (10 %, 12.5 %, 100 %, validation > 100 %) | Pass |
| 6 | Fees for different classes | `scenario6_everyClass` (all 13 classes) | Pass |
| 7 | Three-installment schedule | `scenario7_…` (1,000,000 → 333,333 / 333,333 / 333,334; due dates) | Pass |
| 8 | Monthly schedule | `scenario8_…` (12 × 83,333 + 83,337; tiny amounts never negative) | Pass |
| 9 | Partial payments | `scenario9_…` (balance, Partially Paid, Overdue) | Pass |
| 10 | Full payments | `scenario10_…` (Fully Paid, spill-over to next installment) | Pass |
| 11 | Outstanding balances | `scenario11_…` (5 % discount hostel monthly; voided payment excluded) | Pass |
| 12 | Receipts | `scenario12_receipt` (all required receipt fields, duplicate marking) | Pass |
| 13 | Daily collection report | `scenario13_…` (daily/monthly totals, dashboard figures, unpaid/partial reports, every report with filters) | Pass |
| 14 | Import existing workbook | `scenario14_importOriginalWorkbook` (all 5 sheets found, every rate read correctly, 0 errors) | Pass |
| 15 | Export back to Excel | `scenario15_exportAndReimportRoundTrip` (5 sheets, students/receipts/dates/amounts survive re-import; every report exports) | Pass |

## Excel equivalence detail
```
== FIXED formulas (approved): 50 students x 11 columns = 550 cells: exact 550, within 0.5 kyat 0, different 0
   differences by column: none
   schedules: 19 identical to Excel, 31 identical except last part absorbs Excel rounding gap
== ORIGINAL formulas: 50 students x 11 columns = 550 cells: exact 308, within 0.5 kyat 0, different 242
   differences by column: {'Instrument': 50, 'Gross': 50, 'Net': 50, 'Balance': 50, 'DiscAmt': 40, 'Hostel': 2}
   schedules: 0 identical to Excel, 0 identical except last part absorbs Excel rounding gap
```

* **With the approved fixes** the app reproduces every fee, gross, discount, net, paid and balance value the workbook calculates.
* **Against the original formulas** the only differences are the two errors approved for correction: the instrument fee (0 in Excel for all 50 students) and the KG hostel fee (missing for the 2 KG hostel students). Gross, Net, Discount and Balance differ only as a knock-on effect. On these 50 test students the original workbook understated total receivable by **10,017,600 MMK** (57,885,000 vs 67,902,600).
* Schedules: parts 1…n-1 always equal Excel's `ROUND(Net/n)`. In 31 of 50 cases Excel's parts did not add up to Net; the app's last part absorbs that 1–6 kyat gap (approved).

## Unit test log
```
BusinessRulesTest  15 tests  0 failures
MigrationTest       1 test   0 failures
MultiClassTest      6 tests  0 failures
WorkbookTest        3 tests  0 failures
RESULT: 25 passed, 0 failed
```
