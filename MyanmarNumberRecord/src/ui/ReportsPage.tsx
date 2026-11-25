import { useMemo, useState } from 'react';
import { useApp, errMsg } from './ctx';
import { Field, Seg } from './Components';
import { buildReport, reportHtml, type ReportKind, type ReportParams } from '../lib/reports';
import { startOfMonth, today } from '../lib/dates';
import { exportReportPdf, exportReportXlsx, printReport } from '../lib/export';

export function ReportsPage() {
  const { repo, t, version, toast } = useApp();
  const d = today();
  const [p, setP] = useState<ReportParams>({
    kind: 'daily',
    date: d,
    from: startOfMonth(d),
    to: d,
    type: '2D',
    session: '',
    year: d.slice(0, 4),
    customerType: '',
  });
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof ReportParams>(k: K, v: ReportParams[K]) => setP((x) => ({ ...x, [k]: v }));
  const badRange = p.from > p.to;
  const doc = useMemo(() => (badRange || !p.date || !p.from || !p.to ? null : buildReport(repo, p, t)), [repo, version, p, t, badRange]);
  const html = useMemo(() => (doc ? reportHtml(doc, t) : ''), [doc, t]);

  async function run(kind: 'xlsx' | 'pdf' | 'print') {
    if (!doc || busy) return;
    setBusy(true);
    try {
      if (kind === 'print') await printReport(doc, t);
      else {
        const f = kind === 'xlsx' ? await exportReportXlsx(doc) : await exportReportPdf(doc, t);
        toast(t('rep.exported', { f }));
      }
    } catch (e) {
      toast(errMsg(t, e), 'err');
    } finally {
      setBusy(false);
    }
  }

  const kinds: { value: ReportKind; label: string }[] = [
    { value: 'daily', label: t('rep.daily') },
    { value: 'byNumber', label: t('rep.byNumber') },
    { value: 'byDate', label: t('rep.byDate') },
    { value: 'bySession', label: t('rep.bySession') },
    { value: 'monthly', label: t('rep.monthly') },
    { value: 'customers', label: t('rep.customers') },
  ];
  const usesRange = ['byNumber', 'byDate', 'bySession', 'customers'].includes(p.kind);
  const years = Array.from({ length: 6 }, (_, i) => String(Number(d.slice(0, 4)) - i));

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rep.title')}</h1>
        <div className="head-actions">
          <button className="btn" disabled={!doc || busy} onClick={() => run('xlsx')} data-testid="rep-xlsx">
            ⬇ {t('c.excel')}
          </button>
          <button className="btn" disabled={!doc || busy} onClick={() => run('pdf')} data-testid="rep-pdf">
            ⬇ {t('c.pdf')}
          </button>
          <button className="btn primary" disabled={!doc || busy} onClick={() => run('print')} data-testid="rep-print">
            🖨 {t('c.print')}
          </button>
        </div>
      </div>
      <div className="card filters">
        <div className="filter-grid">
          <Field label={t('rep.type')}>
            <select className="input" value={p.kind} onChange={(e) => set('kind', e.target.value as ReportKind)} data-testid="rep-kind">
              {kinds.map((k) => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </Field>
          {p.kind === 'daily' && (
            <Field label={t('c.date')}>
              <input className="input" type="date" value={p.date} onChange={(e) => set('date', e.target.value)} data-testid="rep-date" />
            </Field>
          )}
          {usesRange && (
            <>
              <Field label={t('c.from')} error={badRange ? t('err.dateRange') : undefined}>
                <input className="input" type="date" value={p.from} onChange={(e) => set('from', e.target.value)} data-testid="rep-from" />
              </Field>
              <Field label={t('c.to')}>
                <input className="input" type="date" value={p.to} onChange={(e) => set('to', e.target.value)} data-testid="rep-to" />
              </Field>
            </>
          )}
          {p.kind === 'byNumber' && (
            <>
              <Field label={t('c.type')}>
                <Seg name="rep-type" value={p.type} onChange={(v) => set('type', v)} options={[{ value: '2D', label: '2D' }, { value: '3D', label: '3D' }]} />
              </Field>
              {p.type === '2D' && (
                <Field label={t('c.session')}>
                  <Seg
                    name="rep-session"
                    value={p.session}
                    onChange={(v) => set('session', v)}
                    options={[{ value: '', label: t('c.all') }, { value: 'AM', label: t('c.morning') }, { value: 'PM', label: t('c.evening') }]}
                  />
                </Field>
              )}
            </>
          )}
          {p.kind === 'customers' && (
            <Field label={t('c.type')}>
              <Seg name="rep-ctype" value={p.customerType} onChange={(v) => set('customerType', v)} options={[{ value: '', label: t('c.all') }, { value: '2D', label: '2D' }, { value: '3D', label: '3D' }]} />
            </Field>
          )}
          {p.kind === 'monthly' && (
            <Field label={t('rep.year')}>
              <select className="input" value={p.year} onChange={(e) => set('year', e.target.value)}>
                {years.map((y) => (
                  <option key={y}>{y}</option>
                ))}
              </select>
            </Field>
          )}
        </div>
      </div>
      <div className="card report-card">
        <div className="report-view" data-testid="report-view" dangerouslySetInnerHTML={{ __html: html }} />
      </div>
    </div>
  );
}
