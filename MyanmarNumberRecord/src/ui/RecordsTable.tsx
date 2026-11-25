import type { RecordRow } from '../db/repo';
import { fmtMoney } from '../lib/format';
import { useApp } from './ctx';
import { Empty } from './Components';

export function useDeleteRecords() {
  const { repo, t, toast, confirm } = useApp();
  return async (ids: number[]) => {
    if (!ids.length) return false;
    const ok = await confirm({
      title: t('c.delete'),
      message: ids.length === 1 ? t('r.deleteConfirm') : t('r.deleteManyConfirm', { n: ids.length }),
      ok: t('c.delete'),
      danger: true,
    });
    if (!ok) return false;
    const n = repo.deleteRecords(ids);
    toast(t('r.deleted', { n }));
    return true;
  };
}

export function RecordsTable({
  rows,
  compact,
  selected,
  onSelect,
}: {
  rows: RecordRow[];
  compact?: boolean;
  selected?: Set<number>;
  onSelect?: (s: Set<number>) => void;
}) {
  const { t, editRecord } = useApp();
  const del = useDeleteRecords();
  if (!rows.length) return <Empty>{t('c.noData')}</Empty>;
  const allOn = !!selected && rows.every((r) => selected.has(r.id));
  const toggle = (id: number) => {
    if (!selected || !onSelect) return;
    const s = new Set(selected);
    if (s.has(id)) s.delete(id);
    else s.add(id);
    onSelect(s);
  };
  return (
    <div className="table-wrap">
      <table className={'table' + (compact ? ' compact' : '')} data-testid="records-table">
        <thead>
          <tr>
            {onSelect && (
              <th className="w-check">
                <input
                  type="checkbox"
                  aria-label="select all"
                  checked={allOn}
                  onChange={() => {
                    const s = new Set(selected);
                    rows.forEach((r) => (allOn ? s.delete(r.id) : s.add(r.id)));
                    onSelect(s);
                  }}
                />
              </th>
            )}
            {!compact && <th>{t('c.date')}</th>}
            <th>{t('c.type')}</th>
            <th className="num">{t('c.number')}</th>
            <th className="money">{t('c.amount')}</th>
            <th>{t('c.customer')}</th>
            {!compact && <th>{t('c.remark')}</th>}
            <th className="actions">{t('c.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className={selected?.has(r.id) ? 'sel' : ''}>
              {onSelect && (
                <td className="w-check">
                  <input type="checkbox" aria-label={`select ${r.id}`} checked={selected!.has(r.id)} onChange={() => toggle(r.id)} />
                </td>
              )}
              {!compact && <td className="nowrap">{r.draw_date}</td>}
              <td className="nowrap">
                <span className={'tag t' + r.type}>{r.type}</span>
                {r.session && <span className="sess">{r.session === 'AM' ? t('c.morning') : t('c.evening')}</span>}
              </td>
              <td className="num">
                <span className="num-pill">{r.number}</span>
              </td>
              <td className="money">{fmtMoney(r.amount)}</td>
              <td className="ellipsis">{r.customer}</td>
              {!compact && <td className="ellipsis muted">{r.remark}</td>}
              <td className="actions nowrap">
                <button className="icon-btn" title={t('c.edit')} aria-label={t('c.edit')} onClick={() => editRecord(r.id)} data-testid={`edit-${r.id}`}>
                  ✎
                </button>
                <button className="icon-btn danger" title={t('c.delete')} aria-label={t('c.delete')} onClick={() => void del([r.id])} data-testid={`del-${r.id}`}>
                  🗑
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
