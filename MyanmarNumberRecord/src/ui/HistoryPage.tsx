import { useMemo, useState } from 'react';
import { useApp, errMsg } from './ctx';
import { Empty } from './Components';

export function HistoryPage() {
  const { repo, t, version, toast } = useApp();
  const [limit, setLimit] = useState(200);
  const rows = useMemo(() => repo.history(limit), [repo, version, limit]);

  function undo(id: number) {
    try {
      repo.undoDelete(id);
      toast(t('h.restored'));
    } catch (e) {
      toast(errMsg(t, e), 'err');
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('h.title')}</h1>
      </div>
      <div className="card">
        {!rows.length ? (
          <Empty>{t('c.noData')}</Empty>
        ) : (
          <div className="table-wrap">
            <table className="table" data-testid="history-table">
              <thead>
                <tr>
                  <th>{t('h.at')}</th>
                  <th>{t('h.action')}</th>
                  <th>{t('h.details')}</th>
                  <th className="actions" />
                </tr>
              </thead>
              <tbody>
                {rows.map((h) => (
                  <tr key={h.id}>
                    <td className="nowrap muted">{h.at}</td>
                    <td>
                      <span className={'tag h-' + h.action}>{t('h.' + h.action)}</span>
                    </td>
                    <td className="mono-ish">{h.summary}</td>
                    <td className="actions">
                      {h.action === 'DELETE' && h.before_json && (
                        <button className="btn small" onClick={() => undo(h.id)} data-testid={`undo-${h.id}`}>
                          ↺ {t('h.undo')}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {rows.length === limit && (
          <div className="pager">
            <button className="btn" onClick={() => setLimit(limit + 200)}>
              {t('c.next')} ›
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
