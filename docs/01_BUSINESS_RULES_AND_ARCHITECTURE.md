# School Fee Manager – Business Rules & Architecture

Source: `School_Fee_Management.xlsx` (5 worksheets: Dashboard, Student_Register, Payment_Schedule, Cash_Collection, Fee_Rates).
The workbook supplied is an empty template (no student or cash rows), so the rate card is the only data it contains.

## 1. Business rules found in the workbook

### Fee_Rates (all MMK, per year unless stated)

| Class | Training | Book Day-MM | Book Day-Intl | Book Hostel-MM | Book Hostel-Intl | Hostel |
|---|---:|---:|---:|---:|---:|---:|
| KG | 150,000 | 20,000 | 25,000 | 22,000 | 28,000 | 800,000 |
| G-1 | 180,000 | 25,000 | 30,000 | 27,000 | 33,000 | 850,000 |
| G-2 | 200,000 | 28,000 | 34,000 | 30,000 | 37,000 | 900,000 |
| G-3 | 220,000 | 30,000 | 36,000 | 33,000 | 40,000 | 950,000 |
| G-4 | 240,000 | 33,000 | 40,000 | 36,000 | 44,000 | 1,000,000 |
| G-5 | 260,000 | 36,000 | 44,000 | 39,000 | 48,000 | 1,050,000 |
| G-6 | 280,000 | 40,000 | 48,000 | 43,000 | 53,000 | 1,100,000 |
| G-7 | 300,000 | 44,000 | 53,000 | 47,000 | 58,000 | 1,150,000 |
| G-8 | 320,000 | 48,000 | 58,000 | 52,000 | 64,000 | 1,200,000 |
| G-9 | 340,000 | 53,000 | 64,000 | 57,000 | 70,000 | 1,250,000 |
| G-10 | 380,000 | 60,000 | 72,000 | 65,000 | 78,000 | 1,300,000 |
| G-11 | 420,000 | 65,000 | 78,000 | 70,000 | 85,000 | 1,350,000 |
| G-12 | 450,000 | 70,000 | 85,000 | 75,000 | 92,000 | 1,400,000 |

Instrument fee 200,000 (all students) · Laundry 70,000/month × 12 months (hostel only) · Entrance fee 100,000 (new students only).

### Student_Register formulas (re-implemented in `domain/FeeCalculator.kt`)

| Col | Excel | App |
|---|---|---|
| G Training | `VLOOKUP(class, Fee_Rates!A6:B18)` | same |
| H Instrument | `Fee_Rates!$G$7` | **`G6` – see issue 1** |
| I Book | Day/Hostel × Myanmar/Intl lookup in `K6:O18` | same |
| J Hostel | Hostel only, `VLOOKUP(class, Fee_Rates!A23:B35)` | **`A22:B34` – see issue 2** |
| K Laundry | Hostel only, `B37 × D37` | same |
| L Entrance | `IF(E="New", B40)` | same |
| M Gross | G+H+I+J+K+L | same |
| O Discount | M × N | same, rounded to whole kyat |
| P Net | M − O | same |
| R Paid | `SUMIF(Cash_Collection!B:B, name, E:E)` | sum of the student's non-voided transactions (matched by Student ID, not by name) |
| S Balance | P − R | same |

### Payment_Schedule
`3-Inst`: each part = `ROUND(Net/3, 0)`. `Monthly`: each part = `ROUND(Net/12, 0)`.

### Dashboard
Counts of students, Day/Hostel, New/Old; sums of Net (receivable), cash collected, balance; class-wise table (Day/Hostel count, Day/Hostel receivable, total, collected, balance).

## 2. Issues found and decisions (approved 22-Nov-2026)

