import { useMemo } from 'react';
import { useApp } from './ctx';
import { BarChart, Empty, StatCard } from './Components';
import { RecordsTable } from './RecordsTable';
import { addDays, dateRange, startOfMonth, startOfWeek, today } from '../lib/dates';
import { fmtInt, fmtMoney } from '../lib/format';

export function Dashboard() {
  const { repo, t, version, go } = useApp();
  const d = today();
  const data = useMemo(() => {
    const todayT = repo.totals(d, d);
    const week = repo.totals(startOfWeek(d), d);
    const month = repo.totals(startOfMonth(d), d);
    const from14 = addDays(d, -13);
    const daily = new Map(repo.dailyTotals(from14, d).map((r) => [r.date, r]));
    const chart = dateRange(from14, d).map((x) => ({ label: x, a: daily.get(x)?.t2d ?? 0, b: daily.get(x)?.t3d ?? 0 }));
    return {
      todayT,
      week,
      month,
      chart,
      all: repo.allTimeCount(),
      top2d: repo.topNumbers('2D', d, d, 5),
      top3d: repo.topNumbers('3D', startOfMonth(d), d, 5),
      recent: repo.queryRecords({ sort: 'newest', limit: 8 }).rows,
    };
  }, [repo, version, d]);

  const k = t('c.kyat');
  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('nav.dashboard')}</h1>
          <div className="muted">{d}</div>
        </div>
        <div className="head-actions">
          <button className="btn primary" onClick={() => go('entry', { type: '2D' })} data-testid="quick-2d">
            + 2D
          </button>
          <button className="btn primary alt" onClick={() => go('entry', { type: '3D' })} data-testid="quick-3d">
            + 3D
          </button>
        </div>
      </div>

      <div className="stats" data-testid="stats">
        <StatCard label={t('d.todayRecords')} value={fmtInt(data.todayT.count)} sub={`2D ${data.todayT.c2d} · 3D ${data.todayT.c3d}`} accent="--c-ink" />
        <StatCard label={t('d.todayAmount')} value={fmtMoney(data.todayT.total)} sub={k} accent="--c-brand" />
        <StatCard label={t('d.total2d')} value={fmtMoney(data.todayT.t2d)} sub={`${t('c.morning')} ${fmtMoney(data.todayT.am)} · ${t('c.evening')} ${fmtMoney(data.todayT.pm)}`} accent="--c-2d" />
        <StatCard label={t('d.total3d')} value={fmtMoney(data.todayT.t3d)} sub={k} accent="--c-3d" />
        <StatCard label={t('d.transactions')} value={fmtInt(data.all)} sub={t('d.allTime')} accent="--c-ink" />
      </div>

      {data.all === 0 && (
        <Empty>
          <b>{t('d.emptyTitle')}</b>
          <p>{t('d.emptyText')}</p>
          <button className="btn primary" onClick={() => go('entry')}>
            {t('d.newEntry')}
          </button>
        </Empty>
      )}

      <div className="dash-grid">
        <div className="card">
          <div className="card-head">
            <h2>{t('d.summary')}</h2>
          </div>
          <div className="table-wrap">
            <table className="table" data-testid="summary-table">
              <thead>
                <tr>
                  <th>{t('d.period')}</th>
                  <th className="money">{t('c.count')}</th>
                  <th className="money">2D</th>
                  <th className="money">3D</th>
                  <th className="money">{t('c.total')}</th>
                </tr>
              </thead>
              <tbody>
                {(
                  [
                    [t('c.today'), data.todayT],
                    [t('c.week'), data.week],
                    [t('c.monthLabel'), data.month],
                  ] as const
                ).map(([label, x]) => (
                  <tr key={label}>
                    <td>{label}</td>
                    <td className="money">{fmtInt(x.count)}</td>
                    <td className="money">{fmtMoney(x.t2d)}</td>
                    <td className="money">{fmtMoney(x.t3d)}</td>
                    <td className="money strong">{fmtMoney(x.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div className="card">
          <div className="card-head">
            <h2>{t('d.last14')}</h2>
          </div>
          <BarChart data={data.chart} />
        </div>
        <div className="card">
          <div className="card-head">
            <h2>{t('d.top2d')}</h2>
          </div>
          <TopList rows={data.top2d} />
        </div>
        <div className="card">
          <div className="card-head">
            <h2>{t('d.top3d')}</h2>
          </div>
          <TopList rows={data.top3d} />
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t('d.recent')}</h2>
          <button className="btn ghost" onClick={() => go('records')}>
            {t('nav.records')} ›
          </button>
        </div>
        <RecordsTable rows={data.recent} />
      </div>
    </div>
  );
}

function TopList({ rows }: { rows: { number: string; count: number; total: number }[] }) {
  const { t } = useApp();
  if (!rows.length) return <Empty>{t('c.noData')}</Empty>;
  const max = rows[0].total || 1;
  return (
    <ul className="top-list">
      {rows.map((r) => (
        <li key={r.number}>
          <span className="num-pill">{r.number}</span>
          <span className="top-bar">
            <i style={{ width: `${(r.total / max) * 100}%` }} />
          </span>
          <span className="money">{fmtMoney(r.total)}</span>
          <span className="muted small">×{r.count}</span>
        </li>
      ))}
    </ul>
  );
}
