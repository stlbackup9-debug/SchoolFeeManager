import { useEffect, useRef, useState } from 'react';
import { useApp, errMsg } from './ctx';
import { Field, Seg } from './Components';
import { inspectBackup } from '../db/database';
import { storage, MAX_SNAPSHOTS } from '../db/storage';
import { loadSampleData } from '../db/sample';
import { fileStamp } from '../lib/dates';
import { isNative, saveFile } from '../lib/export';
import type { Lang } from '../lib/i18n';

export type Theme = 'light' | 'dark' | 'system';

export function SettingsPage({ restoreBytes, wasmUrl }: { restoreBytes: (b: Uint8Array) => Promise<void>; wasmUrl?: string }) {
  const { repo, t, toast, confirm, version } = useApp();
  const [snaps, setSnaps] = useState<string[]>([]);
  const [business, setBusiness] = useState(repo.getSetting('business', ''));
  const fileRef = useRef<HTMLInputElement>(null);
  const lang = repo.getSetting('lang', 'en') as Lang;
  const theme = repo.getSetting('theme', 'system') as Theme;
  const dupCheck = repo.getSetting('dupCheck', '1') === '1';
  const size = repo.db.export().length;

  useEffect(() => {
    storage.listSnapshots().then(setSnaps).catch(() => setSnaps([]));
  }, [version]);

  async function backup() {
    try {
      const f = await saveFile(`2d3d-backup-${fileStamp()}.sqlite`, repo.db.export(), 'application/vnd.sqlite3');
      toast(t('rep.exported', { f }));
    } catch (e) {
      toast(errMsg(t, e), 'err');
    }
  }

  async function restoreFrom(bytes: Uint8Array) {
    const info = await inspectBackup(bytes, wasmUrl);
    if (!info.ok) {
      toast(t(info.error), 'err');
      return;
    }
    if (!(await confirm({ title: t('s.restore'), message: t('s.restoreConfirm', { n: info.records }), ok: t('s.restore'), danger: true }))) return;
    try {
      await restoreBytes(bytes);
      toast(t('s.restored'));
    } catch (e) {
      toast(errMsg(t, e), 'err');
    }
  }

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    await restoreFrom(new Uint8Array(await file.arrayBuffer()));
  }

  async function restoreSnapshot(name: string) {
    const bytes = await storage.loadSnapshot(name);
    if (bytes) await restoreFrom(bytes);
  }

  async function sample() {
    if (!(await confirm({ title: t('s.loadSample'), message: t('s.sampleConfirm') }))) return;
    const n = loadSampleData(repo);
    toast(t('s.sampleLoaded', { n }));
  }

  async function clearAll() {
    if (!(await confirm({ title: t('s.clearAll'), message: t('s.clearConfirm'), danger: true, requireText: 'DELETE', ok: t('c.delete') }))) return;
    await storage.saveSnapshot(`before-clear-${fileStamp()}`, repo.db.export());
    repo.clearAll();
    toast(t('s.cleared'));
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('s.title')}</h1>
      </div>
      <div className="settings-grid">
        <section className="card stack">
          <h2>{t('s.appearance')}</h2>
          <Field label={t('s.language')}>
            <Seg name="lang" value={lang} onChange={(v) => repo.setSetting('lang', v)} options={[{ value: 'en', label: 'English' }, { value: 'my', label: 'မြန်မာ' }]} />
          </Field>
          <Field label={t('s.theme')}>
            <Seg
              name="theme"
              value={theme}
              onChange={(v) => repo.setSetting('theme', v)}
              options={[{ value: 'light', label: '☀ ' + t('s.light') }, { value: 'dark', label: '☾ ' + t('s.dark') }, { value: 'system', label: t('s.system') }]}
            />
          </Field>
          <Field label={t('s.business')}>
            <input className="input" value={business} maxLength={100} onChange={(e) => setBusiness(e.target.value)} onBlur={() => repo.setSetting('business', business.trim())} data-testid="business" />
          </Field>
          <label className="check">
            <input type="checkbox" checked={dupCheck} onChange={(e) => repo.setSetting('dupCheck', e.target.checked ? '1' : '0')} />
            {t('s.dupCheck')}
          </label>
        </section>

        <section className="card stack">
          <h2>{t('s.backup')}</h2>
          <p className="muted">{t('s.backupText')}</p>
          <div className="btn-row">
            <button className="btn primary" onClick={backup} data-testid="backup">
              ⬇ {t('s.backupNow')}
            </button>
            <button className="btn" onClick={() => fileRef.current?.click()} data-testid="restore">
              ⬆ {t('s.restore')}
            </button>
            <input ref={fileRef} type="file" accept={isNative() ? undefined : '.sqlite,.db,.sqlite3'} hidden onChange={onFile} data-testid="restore-file" />
          </div>
          <h3>{t('s.snapshots')}</h3>
          <p className="muted small">{t('s.snapshotsText', { n: MAX_SNAPSHOTS })}</p>
          {snaps.length ? (
            <ul className="snap-list">
              {snaps.map((s) => (
                <li key={s}>
                  <span>{s}</span>
                  <button className="btn small" onClick={() => restoreSnapshot(s)}>
                    {t('s.snapshotRestore')}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small">{t('s.noSnapshots')}</p>
          )}
        </section>

        <section className="card stack">
          <h2>{t('s.data')}</h2>
          <p className="muted">{t('s.stats', { n: repo.allTimeCount(), s: `${(size / 1024).toFixed(1)} KB` })}</p>
          <div className="btn-row">
            <button className="btn" onClick={sample} data-testid="load-sample">
              {t('s.loadSample')}
            </button>
            <button className="btn danger" onClick={clearAll} data-testid="clear-all">
              {t('s.clearAll')}
            </button>
          </div>
          <p className="muted small">{t('s.about')} v1.0.0</p>
        </section>
      </div>
    </div>
  );
}
