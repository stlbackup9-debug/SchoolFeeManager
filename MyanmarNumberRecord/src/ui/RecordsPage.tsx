import { useEffect, useMemo, useState } from 'react';
import { useApp, errMsg } from './ctx';
import { Field, Seg } from './Components';
import { RecordsTable, useDeleteRecords } from './RecordsTable';
import type { RecordFilter } from '../db/repo';
import { normalizeDigits } from '../lib/validation';
import { fileStamp } from '../lib/dates';
import { fmtMoney } from '../lib/format';
import { exportRecordsXlsx } from '../lib/export';

const PAGE = 50;

export function RecordsPage({ preset }: { preset?: Partial<RecordFilter> }) {
  const { repo, t, version, toast } = useApp();
  const del = useDeleteRecords();
  const [f, setF] = useState<RecordFilter>({ type: '', session: '', from: '', to: '', number: '', customer: '', sort: 'newest', numberContains: false, ...preset });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());

  useEffect(() => setPage(0), [f]);
  const set = <K extends keyof RecordFilter>(k: K, v: RecordFilter[K]) => setF((x) => ({ ...x, [k]: v }));

  const badRange = !!(f.from && f.to && f.from > f.to);
  const query = useMemo(
    () => ({ ...f, number: normalizeDigits(f.number ?? '').trim(), session: f.type === '3D' ? '' : f.session }) as RecordFilter,
    [f],
  );
  const result = useMemo(() => repo.queryRecords({ ...query, limit: PAGE, offset: page * PAGE }), [repo, version, query, page]);
  const pages = Math.max(1, Math.ceil(result.totals.count / PAGE));
  useEffect(() => {
    if (page >= pages) setPage(pages - 1);
  }, [page, pages]);
  // Drop selections that no longer exist.
  useEffect(() => setSelected(new Set()), [version]);

  async function exportXlsx() {
    try {
      const all = repo.queryRecords(query).rows;
      const f = await exportRecordsXlsx(all, t, `records-${fileStamp()}.xlsx`);
      toast(t('rep.exported', { f }));
    } catch (e) {
      toast(errMsg(t, e), 'err');
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('r.title')}</h1>
        <div className="head-actions">
          <button className="btn" onClick={exportXlsx} data-testid="records-export">
            ⬇ {t('c.excel')}
          </button>
        </div>
      </div>
      <div className="card filters" data-testid="filters">
        <div className="filter-grid">
          <Field label={t('r.searchNumber')}>
            <input className="input mono" inputMode="numeric" value={f.number} onChange={(e) => set('number', e.target.value)} placeholder="25 / 123" data-testid="f-number" />
          </Field>
          <Field label={t('c.type')}>
            <Seg name="f-type" value={f.type ?? ''} onChange={(v) => set('type', v)} options={[{ value: '', label: t('c.all') }, { value: '2D', label: '2D' }, { value: '3D', label: '3D' }]} />
          </Field>
          <Field label={t('c.session')}>
            <Seg
              name="f-session"
              value={f.type === '3D' ? '' : f.session ?? ''}
              onChange={(v) => set('session', v)}
              options={[{ value: '', label: t('c.all') }, { value: 'AM', label: t('c.morning') }, { value: 'PM', label: t('c.evening') }]}
            />
          </Field>
          <Field label={t('c.from')} error={badRange ? t('err.dateRange') : undefined}>
            <input className="input" type="date" value={f.from} onChange={(e) => set('from', e.target.value)} data-testid="f-from" />
          </Field>
          <Field label={t('c.to')}>
            <input className="input" type="date" value={f.to} onChange={(e) => set('to', e.target.value)} data-testid="f-to" />
          </Field>
          <Field label={t('r.customer')}>
            <input className="input" value={f.customer} onChange={(e) => set('customer', e.target.value)} data-testid="f-customer" />
          </Field>
          <Field label={t('r.sort')}>
            <select className="input" value={f.sort} onChange={(e) => set('sort', e.target.value as RecordFilter['sort'])}>
              <option value="newest">{t('r.sortNewest')}</option>
              <option value="oldest">{t('r.sortOldest')}</option>
              <option value="number">{t('r.sortNumber')}</option>
              <option value="amount_desc">{t('r.sortAmount')}</option>
            </select>
          </Field>
        </div>
        <div className="filter-foot">
          <label className="check">
            <input type="checkbox" checked={!!f.numberContains} onChange={(e) => set('numberContains', e.target.checked)} />
            {t('r.contains')}
          </label>
          <button className="btn ghost" onClick={() => setF({ type: '', session: '', from: '', to: '', number: '', customer: '', sort: 'newest', numberContains: false })}>
            {t('c.reset')}
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2 data-testid="records-summary">{t('r.matching', { n: result.totals.count, t: fmtMoney(result.totals.total) })}</h2>
          <div className="head-actions">
            {selected.size > 0 && (
              <button className="btn danger" onClick={() => void del([...selected])} data-testid="delete-selected">
                {t('r.deleteSelected', { n: selected.size })}
              </button>
            )}
          </div>
        </div>
        <div className="mini-totals">
          <span>2D: <b>{fmtMoney(result.totals.t2d)}</b></span>
          <span>3D: <b>{fmtMoney(result.totals.t3d)}</b></span>
          <span>{t('c.morning')}: <b>{fmtMoney(result.totals.am)}</b></span>
          <span>{t('c.evening')}: <b>{fmtMoney(result.totals.pm)}</b></span>
        </div>
        <RecordsTable rows={badRange ? [] : result.rows} selected={selected} onSelect={setSelected} />
        {pages > 1 && (
          <div className="pager">
            <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>
              ‹ {t('c.prev')}
            </button>
            <span>{t('c.page', { p: page + 1, n: pages })}</span>
            <button className="btn" disabled={page >= pages - 1} onClick={() => setPage(page + 1)} data-testid="next-page">
              {t('c.next')} ›
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
