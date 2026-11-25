import initSqlJs, { type Database, type SqlJsStatic, type SqlValue } from 'sql.js';
import { MIGRATIONS, SCHEMA_VERSION } from './schema';

let SQL: SqlJsStatic | null = null;

/** Loads the SQLite WebAssembly engine once. `wasmUrl` is only needed in the browser. */
export async function loadSqlEngine(wasmUrl?: string): Promise<SqlJsStatic> {
  if (!SQL) SQL = await initSqlJs(wasmUrl ? { locateFile: () => wasmUrl } : undefined);
  return SQL;
}

export type Row = Record<string, SqlValue>;

/** Thin typed wrapper around a sql.js database. */
export class Db {
  constructor(public raw: Database) {}

  static async create(bytes?: Uint8Array | null, wasmUrl?: string): Promise<Db> {
    const sql = await loadSqlEngine(wasmUrl);
    const db = new Db(bytes && bytes.length ? new sql.Database(bytes) : new sql.Database());
    db.migrate();
    return db;
  }

  migrate(): void {
    this.raw.exec('PRAGMA foreign_keys = ON;');
    const current = Number(this.one<{ v: number }>('PRAGMA user_version')?.v ?? 0);
    if (current > SCHEMA_VERSION) throw new Error('DB_TOO_NEW');
    for (let v = current + 1; v <= SCHEMA_VERSION; v++) {
      this.tx(() => {
        this.raw.exec(MIGRATIONS[v]);
        this.raw.exec(`PRAGMA user_version = ${v}`);
      });
    }
  }

  all<T = Row>(sql: string, params: SqlValue[] | Record<string, SqlValue> = []): T[] {
    const stmt = this.raw.prepare(sql);
    try {
      stmt.bind(params as never);
      const rows: T[] = [];
      while (stmt.step()) rows.push(stmt.getAsObject() as T);
      return rows;
    } finally {
      stmt.free();
    }
  }

  one<T = Row>(sql: string, params: SqlValue[] | Record<string, SqlValue> = []): T | undefined {
    return this.all<T>(sql, params)[0];
  }

  run(sql: string, params: SqlValue[] | Record<string, SqlValue> = []): void {
    this.raw.run(sql, params as never);
  }

  lastId(): number {
    return Number(this.one<{ id: number }>('SELECT last_insert_rowid() AS id')!.id);
  }

  /** Runs fn inside a transaction; rolls back on any error. Nested calls join the outer one. */
  private depth = 0;
  tx<T>(fn: () => T): T {
    if (this.depth > 0) return fn();
    this.raw.exec('BEGIN');
    this.depth++;
    try {
      const r = fn();
      this.raw.exec('COMMIT');
      return r;
    } catch (e) {
      this.raw.exec('ROLLBACK');
      throw e;
    } finally {
      this.depth--;
    }
  }

  export(): Uint8Array {
    return this.raw.export();
  }

  close(): void {
    this.raw.close();
  }
}

/** Validates that bytes are a SQLite file created by this application. */
export async function inspectBackup(bytes: Uint8Array, wasmUrl?: string): Promise<{ ok: true; records: number } | { ok: false; error: string }> {
  const header = new TextDecoder().decode(bytes.slice(0, 15));
  if (header !== 'SQLite format 3') return { ok: false, error: 'err.backupNotSqlite' };
  const sql = await loadSqlEngine(wasmUrl);
  let raw: Database | null = null;
  try {
    raw = new sql.Database(bytes);
    const db = new Db(raw);
    const ok = db.one<{ integrity_check: string }>('PRAGMA integrity_check')?.integrity_check;
    if (ok !== 'ok') return { ok: false, error: 'err.backupCorrupt' };
    const t = db.one<{ n: number }>("SELECT count(*) AS n FROM sqlite_master WHERE type='table' AND name='records'");
    if (!t || Number(t.n) !== 1) return { ok: false, error: 'err.backupWrongApp' };
    const v = Number(db.one<{ v: number }>('PRAGMA user_version')?.v ?? 0);
    if (v > SCHEMA_VERSION) return { ok: false, error: 'err.backupTooNew' };
    const n = Number(db.one<{ n: number }>('SELECT count(*) AS n FROM records')!.n);
    return { ok: true, records: n };
  } catch {
    return { ok: false, error: 'err.backupCorrupt' };
  } finally {
    raw?.close();
  }
}
