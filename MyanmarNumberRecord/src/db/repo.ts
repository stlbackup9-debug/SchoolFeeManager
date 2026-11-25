// Repository: every read/write of the application goes through here.
// All money arithmetic is done by SQLite on INTEGER columns, so totals are exact.

import type { SqlValue } from 'sql.js';
import { Db } from './database';
import {
  type GameType,
  type Session,
  cleanText,
  validateAmount,
  validateDate,
  validateNumber,
  validateSession,
} from '../lib/validation';
import { nowStamp } from '../lib/dates';

export interface RecordRow {
  id: number;
  type: GameType;
  draw_date: string;
  session: Session | null;
  number: string;
  amount: number;
  customer: string;
  remark: string;
  batch_id: string | null;
  created_at: string;
  updated_at: string;
}

export interface RecordInput {
  type: GameType;
  draw_date: string;
  session: Session | null;
  number: string;
  amount: number | string;
  customer?: string;
  remark?: string;
}

export class ValidationError extends Error {
  constructor(public field: string, public key: string) {
    super(key);
  }
}

export interface Totals {
  count: number;
  total: number;
  t2d: number;
  t3d: number;
  am: number;
  pm: number;
  c2d: number;
  c3d: number;
}

export interface RecordFilter {
  number?: string; // exact number
  numberContains?: boolean;
  type?: GameType | '';
  session?: Session | '';
  from?: string;
  to?: string;
  customer?: string; // contains, case-insensitive
  sort?: 'newest' | 'oldest' | 'number' | 'amount_desc';
  limit?: number;
  offset?: number;
}

type Listener = () => void;

export function normalizeInput(input: RecordInput): Omit<RecordRow, 'id' | 'batch_id' | 'created_at' | 'updated_at'> {
  const type = input.type;
  if (type !== '2D' && type !== '3D') throw new ValidationError('type', 'err.type');
  const d = validateDate(input.draw_date);
  if (!d.ok) throw new ValidationError('draw_date', d.error);
  const s = validateSession(type, input.session);
  if (!s.ok) throw new ValidationError('session', s.error);
  const n = validateNumber(type, input.number);
  if (!n.ok) throw new ValidationError('number', n.error);
  const a = validateAmount(input.amount);
  if (!a.ok) throw new ValidationError('amount', a.error);
  return {
    type,
    draw_date: d.value,
    session: s.value,
    number: n.value,
    amount: a.value,
    customer: cleanText(input.customer),
    remark: cleanText(input.remark),
  };
}

function totalsFrom(row: Record<string, SqlValue> | undefined): Totals {
  const g = (k: string) => Number(row?.[k] ?? 0) || 0;
  return { count: g('count'), total: g('total'), t2d: g('t2d'), t3d: g('t3d'), am: g('am'), pm: g('pm'), c2d: g('c2d'), c3d: g('c3d') };
}

const TOTALS_SQL = `
  SELECT count(*) AS count,
         coalesce(sum(amount),0) AS total,
         coalesce(sum(CASE WHEN type='2D' THEN amount END),0) AS t2d,
         coalesce(sum(CASE WHEN type='3D' THEN amount END),0) AS t3d,
         coalesce(sum(CASE WHEN session='AM' THEN amount END),0) AS am,
         coalesce(sum(CASE WHEN session='PM' THEN amount END),0) AS pm,
         coalesce(sum(type='2D'),0) AS c2d,
         coalesce(sum(type='3D'),0) AS c3d
  FROM records`;

export class Repo {
  private listeners = new Set<Listener>();
  constructor(public db: Db) {}

  onChange(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }
  private changed() {
    this.listeners.forEach((l) => l());
  }

  /** Replaces the underlying database (restore). */
  swap(db: Db) {
    this.db.close();
    this.db = db;
    this.changed();
  }

  private log(action: string, recordId: number | null, summary: string, before?: unknown, after?: unknown) {
    this.db.run('INSERT INTO history(at, action, record_id, summary, before_json, after_json) VALUES (?,?,?,?,?,?)', [
      nowStamp(),
      action,
      recordId,
      summary,
      before ? JSON.stringify(before) : null,
      after ? JSON.stringify(after) : null,
    ]);
  }

  // ---------- records CRUD ----------

  getRecord(id: number): RecordRow | undefined {
    return this.db.one<RecordRow>('SELECT * FROM records WHERE id = ?', [id]);
  }

  /** Inserts one or more records atomically. Returns the new ids. */
  addRecords(inputs: RecordInput[]): number[] {
    if (!inputs.length) return [];
    const rows = inputs.map(normalizeInput);
    const stamp = nowStamp();
    const batch = rows.length > 1 ? `B${Date.now().toString(36)}` : null;
    const ids = this.db.tx(() =>
      rows.map((r) => {
        this.db.run(
          `INSERT INTO records(type, draw_date, session, number, amount, customer, remark, batch_id, created_at, updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)`,
          [r.type, r.draw_date, r.session, r.number, r.amount, r.customer, r.remark, batch, stamp, stamp],
        );
        const id = this.db.lastId();
        this.log('CREATE', id, describe(r), undefined, { ...r, id });
        return id;
      }),
    );
    this.changed();
    return ids;
  }

