import { useEffect, useMemo, useRef, useState } from 'react';
import { useApp, errMsg } from './ctx';
import { Field, Modal, Seg } from './Components';
import { type GameType, type Session, parseNumberList, validateAmount, validateDate, validateNumber } from '../lib/validation';
import { defaultSession, today } from '../lib/dates';
import { fmtMoney } from '../lib/format';
import { describe as describeRec, type RecordInput, type RecordRow } from '../db/repo';
import { RecordsTable } from './RecordsTable';

/** Asks for confirmation when inputs duplicate existing rows (if enabled in settings). */
export function useDuplicateGuard() {
  const { repo, t, confirm } = useApp();
  return async (inputs: RecordInput[], excludeId?: number): Promise<boolean> => {
    if (repo.getSetting('dupCheck', '1') !== '1') return true;
    const dups = repo.findDuplicates(inputs, excludeId);
    if (!dups.length) return true;
    return confirm({
      title: t('e.dupTitle'),
      message: (
        <>
          <p>{t('e.dupMsg')}</p>
          <ul className="dup-list">
            {dups.slice(0, 10).map((d) => (
              <li key={d.id}>
                #{d.id} · {describeRec(d)}
              </li>
            ))}
            {dups.length > 10 && <li>… +{dups.length - 10}</li>}
          </ul>
        </>
      ),
      ok: t('e.saveAnyway'),
    });
  };
}

export function EntryPage({ initialType }: { initialType?: GameType }) {
  const { repo, t, toast, version } = useApp();
  const guard = useDuplicateGuard();
  const [type, setType] = useState<GameType>(initialType ?? (repo.getSetting('lastType', '2D') as GameType));
  const [date, setDate] = useState(today());
  const [session, setSession] = useState<Session>(defaultSession());
  const [numbers, setNumbers] = useState('');
  const [amount, setAmount] = useState('');
  const [customer, setCustomer] = useState('');
  const [remark, setRemark] = useState('');
  const [expand, setExpand] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const numRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initialType) setType(initialType);
  }, [initialType]);
  useEffect(() => numRef.current?.focus(), [type]);

  const parsed = useMemo(() => parseNumberList(type, numbers, expand), [type, numbers, expand]);
  const amt = validateAmount(amount);
  const customers = useMemo(() => repo.customers(), [repo, version]);
  const todays = useMemo(
    () => repo.queryRecords({ from: date, to: date, type, session: type === '2D' ? session : '', sort: 'newest' }),
    [repo, version, date, type, session],
  );

  async function submit(e?: React.FormEvent) {
    e?.preventDefault();
    if (busy) return; // prevents double-submits
    const errs: Record<string, string> = {};
    const d = validateDate(date);
    if (!d.ok) errs.date = t(d.error);
    if (!parsed.numbers.length) errs.numbers = t(parsed.invalid.length ? (validateNumber(type, parsed.invalid[0]) as { error: string }).error : 'err.numberRequired');
    else if (parsed.invalid.length) errs.numbers = t('e.invalid', { list: parsed.invalid.join(', ') });
    if (!amt.ok) errs.amount = t(amt.error);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const inputs: RecordInput[] = parsed.numbers.map((n) => ({
      type,
      draw_date: date,
      session: type === '2D' ? session : null,
      number: n,
      amount: (amt as { value: number }).value,
      customer,
      remark,
    }));
    setBusy(true);
    try {
      if (!(await guard(inputs))) return;
      const ids = repo.addRecords(inputs);
      repo.setSetting('lastType', type);
      toast(t('e.savedN', { n: ids.length }));
      setNumbers('');
      setExpand(false);
      numRef.current?.focus();
    } catch (err) {
      toast(errMsg(t, err), 'err');
    } finally {
      setBusy(false);
    }
  }

  const hint = type === '2D' ? t('e.numbersHint2d') : t('e.numbersHint3d');
  const previewTotal = amt.ok ? parsed.numbers.length * amt.value : 0;

  return (
    <div className="page entry-page">
      <div className="page-head">
        <h1>{t('e.title')}</h1>
      </div>
      <div className="entry-grid">
        <form className="card entry-form" onSubmit={submit} noValidate data-testid="entry-form">
          <Seg
            name="type"
            value={type}
            onChange={(v) => {
              setType(v);
              setErrors({});
              setNumbers('');
            }}
            options={[
              { value: '2D', label: '2D (00–99)' },
              { value: '3D', label: '3D (000–999)' },
            ]}
          />
          <div className="row2">
            <Field label={t('c.date')} error={errors.date}>
              <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} data-testid="entry-date" required />
            </Field>
            {type === '2D' && (
              <Field label={t('c.session')} hint={t('e.sessionHint')}>
                <Seg
                  name="session"
                  value={session}
                  onChange={setSession}
                  options={[
                    { value: 'AM', label: t('c.morning') },
                    { value: 'PM', label: t('c.evening') },
                  ]}
                />
              </Field>
            )}
          </div>
          <Field label={t('e.numbers')} error={errors.numbers} hint={hint}>
            <input
              ref={numRef}
              className="input big mono"
              inputMode="numeric"
              autoComplete="off"
              value={numbers}
              onChange={(e) => setNumbers(e.target.value)}
              placeholder={type === '2D' ? '25' : '123'}
              data-testid="entry-numbers"
            />
          </Field>
          <label className="check">
            <input type="checkbox" checked={expand} onChange={(e) => setExpand(e.target.checked)} data-testid="entry-expand" />
            {type === '2D' ? t('e.reverse') : t('e.permute')}
          </label>
          <Field label={`${t('c.amount')} (${t('c.kyat')})`} error={errors.amount}>
            <input className="input big mono" inputMode="numeric" autoComplete="off" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="1000" data-testid="entry-amount" />
          </Field>
          <div className="quick-amts">
            {[500, 1000, 2000, 5000, 10000].map((a) => (
              <button type="button" key={a} className="chip" onClick={() => setAmount(String(a))}>
                {fmtMoney(a)}
              </button>
            ))}
          </div>
          <div className="row2">
            <Field label={t('c.customerName')}>
              <input className="input" list="customer-list" value={customer} onChange={(e) => setCustomer(e.target.value)} data-testid="entry-customer" maxLength={200} />
              <datalist id="customer-list">
                {customers.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </Field>
            <Field label={t('c.remark')}>
              <input className="input" value={remark} onChange={(e) => setRemark(e.target.value)} data-testid="entry-remark" maxLength={200} />
            </Field>
          </div>
          {parsed.numbers.length > 0 && (
            <div className="preview" data-testid="entry-preview">
              <div className="preview-nums">
                {parsed.numbers.map((n) => (
                  <span key={n} className="num-pill">
                    {n}
                  </span>
                ))}
              </div>
              <div className="preview-total">
                {t('e.preview', { n: parsed.numbers.length, a: amt.ok ? fmtMoney(amt.value) : '?', t: fmtMoney(previewTotal) })}
              </div>
            </div>
          )}
          <button className="btn primary block big" type="submit" disabled={busy} data-testid="entry-save">
            {t('e.saveBtn')}
          </button>
        </form>
        <div className="card">
          <div className="card-head">
            <h2>
              {t('e.entriesFor')} · {type}
              {type === '2D' ? ` · ${session === 'AM' ? t('c.morning') : t('c.evening')}` : ''}
            </h2>
            <span className="badge">
              {todays.totals.count} · {fmtMoney(todays.totals.total)} {t('c.kyat')}
            </span>
          </div>
          <RecordsTable rows={todays.rows.slice(0, 100)} compact />
        </div>
      </div>
    </div>
  );
}

