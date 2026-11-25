// Builds printable/exportable report documents from the repository.
// The same ReportDoc is used for the on-screen view, print, PDF and Excel,
// so every output shows identical numbers.

import type { Repo } from '../db/repo';
import type { GameType, Session } from './validation';
import type { T } from './i18n';
import { fmtInt, fmtMoney } from './format';
import { nowStamp } from './dates';

export type ColKind = 'text' | 'num' | 'money' | 'int' | 'pct';
export interface Col { key: string; label: string; kind?: ColKind }
export type Cell = string | number;
export interface Section { title: string; columns: Col[]; rows: Record<string, Cell>[]; totals?: Record<string, Cell>; grid?: boolean }
export interface ReportDoc { title: string; subtitle: string; business: string; generated: string; sections: Section[]; fileBase: string }

export type ReportKind = 'daily' | 'byNumber' | 'byDate' | 'bySession' | 'monthly' | 'customers';
export interface ReportParams {
  kind: ReportKind;
  date: string;
  from: string;
  to: string;
  type: GameType;
  session: Session | '';
  year: string;
  customerType: GameType | '';
}

const pct = (part: number, whole: number) => (whole ? Math.round((part / whole) * 10000) / 100 : 0);

function numberSection(t: T, title: string, rows: { number: string; count: number; total: number }[]): Section {
  const sum = rows.reduce((s, r) => s + r.total, 0);
  const cnt = rows.reduce((s, r) => s + r.count, 0);
  return {
    title,
    columns: [
      { key: 'number', label: t('c.number'), kind: 'num' },
      { key: 'count', label: t('c.count'), kind: 'int' },
      { key: 'total', label: t('c.amount'), kind: 'money' },
      { key: 'share', label: t('rep.share'), kind: 'pct' },
    ],
    rows: rows.map((r) => ({ number: r.number, count: r.count, total: r.total, share: pct(r.total, sum) })),
    totals: { number: t('c.total'), count: cnt, total: sum, share: rows.length ? 100 : 0 },
  };
}

function gridSection(t: T, rows: { number: string; total: number }[]): Section {
  const map = new Map(rows.map((r) => [r.number, r.total]));
  const columns: Col[] = [{ key: 'row', label: '', kind: 'num' }];
  for (let u = 0; u < 10; u++) columns.push({ key: 'u' + u, label: String(u), kind: 'money' });
  columns.push({ key: 'sum', label: t('c.total'), kind: 'money' });
  const out: Record<string, Cell>[] = [];
  const colSums = Array(10).fill(0);
  for (let tens = 0; tens < 10; tens++) {
    const row: Record<string, Cell> = { row: `${tens}x` };
    let s = 0;
    for (let u = 0; u < 10; u++) {
      const v = map.get(`${tens}${u}`) ?? 0;
      row['u' + u] = v || '';
      s += v;
      colSums[u] += v;
    }
    row.sum = s;
    out.push(row);
  }
  const totals: Record<string, Cell> = { row: t('c.total'), sum: colSums.reduce((a, b) => a + b, 0) };
  colSums.forEach((v, u) => (totals['u' + u] = v));
  return { title: t('rep.grid'), columns, rows: out, totals, grid: true };
}