  updateRecord(id: number, input: RecordInput): void {
    const before = this.getRecord(id);
    if (!before) throw new ValidationError('id', 'err.notFound');
    const r = normalizeInput(input);
    this.db.tx(() => {
      this.db.run(
        `UPDATE records SET type=?, draw_date=?, session=?, number=?, amount=?, customer=?, remark=?, updated_at=? WHERE id=?`,
        [r.type, r.draw_date, r.session, r.number, r.amount, r.customer, r.remark, nowStamp(), id],
      );
      this.log('UPDATE', id, `${describe(before)} → ${describe(r)}`, before, { ...r, id });
    });
    this.changed();
  }

  deleteRecords(ids: number[]): number {
    let n = 0;
    this.db.tx(() => {
      for (const id of ids) {
        const before = this.getRecord(id);
        if (!before) continue;
        this.db.run('DELETE FROM records WHERE id = ?', [id]);
        this.log('DELETE', id, describe(before), before);
        n++;
      }
    });
    if (n) this.changed();
    return n;
  }

  /**
   * Returns existing records that look like accidental duplicates of the inputs:
   * same type, date, session, number, amount and customer.
   */
  findDuplicates(inputs: RecordInput[], excludeId?: number): RecordRow[] {
    const out: RecordRow[] = [];
    const seen = new Set<number>();
    for (const input of inputs) {
      const r = normalizeInput(input);
      const rows = this.db.all<RecordRow>(
        `SELECT * FROM records WHERE type=? AND draw_date=? AND session IS ? AND number=? AND amount=? AND customer=? AND id IS NOT ?`,
        [r.type, r.draw_date, r.session, r.number, r.amount, r.customer, excludeId ?? null],
      );
      for (const row of rows) if (!seen.has(row.id)) {
        seen.add(row.id);
        out.push(row);
      }
    }
    return out;
  }

  private where(f: RecordFilter): { sql: string; params: SqlValue[] } {
    const w: string[] = [];
    const p: SqlValue[] = [];
    if (f.type) { w.push('type = ?'); p.push(f.type); }
    if (f.session) { w.push('session = ?'); p.push(f.session); }
    if (f.from) { w.push('draw_date >= ?'); p.push(f.from); }
    if (f.to) { w.push('draw_date <= ?'); p.push(f.to); }
    if (f.number) {
      if (f.numberContains) { w.push("instr(number, ?) > 0"); p.push(f.number); }
      else { w.push('number = ?'); p.push(f.number); }
    }
    if (f.customer) { w.push("instr(lower(customer), lower(?)) > 0"); p.push(f.customer.trim()); }
    return { sql: w.length ? ' WHERE ' + w.join(' AND ') : '', params: p };
  }

  queryRecords(f: RecordFilter): { rows: RecordRow[]; totals: Totals } {
    const { sql, params } = this.where(f);
    const order = {
      newest: 'draw_date DESC, id DESC',
      oldest: 'draw_date ASC, id ASC',
      number: 'type ASC, number ASC, draw_date ASC, id ASC',
      amount_desc: 'amount DESC, id DESC',
    }[f.sort ?? 'newest'];
    const limit = f.limit ?? 1_000_000;
    const rows = this.db.all<RecordRow>(`SELECT * FROM records${sql} ORDER BY ${order} LIMIT ? OFFSET ?`, [...params, limit, f.offset ?? 0]);
    const totals = totalsFrom(this.db.one(TOTALS_SQL + sql, params));
    return { rows, totals };
  }

  // ---------- summaries ----------

  totals(from?: string, to?: string): Totals {
    const { sql, params } = this.where({ from, to });
    return totalsFrom(this.db.one(TOTALS_SQL + sql, params));
  }

  allTimeCount(): number {
    return Number(this.db.one<{ n: number }>('SELECT count(*) AS n FROM records')!.n);
  }

  /** Per-day totals in a range (only days that have records). */
  dailyTotals(from: string, to: string): Array<{ date: string } & Totals> {
    const rows = this.db.all(
      TOTALS_SQL.replace('SELECT', 'SELECT draw_date AS date,') + ' WHERE draw_date BETWEEN ? AND ? GROUP BY draw_date ORDER BY draw_date',
      [from, to],
    );
    return rows.map((r) => ({ date: String(r.date), ...totalsFrom(r) }));
  }