/** Modal to edit a single record. */
export function EditRecordModal({ record, onClose }: { record: RecordRow; onClose: () => void }) {
  const { repo, t, toast } = useApp();
  const guard = useDuplicateGuard();
  const [type, setType] = useState<GameType>(record.type);
  const [date, setDate] = useState(record.draw_date);
  const [session, setSession] = useState<Session>(record.session ?? 'AM');
  const [number, setNumber] = useState(record.number);
  const [amount, setAmount] = useState(String(record.amount));
  const [customer, setCustomer] = useState(record.customer);
  const [remark, setRemark] = useState(record.remark);
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function save() {
    const errs: Record<string, string> = {};
    const d = validateDate(date);
    if (!d.ok) errs.date = t(d.error);
    const n = validateNumber(type, number);
    if (!n.ok) errs.number = t(n.error);
    const a = validateAmount(amount);
    if (!a.ok) errs.amount = t(a.error);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input: RecordInput = { type, draw_date: date, session: type === '2D' ? session : null, number, amount, customer, remark };
    try {
      if (!(await guard([input], record.id))) return;
      repo.updateRecord(record.id, input);
      toast(t('e.updated'));
      onClose();
    } catch (err) {
      toast(errMsg(t, err), 'err');
    }
  }

  return (
    <Modal
      title={`${t('e.editTitle')} #${record.id}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            {t('c.cancel')}
          </button>
          <button className="btn primary" onClick={save} data-testid="edit-save">
            {t('c.save')}
          </button>
        </>
      }
    >
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <Seg name="edit-type" value={type} onChange={setType} options={[{ value: '2D', label: '2D' }, { value: '3D', label: '3D' }]} />
        <div className="row2">
          <Field label={t('c.date')} error={errors.date}>
            <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          {type === '2D' && (
            <Field label={t('c.session')}>
              <Seg name="edit-session" value={session} onChange={setSession} options={[{ value: 'AM', label: t('c.morning') }, { value: 'PM', label: t('c.evening') }]} />
            </Field>
          )}
        </div>
        <div className="row2">
          <Field label={t('c.number')} error={errors.number}>
            <input className="input mono" inputMode="numeric" value={number} onChange={(e) => setNumber(e.target.value)} data-testid="edit-number" />
          </Field>
          <Field label={`${t('c.amount')} (${t('c.kyat')})`} error={errors.amount}>
            <input className="input mono" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} data-testid="edit-amount" />
          </Field>
        </div>
        <Field label={t('c.customerName')}>
          <input className="input" list="customer-list" value={customer} onChange={(e) => setCustomer(e.target.value)} maxLength={200} />
        </Field>
        <Field label={t('c.remark')}>
          <input className="input" value={remark} onChange={(e) => setRemark(e.target.value)} maxLength={200} />
        </Field>
        <button type="submit" hidden />
      </form>
    </Modal>
  );
}
