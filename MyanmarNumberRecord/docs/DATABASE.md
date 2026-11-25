# Database Structure

Engine: **SQLite 3** (sql.js 1.13, compiled to WebAssembly) running inside the app. No server and no internet are needed.
The whole database is a single SQLite file. It is stored on the device (IndexedDB, inside the app's private storage on Android) and is identical to the `.sqlite` backup file, so a backup can be opened with any SQLite tool (DB Browser for SQLite, `sqlite3` CLI…).

Schema version: `PRAGMA user_version = 1`. Migrations live in `src/db/schema.ts` and run automatically on start-up and on restore, so older backups are upgraded.

## Table `records`: one row per number entry

| Column | Type | Rules |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `type` | TEXT NOT NULL | `'2D'` or `'3D'` |
| `draw_date` | TEXT NOT NULL | `YYYY-MM-DD` (local calendar date) |
| `session` | TEXT | `'AM'` (Morning) / `'PM'` (Evening) for 2D; **must be NULL** for 3D |
| `number` | TEXT NOT NULL | 2D: exactly 2 digits `00`–`99`; 3D: exactly 3 digits `000`–`999` (text keeps leading zeros) |
| `amount` | INTEGER NOT NULL | whole Kyat, `> 0` (the app limits it to 100,000,000) |
| `customer` | TEXT NOT NULL DEFAULT '' | optional customer name |
| `remark` | TEXT NOT NULL DEFAULT '' | optional |
| `batch_id` | TEXT | shared by records saved together (multi-number entry) |
| `created_at`, `updated_at` | TEXT NOT NULL | `YYYY-MM-DD HH:MM:SS` |

Table-level CHECK constraint (`src/db/schema.ts`):
```sql
CHECK (
  (type = '2D' AND session IS NOT NULL AND length(number) = 2 AND number GLOB '[0-9][0-9]') OR
  (type = '3D' AND session IS NULL     AND length(number) = 3 AND number GLOB '[0-9][0-9][0-9]')
)
-- and: CHECK (typeof(amount) = 'integer' AND amount > 0)
```
Even if a bug bypassed the app's validation, SQLite itself would refuse an invalid row.

Indexes: `idx_records_date (draw_date, type, session)`, `idx_records_number (type, number)`, `idx_records_customer (customer)`.

## Table `history`: audit log

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `at` | TEXT | timestamp |
| `action` | TEXT | `CREATE`, `UPDATE`, `DELETE`, `RESTORE`, `SAMPLE`, `CLEAR` |
| `record_id` | INTEGER | affected record |
| `summary` | TEXT | human-readable description |
| `before_json` / `after_json` | TEXT | full row before/after (used to restore deleted records) |

## Table `settings`: key/value

| key | values |
|---|---|
| `lang` | `en` / `my` |
| `theme` | `light` / `dark` / `system` |
| `business` | business name for report headers |
| `dupCheck` | `1` / `0` |
| `lastType` | last used entry type |

## How calculations are done
All totals are calculated by SQLite with `SUM()` over the INTEGER `amount` column. That is exact integer arithmetic, with no floating-point rounding. Examples (`src/db/repo.ts`):

```sql
-- Totals for a period (dashboard, daily/weekly/monthly)
SELECT count(*) AS count, coalesce(sum(amount),0) AS total,
       coalesce(sum(CASE WHEN type='2D' THEN amount END),0) AS t2d,
       coalesce(sum(CASE WHEN type='3D' THEN amount END),0) AS t3d,
       coalesce(sum(CASE WHEN session='AM' THEN amount END),0) AS am,
       coalesce(sum(CASE WHEN session='PM' THEN amount END),0) AS pm
FROM records WHERE draw_date BETWEEN ? AND ?;

-- Total by number
SELECT number, count(*) AS count, sum(amount) AS total
FROM records WHERE type=? AND draw_date BETWEEN ? AND ? [AND session=?]
GROUP BY number ORDER BY number;

-- Monthly totals
SELECT substr(draw_date,1,7) AS month, ... FROM records
WHERE substr(draw_date,1,4)=? GROUP BY month;

-- Customer summary
SELECT customer, count(*), sum(CASE WHEN type='2D' ...), sum(CASE WHEN type='3D' ...), sum(amount), min(draw_date), max(draw_date)
FROM records WHERE ... GROUP BY customer ORDER BY 5 DESC;
```

Multi-number saves run in one transaction, so either all rows are saved or none are.

## Saving, backup and recovery
- **Autosave**: after every change the database file is written to storage (debounced ~250 ms, plus an immediate flush when the app is hidden or closed).
- **Daily snapshots**: on the first start each day, the previous database file is copied as a snapshot (7 kept). Snapshots are also taken before a restore and before "delete all".
- **Backup**: the exact SQLite file is downloaded/shared.
- **Restore**: the file must start with the `SQLite format 3` header, pass `PRAGMA integrity_check`, contain the `records` table and not be from a newer schema version. Only then is the user asked to confirm.