  monthlyTotals(year: string): Array<{ month: string } & Totals> {
    const rows = this.db.all(
      TOTALS_SQL.replace('SELECT', "SELECT substr(draw_date,1,7) AS month,") +
        " WHERE substr(draw_date,1,4) = ? GROUP BY month ORDER BY month",
      [year],
    );
    return rows.map((r) => ({ month: String(r.month), ...totalsFrom(r) }));
  }

  /** Total amount per number. */
  byNumber(type: GameType, from: string, to: string, session?: Session | ''): Array<{ number: string; count: number; total: number }> {
    const { sql, params } = this.where({ type, from, to, session });
    return this.db
      .all(`SELECT number, count(*) AS count, sum(amount) AS total FROM records${sql} GROUP BY number ORDER BY number`, params)
      .map((r) => ({ number: String(r.number), count: Number(r.count), total: Number(r.total) }));
  }

  bySession(from: string, to: string): Array<{ session: Session; count: number; total: number }> {
    const rows = this.db.all(
      `SELECT session, count(*) AS count, sum(amount) AS total FROM records
       WHERE type='2D' AND draw_date BETWEEN ? AND ? GROUP BY session ORDER BY session`,
      [from, to],
    );
    const map = new Map(rows.map((r) => [String(r.session), r]));
    return (['AM', 'PM'] as Session[]).map((s) => ({ session: s, count: Number(map.get(s)?.count ?? 0), total: Number(map.get(s)?.total ?? 0) }));
  }

  byCustomer(from: string, to: string, type?: GameType | ''): Array<{ customer: string; count: number; t2d: number; t3d: number; total: number; first: string; last: string }> {
    const { sql, params } = this.where({ from, to, type });
    return this.db
      .all(
        `SELECT customer, count(*) AS count,
                coalesce(sum(CASE WHEN type='2D' THEN amount END),0) AS t2d,
                coalesce(sum(CASE WHEN type='3D' THEN amount END),0) AS t3d,
                sum(amount) AS total, min(draw_date) AS first, max(draw_date) AS last
         FROM records${sql} GROUP BY customer ORDER BY total DESC, customer`,
        params,
      )
      .map((r) => ({
        customer: String(r.customer),
        count: Number(r.count),
        t2d: Number(r.t2d),
        t3d: Number(r.t3d),
        total: Number(r.total),
        first: String(r.first),
        last: String(r.last),
      }));
  }

  topNumbers(type: GameType, from: string, to: string, limit = 5) {
    return this.byNumber(type, from, to)
      .sort((a, b) => b.total - a.total || a.number.localeCompare(b.number))
      .slice(0, limit);
  }

  customers(): string[] {
    return this.db.all<{ c: string }>("SELECT DISTINCT customer AS c FROM records WHERE customer <> '' ORDER BY c COLLATE NOCASE LIMIT 500").map((r) => r.c);
  }

  // ---------- history ----------

  history(limit = 200, offset = 0) {
    return this.db.all<{ id: number; at: string; action: string; record_id: number | null; summary: string; before_json: string | null }>(
      'SELECT id, at, action, record_id, summary, before_json FROM history ORDER BY id DESC LIMIT ? OFFSET ?',
      [limit, offset],
    );
  }

  /** Re-inserts a deleted record from the history log ("undo delete"). */
  undoDelete(historyId: number): number {
    const h = this.db.one<{ action: string; before_json: string | null }>('SELECT action, before_json FROM history WHERE id=?', [historyId]);
    if (!h || h.action !== 'DELETE' || !h.before_json) throw new ValidationError('history', 'err.notFound');
    const r = JSON.parse(h.before_json) as RecordRow;
    const [id] = this.addRecords([r]);
    this.db.run("UPDATE history SET action='RESTORE', summary = summary || ' (restored)' WHERE id = ?", [historyId]);
    this.changed();
    return id;
  }

  logEvent(action: 'SAMPLE' | 'CLEAR' | 'RESTORE', summary: string) {
    this.log(action, null, summary);
    this.changed();
  }

  clearAll() {
    this.db.tx(() => {
      this.db.run('DELETE FROM records');
      this.db.run('DELETE FROM history');
      this.log('CLEAR', null, 'All records cleared');
    });
    this.changed();
  }

  // ---------- settings ----------

  getSetting(key: string, fallback = ''): string {
    return this.db.one<{ value: string }>('SELECT value FROM settings WHERE key=?', [key])?.value ?? fallback;
  }

  setSetting(key: string, value: string) {
    this.db.run('INSERT INTO settings(key, value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value', [key, value]);
    this.changed();
  }
}

export function describe(r: { type: string; draw_date: string; session: string | null; number: string; amount: number; customer?: string }): string {
  return `${r.type} ${r.draw_date}${r.session ? ' ' + r.session : ''} #${r.number} = ${r.amount}${r.customer ? ' (' + r.customer + ')' : ''}`;
}