export function buildReport(repo: Repo, p: ReportParams, t: T): ReportDoc {
  const business = repo.getSetting('business', '');
  const generated = nowStamp();
  const range = p.from === p.to ? p.from : `${p.from} → ${p.to}`;
  const sessLabel = (s: Session | '' | null) => (s === 'AM' ? t('c.morning') : s === 'PM' ? t('c.evening') : t('c.all'));

  switch (p.kind) {
    case 'daily': {
      const d = p.date;
      const am = repo.byNumber('2D', d, d, 'AM');
      const pm = repo.byNumber('2D', d, d, 'PM');
      const d3 = repo.byNumber('3D', d, d);
      const tot = repo.totals(d, d);
      const s = (rows: { count: number; total: number }[]) => [rows.reduce((a, r) => a + r.count, 0), rows.reduce((a, r) => a + r.total, 0)];
      const [amC, amT] = s(am), [pmC, pmT] = s(pm), [d3C, d3T] = s(d3);
      const list = repo.queryRecords({ from: d, to: d, sort: 'oldest' }).rows;
      return {
        title: t('rep.daily'),
        subtitle: d,
        business,
        generated,
        fileBase: `daily-report-${d}`,
        sections: [
          {
            title: t('rep.summary'),
            columns: [
              { key: 'label', label: t('c.type') },
              { key: 'count', label: t('c.count'), kind: 'int' },
              { key: 'total', label: t('c.amount'), kind: 'money' },
            ],
            rows: [
              { label: t('rep.2dMorning'), count: amC, total: amT },
              { label: t('rep.2dEvening'), count: pmC, total: pmT },
              { label: t('rep.2dTotal'), count: amC + pmC, total: amT + pmT },
              { label: t('rep.3dTotal'), count: d3C, total: d3T },
            ],
            totals: { label: t('rep.grandTotal'), count: tot.count, total: tot.total },
          },
          numberSection(t, `${t('rep.2dMorning')} – ${t('rep.byNumber')}`, am),
          numberSection(t, `${t('rep.2dEvening')} – ${t('rep.byNumber')}`, pm),
          numberSection(t, `${t('rep.3d')} – ${t('rep.byNumber')}`, d3),
          {
            title: t('nav.records'),
            columns: [
              { key: 'id', label: '#', kind: 'int' },
              { key: 'type', label: t('c.type') },
              { key: 'session', label: t('c.session') },
              { key: 'number', label: t('c.number'), kind: 'num' },
              { key: 'amount', label: t('c.amount'), kind: 'money' },
              { key: 'customer', label: t('c.customer') },
              { key: 'remark', label: t('c.remark') },
            ],
            rows: list.map((r) => ({ id: r.id, type: r.type, session: r.session ? sessLabel(r.session) : '-', number: r.number, amount: r.amount, customer: r.customer, remark: r.remark })),
            totals: { id: '', type: t('c.total'), amount: tot.total },
          },
        ],
      };
    }
    case 'byNumber': {
      const rows = repo.byNumber(p.type, p.from, p.to, p.type === '2D' ? p.session : '');
      const sections = [numberSection(t, `${p.type} – ${t('rep.byNumber')}`, rows)];
      if (p.type === '2D') sections.unshift(gridSection(t, rows));
      return {
        title: `${t('rep.byNumber')} (${p.type})`,
        subtitle: `${range}${p.type === '2D' ? ' · ' + sessLabel(p.session) : ''}`,
        business,
        generated,
        fileBase: `by-number-${p.type}-${p.from}_${p.to}`,
        sections,
      };
    }
    case 'byDate': {
      const rows = repo.dailyTotals(p.from, p.to);
      const sum = repo.totals(p.from, p.to);
      return {
        title: t('rep.byDate'),
        subtitle: range,
        business,
        generated,
        fileBase: `by-date-${p.from}_${p.to}`,
        sections: [
          {
            title: t('rep.byDate'),
            columns: [
              { key: 'date', label: t('c.date') },
              { key: 'count', label: t('c.count'), kind: 'int' },
              { key: 'am', label: t('rep.2dMorning'), kind: 'money' },
              { key: 'pm', label: t('rep.2dEvening'), kind: 'money' },
              { key: 't2d', label: t('rep.2dTotal'), kind: 'money' },
              { key: 't3d', label: t('rep.3dTotal'), kind: 'money' },
              { key: 'total', label: t('c.total'), kind: 'money' },
            ],
            rows: rows.map((r) => ({ date: r.date, count: r.count, am: r.am, pm: r.pm, t2d: r.t2d, t3d: r.t3d, total: r.total })),
            totals: { date: t('c.total'), count: sum.count, am: sum.am, pm: sum.pm, t2d: sum.t2d, t3d: sum.t3d, total: sum.total },
          },
        ],
      };
    }
    case 'bySession': {
      const rows = repo.bySession(p.from, p.to);
      const sum = rows.reduce((a, r) => a + r.total, 0);
      const daily = repo.dailyTotals(p.from, p.to).filter((r) => r.c2d > 0);
      return {
        title: t('rep.bySession'),
        subtitle: range,
        business,
        generated,
        fileBase: `by-session-${p.from}_${p.to}`,
        sections: [
          {
            title: t('rep.summary'),
            columns: [
              { key: 'session', label: t('c.session') },
              { key: 'count', label: t('c.count'), kind: 'int' },
              { key: 'total', label: t('c.amount'), kind: 'money' },
              { key: 'share', label: t('rep.share'), kind: 'pct' },
            ],
            rows: rows.map((r) => ({ session: sessLabel(r.session), count: r.count, total: r.total, share: pct(r.total, sum) })),
            totals: { session: t('c.total'), count: rows.reduce((a, r) => a + r.count, 0), total: sum, share: sum ? 100 : 0 },
          },
          {
            title: t('rep.byDate'),
            columns: [
              { key: 'date', label: t('c.date') },
              { key: 'am', label: t('c.morning'), kind: 'money' },
              { key: 'pm', label: t('c.evening'), kind: 'money' },
              { key: 't2d', label: t('c.total'), kind: 'money' },
            ],
            rows: daily.map((r) => ({ date: r.date, am: r.am, pm: r.pm, t2d: r.t2d })),
            totals: { date: t('c.total'), am: rows[0].total, pm: rows[1].total, t2d: sum },
          },
        ],
      };
    }
    case 'monthly': {
      const data = new Map(repo.monthlyTotals(p.year).map((m) => [m.month, m]));
      const rows: Record<string, Cell>[] = [];
      const acc = { count: 0, t2d: 0, t3d: 0, total: 0 };
      for (let m = 1; m <= 12; m++) {
        const key = `${p.year}-${String(m).padStart(2, '0')}`;
        const r = data.get(key);
        const row = { month: key, count: r?.count ?? 0, am: r?.am ?? 0, pm: r?.pm ?? 0, t2d: r?.t2d ?? 0, t3d: r?.t3d ?? 0, total: r?.total ?? 0 };
        acc.count += row.count; acc.t2d += row.t2d; acc.t3d += row.t3d; acc.total += row.total;
        rows.push(row);
      }
      const am = rows.reduce((a, r) => a + Number(r.am), 0);
      const pm = rows.reduce((a, r) => a + Number(r.pm), 0);
      return {
        title: t('rep.monthly'),
        subtitle: p.year,
        business,
        generated,
        fileBase: `monthly-${p.year}`,
        sections: [
          {
            title: t('rep.monthly'),
            columns: [
              { key: 'month', label: t('c.month') },
              { key: 'count', label: t('c.count'), kind: 'int' },
              { key: 'am', label: t('rep.2dMorning'), kind: 'money' },
              { key: 'pm', label: t('rep.2dEvening'), kind: 'money' },
              { key: 't2d', label: t('rep.2dTotal'), kind: 'money' },
              { key: 't3d', label: t('rep.3dTotal'), kind: 'money' },
              { key: 'total', label: t('c.total'), kind: 'money' },
            ],
            rows,
            totals: { month: t('c.total'), ...acc, am, pm },
          },
        ],
      };
    }
    case 'customers': {
      const rows = repo.byCustomer(p.from, p.to, p.customerType);
      const s = rows.reduce((a, r) => ({ count: a.count + r.count, t2d: a.t2d + r.t2d, t3d: a.t3d + r.t3d, total: a.total + r.total }), { count: 0, t2d: 0, t3d: 0, total: 0 });
      return {
        title: t('rep.customers'),
        subtitle: `${range}${p.customerType ? ' · ' + p.customerType : ''}`,
        business,
        generated,
        fileBase: `customers-${p.from}_${p.to}`,
        sections: [
          {
            title: t('rep.customers'),
            columns: [
              { key: 'customer', label: t('c.customer') },
              { key: 'count', label: t('c.count'), kind: 'int' },
              { key: 't2d', label: '2D', kind: 'money' },
              { key: 't3d', label: '3D', kind: 'money' },
              { key: 'total', label: t('c.total'), kind: 'money' },
              { key: 'first', label: t('rep.firstDate') },
              { key: 'last', label: t('rep.lastDate') },
            ],
            rows: rows.map((r) => ({ ...r, customer: r.customer || t('c.noCustomer') })),
            totals: { customer: t('c.total'), ...s, first: '', last: '' },
          },
        ],
      };
    }
  }
}

