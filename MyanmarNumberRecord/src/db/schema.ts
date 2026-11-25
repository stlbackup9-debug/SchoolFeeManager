// SQLite schema. Version is tracked with PRAGMA user_version so that future
// releases can migrate older backups safely.

export const SCHEMA_VERSION = 1;

export const MIGRATIONS: Record<number, string> = {
  1: `
  CREATE TABLE IF NOT EXISTS records (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    type        TEXT    NOT NULL CHECK (type IN ('2D','3D')),
    draw_date   TEXT    NOT NULL CHECK (draw_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    session     TEXT             CHECK (session IN ('AM','PM')),
    number      TEXT    NOT NULL,
    amount      INTEGER NOT NULL CHECK (typeof(amount) = 'integer' AND amount > 0),
    customer    TEXT    NOT NULL DEFAULT '',
    remark      TEXT    NOT NULL DEFAULT '',
    batch_id    TEXT,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    CHECK (
      (type = '2D' AND session IS NOT NULL AND length(number) = 2 AND number GLOB '[0-9][0-9]') OR
      (type = '3D' AND session IS NULL     AND length(number) = 3 AND number GLOB '[0-9][0-9][0-9]')
    )
  );
  CREATE INDEX IF NOT EXISTS idx_records_date    ON records(draw_date, type, session);
  CREATE INDEX IF NOT EXISTS idx_records_number  ON records(type, number);
  CREATE INDEX IF NOT EXISTS idx_records_customer ON records(customer);

  CREATE TABLE IF NOT EXISTS history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    at          TEXT    NOT NULL,
    action      TEXT    NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE','SAMPLE','CLEAR')),
    record_id   INTEGER,
    summary     TEXT    NOT NULL DEFAULT '',
    before_json TEXT,
    after_json  TEXT
  );
  CREATE INDEX IF NOT EXISTS idx_history_at ON history(at);

  CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
  `,
};
