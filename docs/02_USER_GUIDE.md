# School Fee Manager – User Guide

## First start
1. Open the app and log in with **admin / admin123**.
2. You must set a new password immediately.
3. Go to **Settings → School profile** and enter the school name (printed on receipts), address, phone, receipt prefix and the default academic-year start.
4. Check **Settings → Fee rates**. They are pre-filled from the workbook's Fee_Rates sheet. Each class also has its own **academic-year start (month, day) and period (months)**.
5. Optional: **Settings → Data** to import students, classes, payments and fee rates from Excel (.xlsx) or CSV, or the whole `School_Fee_Management.xlsx`.
6. Create a Cashier account in **Settings → Users & roles**.

## Roles
| | Admin | Cashier |
|---|:-:|:-:|
| Dashboard, view students & schedules, reports, export | ✔ | ✔ |
| Record payments, print / reprint receipts | ✔ | ✔ |
| Register / edit / deactivate students, add / edit classes | ✔ | |
| Fee rates, recalculation | ✔ (Cashier can view rates) | |
| Users, school profile, Excel / CSV import, backup & restore, void receipts | ✔ | |

## Bottom navigation
**Home** (Dashboard) – totals (students, receivable, collected, outstanding, Day/Hostel), today's and this month's collection, receipts issued, students with balances, 6-month chart, class summary, recent transactions. Everything updates as soon as data changes.

**Students** – search by name, **Enroll No.** or Student ID; filter by class, Day/Hostel, payment status, inactive. Each row shows the student's classes and the balance over all classes. Tap a student for the profile.
* **Register** (Admin): **Enroll No.** (typed by you, optional, must be unique), name, remarks, and optionally the first class. The Student ID (STU-0001) is still generated automatically.
* **Student page**: profile, balance of all classes, one card per **class enrollment** (class, academic year, start date, period, type, term, fee breakdown, schedule with **Pay** buttons) and the payment history.
* **Add class** (Admin): a student can attend several classes at the same time, or a new class next year. Each enrollment has its **own academic year, start date and period (months)** – defaults come from the class in Fee Rates, and can be changed. Its fees and schedule are separate: 3-Inst is due at the start, +⅓ and +⅔ of the period; Monthly has one payment per month of the period. Laundry (hostel) is charged per month of the period.
* **Class ⋮ menu**: Edit (changing class, type, New/Old, book, discount or period re-assesses with current rates – old vs new Net is shown), Recalculate with current rates, Deactivate (student left that class; payments kept).
* The same name cannot be registered twice in the same class and academic year.

**Payables** – every installment / month that still has money to pay, for all students and all their classes, earliest due first. Filter by class, *Due by today*, Overdue / Unpaid / Partially Paid, or search. **Tick several items** – of one student or of several (e.g. brothers and sisters) – and tap **Pay selected**: they are collected together on **one combined receipt**.

**Collection** – the cash register.
* **Collect**: choose the student; all payable items of all their classes are listed with check boxes, plus an *other / advance payment* line per class. Tick one or more, adjust the amounts if needed, enter the date and confirm. One receipt is created for all ticked items (a *combined receipt* when there are several). If the total is more than the balance you must confirm the overpayment.
* **Receipt**: shows every line (student, class, payment for, amount). Tap **Print** to print, or choose *Save as PDF* in the print dialog. Reprints are marked *DUPLICATE COPY*.
* **Receipts tab**: search by receipt number, name, Enroll No.; date filters. **Long-press** a receipt (or tap ☑) to select several, then **Combined receipt** prints / saves them as one document: a summary page listing every line and the total, followed by each receipt. Admin can **void** a receipt (a reason is required); all its lines stop counting.
* **Daily / Monthly tabs**: collection summaries.

**Reports** – 10 reports. Each has filters (date range where relevant, class, student search, payment status, include inactive) and three buttons at the top: **Excel**, **PDF**, **Print**.

**Settings** – fee rates, recalculation, school profile, users, data (import/export/backup/restore), activity log, change password, log out.

## Changing fee rates
Saving new rates affects **new class enrollments only**. Existing enrollments keep their approved assessment; the class card shows what the current rates would give. To apply new rates use *Recalculate* on one class or **Settings → Recalculate existing classes** (all active enrollments) – a confirmation shows how many change and the total effect.

## Import from Excel or CSV (Settings → Data)
Each module has **Import…**, and a template as **.xlsx** or **.csv**. Fill the template in Excel or Google Sheets (keep the header row; extra columns are ignored, columns can be in any order, common alternative names like "Name" or "Enroll Number" are understood). Suggested order:
1. **Fee rates** – one row per class: training, 4 book fees, hostel fee, start month/day, period. Existing classes are updated, new ones added.
2. **Students** – Enroll No., name, remarks, and optionally a first class (class, academic year, start date, period, type, New/Old, book, term, discount). The same Enroll No. on several rows = one student in several classes.
3. **Class enrollments** – adds classes to existing students, identified by Enroll No., Student ID or name.
4. **Payments** – date, amount, student (Enroll No. / ID / name), class and academic year when the student has several classes, Payment For (Inst-1, M-3 …). **Rows with the same Receipt No. become one combined receipt**; receipt numbers already in the app are skipped.

Before anything is saved you see a **preview**: rows with errors, warnings, and exactly what the import will add (it is run against your data and undone). Tap **Import** to save. Importing the same file again does not create duplicates.

## Full workbook import
* Imports `Student_Register` (input columns B–F, N, Q, T plus Student ID, Enroll No., Academic Year, Start Date, Period when present), `Cash_Collection` (B–H) and `Fee_Rates`. `Payment_Schedule` and `Dashboard` are formulas, so they are regenerated by the app.
* Rows of the same student (same Student ID / Enroll No., or the same name when there are no IDs) become one student with several classes. Cash rows are linked by Enroll No. / Student ID, else by name (and class), like Excel's SUMIF.

## Excel export
**Settings → Data → Export workbook** writes the five sheets in the original layout (values), with **one Student_Register row per class enrollment** and extra columns (Student ID, Active, Enroll No., Academic Year, Start Date, Period). The file can be imported again.

## Backup & restore
**Back up now** saves one `.db` file containing everything. Keep backups off the phone (Google Drive, computer, USB). **Restore** replaces all data with a backup after confirmation; the app keeps a safety copy of the data it replaced and restarts. Backups made with version 1.0 can be restored – they are upgraded automatically.

## Upgrading from version 1.0
Installing 1.1 over 1.0 keeps all data. Every student becomes a student with one class enrollment (using the old academic-year start and 12 months), every receipt keeps its number, date, amount and balances. Enroll numbers are empty – add them on the student's Edit page or with a Students import.

## Phone / tablet layout and display
The app chooses its layout from the screen: phones get the bottom bar and cards; tablets get the school sidebar with wide tables (compact sidebar in portrait). On a phone open *Settings → Display*, on a tablet tap *Scale & Layout* in the sidebar to change the size of text and items, force the *Phone* or *Tablet* layout, pick one of 7 colours, and choose *Day*, *Night* or *Automatic* (follows the phone's dark theme). The moon/sun button in the sidebar switches night mode quickly.

## Windows version
Install with `SchoolFeeManager-1.2.0-Setup.exe` (or use the portable zip). It works exactly like the phone app; the bottom bar, screens and reports are the same. Differences: files are chosen with the normal Windows Open / Save dialogs; *Print* opens the receipt or report in your web browser with its print dialog (pick a printer or *Microsoft Print to PDF*). To move data between phone and PC, make a backup on one and *Restore* it on the other. Data lives in `%APPDATA%\SchoolFeeManager`.