// ---------- HTML rendering (screen preview, print and PDF) ----------

const esc = (s: unknown) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

export function formatCell(v: Cell | undefined, kind: ColKind = 'text'): string {
  if (v === undefined || v === '') return '';
  if (typeof v === 'number') {
    if (kind === 'money') return fmtMoney(v);
    if (kind === 'int') return fmtInt(v);
    if (kind === 'pct') return v.toFixed(2) + '%';
  }
  return String(v);
}

export function reportHtml(doc: ReportDoc, t: T): string {
  const sec = doc.sections
    .map((s) => {
      const head = s.columns.map((c) => `<th class="k-${c.kind ?? 'text'}">${esc(c.label)}</th>`).join('');
      const body = s.rows.length
        ? s.rows.map((r) => `<tr>${s.columns.map((c) => `<td class="k-${c.kind ?? 'text'}">${esc(formatCell(r[c.key], c.kind))}</td>`).join('')}</tr>`).join('')
        : `<tr><td colspan="${s.columns.length}" class="empty">${esc(t('c.noData'))}</td></tr>`;
      const foot = s.totals ? `<tfoot><tr>${s.columns.map((c) => `<td class="k-${c.kind ?? 'text'}">${esc(formatCell(s.totals![c.key], c.kind))}</td>`).join('')}</tr></tfoot>` : '';
      return `<section class="rep-sec"><h3>${esc(s.title)}</h3><table class="rep-table${s.grid ? ' grid' : ''}"><thead><tr>${head}</tr></thead><tbody>${body}</tbody>${foot}</table></section>`;
    })
    .join('');
  return `<div class="report-doc">
    <header class="rep-head">
      ${doc.business ? `<div class="rep-biz">${esc(doc.business)}</div>` : ''}
      <h2>${esc(doc.title)}</h2>
      <div class="rep-sub">${esc(doc.subtitle)}</div>
      <div class="rep-gen">${esc(t('c.generatedAt'))}: ${esc(doc.generated)} (${esc(t('c.kyat'))})</div>
    </header>${sec}</div>`;
}
