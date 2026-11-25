import { beforeEach, describe, expect, it } from 'vitest';
import { Db, inspectBackup } from '../src/db/database';
import { Repo, ValidationError } from '../src/db/repo';
import { buildSampleData } from '../src/db/sample';

let repo: Repo;
beforeEach(async () => {
  repo = new Repo(await Db.create());
});

const r2 = (number: string, amount: number, extra: Partial<{ draw_date: string; session: 'AM' | 'PM'; customer: string }> = {}) => ({
  type: '2D' as const,
  draw_date: extra.draw_date ?? '2026-11-24',
  session: extra.session ?? ('AM' as const),
  number,
  amount,
  customer: extra.customer ?? '',
});
const r3 = (number: string, amount: number, draw_date = '2026-11-24', customer = '') => ({ type: '3D' as const, draw_date, session: null, number, amount, customer });

describe('repository', () => {
  it('adds, edits and deletes records with history', () => {
    const [id] = repo.addRecords([r2('25', 1000)]);
    expect(repo.getRecord(id)).toMatchObject({ number: '25', amount: 1000, session: 'AM' });
    repo.updateRecord(id, { ...r2('52', 2500), session: 'PM' });
    expect(repo.getRecord(id)).toMatchObject({ number: '52', amount: 2500, session: 'PM' });
    expect(repo.deleteRecords([id])).toBe(1);
    expect(repo.getRecord(id)).toBeUndefined();
    const h = repo.history();
    expect(h.map((x) => x.action)).toEqual(['DELETE', 'UPDATE', 'CREATE']);
    const newId = repo.undoDelete(h[0].id);
    expect(repo.getRecord(newId)).toMatchObject({ number: '52', amount: 2500 });
  });

  it('rejects invalid input and leaves the database untouched (atomic batch)', () => {
    expect(() => repo.addRecords([r2('12', 100), r2('123', 100)])).toThrow(ValidationError);
    expect(() => repo.addRecords([r2('12', 0)])).toThrow(ValidationError);
    expect(() => repo.addRecords([r3('12', 100)])).toThrow(ValidationError);
    expect(() => repo.addRecords([{ ...r2('12', 100), session: null }])).toThrow(ValidationError);
    expect(repo.allTimeCount()).toBe(0);
  });

  it('database CHECK constraints block bad rows even when validation is bypassed', () => {
    expect(() =>
      repo.db.run("INSERT INTO records(type,draw_date,session,number,amount,created_at,updated_at) VALUES ('2D','2026-01-01','AM','1',100,'x','x')"),
    ).toThrow();
    expect(() =>
      repo.db.run("INSERT INTO records(type,draw_date,session,number,amount,created_at,updated_at) VALUES ('3D','2026-01-01','AM','123',100,'x','x')"),
    ).toThrow();
    expect(() =>
      repo.db.run("INSERT INTO records(type,draw_date,session,number,amount,created_at,updated_at) VALUES ('2D','2026-01-01','AM','12',10.5,'x','x')"),
    ).toThrow();
  });

  it('detects duplicates', () => {
    repo.addRecords([r2('25', 1000, { customer: 'Ko Zaw' })]);
    expect(repo.findDuplicates([r2('25', 1000, { customer: 'Ko Zaw' })])).toHaveLength(1);
    expect(repo.findDuplicates([r2('25', 1000, { customer: 'Ma Hla' })])).toHaveLength(0);
    expect(repo.findDuplicates([r2('25', 1000, { customer: 'Ko Zaw', session: 'PM' })])).toHaveLength(0);
    const id = repo.queryRecords({}).rows[0].id;
    expect(repo.findDuplicates([r2('25', 1000, { customer: 'Ko Zaw' })], id)).toHaveLength(0);
  });

  it('calculates totals exactly', () => {
    repo.addRecords([
      r2('25', 1000),
      r2('25', 2000, { session: 'PM' }),
      r2('07', 500, { customer: 'A' }),
      r3('123', 5000, '2026-11-24', 'A'),
      r3('123', 1500, '2026-11-25'),
      r2('25', 700, { draw_date: '2026-10-31' }),
    ]);
    const t = repo.totals('2026-11-24', '2026-11-24');
    expect(t).toMatchObject({ count: 4, total: 8500, t2d: 3500, t3d: 5000, am: 1500, pm: 2000, c2d: 3, c3d: 1 });
    expect(repo.byNumber('2D', '2026-11-01', '2026-11-30')).toEqual([
      { number: '07', count: 1, total: 500 },
      { number: '25', count: 2, total: 3000 },
    ]);
    expect(repo.byNumber('2D', '2026-11-01', '2026-11-30', 'PM')).toEqual([{ number: '25', count: 1, total: 2000 }]);
    expect(repo.byNumber('3D', '2026-01-01', '2026-12-31')).toEqual([{ number: '123', count: 2, total: 6500 }]);
    expect(repo.bySession('2026-11-01', '2026-11-30')).toEqual([
      { session: 'AM', count: 2, total: 1500 },
      { session: 'PM', count: 1, total: 2000 },
    ]);
    const months = repo.monthlyTotals('2026');
    expect(months.map((m) => [m.month, m.total])).toEqual([
      ['2026-10', 700],
      ['2026-11', 10000],
    ]);
    const days = repo.dailyTotals('2026-11-01', '2026-11-30');
    expect(days.map((d) => [d.date, d.total, d.t2d, d.t3d])).toEqual([
      ['2026-11-24', 8500, 3500, 5000],
      ['2026-11-25', 1500, 0, 1500],
    ]);
    const cust = repo.byCustomer('2026-01-01', '2026-12-31');
    expect(cust.find((c) => c.customer === 'A')).toMatchObject({ count: 2, t2d: 500, t3d: 5000, total: 5500 });
  });

  it('filters records', () => {
    repo.addRecords([r2('25', 1000, { customer: 'Ko Zaw' }), r2('52', 1000, { session: 'PM' }), r3('125', 100), r3('300', 100, '2026-12-01')]);
    expect(repo.queryRecords({ number: '25' }).rows).toHaveLength(1);
    expect(repo.queryRecords({ number: '25', numberContains: true }).rows).toHaveLength(2);
    expect(repo.queryRecords({ type: '3D' }).totals.total).toBe(200);
    expect(repo.queryRecords({ session: 'PM' }).rows[0].number).toBe('52');
    expect(repo.queryRecords({ from: '2026-12-01' }).rows[0].number).toBe('300');
    expect(repo.queryRecords({ customer: 'zaw' }).rows).toHaveLength(1);
    expect(repo.queryRecords({ limit: 2, offset: 0 }).rows).toHaveLength(2);
    expect(repo.queryRecords({ limit: 2, offset: 0 }).totals.count).toBe(4);
  });

  it('backs up and restores via SQLite bytes', async () => {
    repo.addRecords(buildSampleData(30, '2026-11-25'));
    const before = repo.totals();
    const bytes = repo.db.export();
    expect(await inspectBackup(bytes)).toEqual({ ok: true, records: before.count });
    const restored = new Repo(await Db.create(bytes));
    expect(restored.totals()).toEqual(before);
    expect((await inspectBackup(new TextEncoder().encode('not a database file at all'))).ok).toBe(false);
  });

  it('sample data matches independent JS totals', () => {
    const rows = buildSampleData(60, '2026-11-25');
    repo.addRecords(rows);
    const sum = rows.reduce((s, r) => s + Number(r.amount), 0);
    const t = repo.totals();
    expect(t.total).toBe(sum);
    expect(t.count).toBe(rows.length);
    expect(t.t2d + t.t3d).toBe(sum);
    expect(t.am + t.pm).toBe(t.t2d);
    const byNum = repo.byNumber('2D', '2000-01-01', '2100-01-01').reduce((s, r) => s + r.total, 0);
    expect(byNum).toBe(t.t2d);
  });

  it('stores settings', () => {
    repo.setSetting('lang', 'my');
    expect(repo.getSetting('lang')).toBe('my');
    repo.setSetting('lang', 'en');
    expect(repo.getSetting('lang')).toBe('en');
  });
});