| # | Issue in workbook | Effect in Excel | Decision |
|---|---|---|---|
| 1 | Instrument Fee formula points to `Fee_Rates!G7` (blank); the value is in `G6` | Every student charged **0** instrument fee instead of 200,000 | **Fixed** – app charges the Fee_Rates instrument fee |
| 2 | Hostel Fee lookup range `A23:B35` starts one row below KG (`A22`) | KG hostel students charged **no** hostel fee (800,000 missing) | **Fixed** – range covers KG |
| 3 | Installments rounded separately | Schedule can differ from Net by 1–6 kyat | **Last part absorbs the difference** so the schedule always totals Net |
| 4 | No due dates in the workbook | "Overdue" cannot be determined | **Academic-year start date** in Settings (default 1 June). 3-Inst due at start, +4, +8 months; Monthly due at start of each month 1–12 |
| 5 | Paid Amount matched by student **name** | Two students with the same name would share payments | App links payments to the Student ID. Import still matches by name (then class) as Excel does |
| 6 | Discount × Gross can produce fractions of a kyat | Excel keeps decimals | Rounded half-up to whole kyat (MMK has no sub-units). No difference occurred in testing |

Other interpretations (consistent with the Excel formulas):
* Blank Book Type → International (Excel's `IF(F="Myanmar", …, intl)` default). Blank New/Old → Old (no entrance fee). Import flags both as warnings.
* Dashboard counts (enrolled, Day, Hostel, New, Old) use **active** students; money totals include deactivated students so their unpaid balances are not lost.
* Fee assessments are **snapshots**: changing Fee Rates affects new registrations only. Existing students change only when an Admin chooses *Recalculate* (per student, or all), or edits a fee-relevant field (class, type, status, book type, discount) – with a confirmation showing old vs new Net.

## 3. Architecture

```
UI (Jetpack Compose, Material 3)          ui/*.kt
   │  observes StateFlow<AppSnapshot>, calls actions
AppViewModel (MVVM)                       ui/AppViewModel.kt
   │  session, role checks, error → snackbar
FeeRepository                             data/FeeRepository.kt
   │  every write in a Room transaction, audit log
Room (SQLite) DAOs + entities             data/*.kt
   │
Pure-Kotlin domain (no Android)           domain/*.kt, io/*.kt
   FeeCalculator · ScheduleCalculator · Ledger · Reports ·
   XlsxReader/Writer · WorkbookImporter/Exporter · HtmlDocs
```

* **Single source of truth**: the repository combines all tables into one `AppSnapshot` flow. Any change to students, rates or payments recomputes balances, schedule statuses, dashboard and reports automatically.
* **Money**: `Long` whole kyat everywhere; discount stored in basis points (`Int`, 10.5 % = 1050). No floating point.
* **Excel**: own dependency-free `.xlsx` reader/writer (zip + XML). Apache POI was not used – it is ~15 MB and depends on `java.awt`/StAX classes missing on Android.
* **Printing**: receipts and reports are rendered as HTML and sent to Android's print framework (any printer, or *Save as PDF*). Report PDF export uses `android.graphics.pdf.PdfDocument`.
* **Offline**: no network permission is requested.

## 4. Database design (Room, version 2)

| Table | Key columns | Notes |
|---|---|---|
| `school_settings` | id = 1 | school name/address/phone, instrument fee, laundry/month, default months, entrance fee, default academic-year start, receipt prefix, next receipt / student sequence |
| `fee_rates` | id, **className (unique)** | sortOrder, training, 4 book fees, hostel fee, **startMonth, startDay, periodMonths** (the class's academic year) |
| `students` | id, **code (unique, STU-0001)**, **enrollNo (unique when given)** | name, nameKey, remarks, active, registeredOn – identity only |
| `enrollments` | id, FK→students, **(studentId, className, academicYear) unique** | academicYear, startDate, periodMonths, type, New/Old, book, term, discountBp, remarks, active + the approved fee snapshot (each fee, gross, discount, net, assessedAt) |
| `payment_schedule` | id, **(enrollmentId, seq) unique**, FK→enrollments | label (Inst-1 / M-1), dueDate, amount |
| `receipts` | id, **receiptNo (unique)**, index on date | date, payerName, previousBalance / remainingBalance (sum over the students on the receipt), receivedBy, remarks, printCount, **voided / voidReason / voidedBy**, imported |
| `payment_transactions` | id, FK→receipts, FK→students, FK→enrollments | one line of a receipt: date, amount, scheduleSeq, paymentFor, remarks, receivedBy, createdAt, voided (follows the receipt), imported |
| `users` | id, **username (unique)** | fullName, role (ADMIN/CASHIER), PBKDF2 hash + salt, active, mustChangePassword |
| `audit_log` | id, index on at | username, action, details |

Relationships: `students 1–n enrollments 1–n payment_schedule`, `receipts 1–n payment_transactions n–1 enrollments`.
A receipt with several lines is a **combined receipt**; its lines may belong to several classes and several students.
All foreign keys are `RESTRICT` – nothing with payments can be deleted (students and enrollments are deactivated instead).

**Migration 1 → 2** (`data/Migrations.kt`): each v1 student keeps its id and becomes a student + enrollment with the same id (start = the school's academic-year start, period = months/year, fee snapshot copied); schedules move to that enrollment; each v1 receipt becomes a receipt with one line; fee_rates get the school start date and period. No amount, date or receipt number changes. `MigrationTest` runs the SQL on SQLite and compares the result with Room's v2 schema.

Accounting controls:
* Payment + receipt are written in **one database transaction**; receipt numbers come from a sequence and are checked against the unique index.
* Transactions are **never deleted**. A wrong receipt is *voided* by an Admin with a reason (all its lines); it stays in the register marked VOIDED and stops counting toward the balance.
* Paid amount and balance are **computed** from transactions every time (never stored), so they cannot drift.
* Overpayment requires an explicit second confirmation.
* Every change is written to `audit_log`.
* Dates are stored as epoch days (no time-zone shifts).
* Backup = single-file copy of the database (journal mode TRUNCATE) taken inside an exclusive transaction. Restore validates the file and keeps a safety copy of the current database first.

## 5. Payment allocation
Allocation is per class enrollment. Each payment line is applied first to the installment/month it was recorded against; any excess spills into the earliest parts of the same enrollment that still have a balance. Part status: **Fully Paid** (paid ≥ scheduled), **Overdue** (past due date and not fully paid), **Partially Paid**, **Unpaid**. Student status: Fully Paid when balance ≤ 0; Overdue if any part is overdue; otherwise Partially Paid / Unpaid.

## 6. Version 1.1 – multiple classes, combined receipts, Excel/CSV import (23-Nov-2026)

* **Enroll No.**: typed by the school on the student (optional, unique). The automatic Student ID stays.
* **Multiple classes**: a student can have any number of enrollments (several classes at once, or a new class each year). Each has its own academic year label, start date and period (1–24 months), defaulting from the class row in Fee Rates. Due dates: 3-Inst at start, +period/3, +2·period/3 (12 months → +0/+4/+8 as before); Monthly one per month of the period. Laundry = laundry/month × period (12 months → unchanged). With the default 12-month period every fee is identical to v1.0, so the Excel equivalence still holds.
* **Payables** tab lists every unpaid line of every active enrollment; any selection (one or several students) is paid on one receipt.
* **Combined receipt**: one receipt number with several lines; also, any set of existing receipts can be printed / saved as one combined document (summary + each receipt).
* **Import**: Students, Class enrollments, Payments and Fee rates from .xlsx or .csv (comma/semicolon/tab, UTF-8, quoted fields), with templates. Header names are matched loosely. Students are matched by Enroll No., then Student ID, then name (+ class). Payment rows with the same receipt number form one receipt. Every import is first run as a **dry run** inside a transaction that is rolled back, so the preview shows exactly what will be added; the real import is one transaction.
* Dashboard: "Total Students" counts students with an active enrollment; class / Day / Hostel / New / Old figures count enrollments.
