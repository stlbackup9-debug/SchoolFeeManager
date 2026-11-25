import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Ctx, type AppCtx, type ConfirmOptions, type Page, useRepoVersion } from './ctx';
import { ConfirmDialog, Toasts } from './Components';
import { Dashboard } from './Dashboard';
import { EntryPage, EditRecordModal } from './EntryPage';
import { RecordsPage } from './RecordsPage';
import { ReportsPage } from './ReportsPage';
import { HistoryPage } from './HistoryPage';
import { SettingsPage, type Theme } from './SettingsPage';
import type { Repo, RecordRow } from '../db/repo';
import { translate, type Lang } from '../lib/i18n';
import type { GameType } from '../lib/validation';

const NAV: { page: Page; icon: string; key: string }[] = [
  { page: 'dashboard', icon: '▦', key: 'nav.dashboard' },
  { page: 'entry', icon: '＋', key: 'nav.entry' },
  { page: 'records', icon: '☰', key: 'nav.records' },
  { page: 'reports', icon: '▤', key: 'nav.reports' },
  { page: 'history', icon: '↺', key: 'nav.history' },
  { page: 'settings', icon: '⚙', key: 'nav.settings' },
];

function readHash(): { page: Page; params: URLSearchParams } {
  const [p, q] = location.hash.replace(/^#\/?/, '').split('?');
  const page = (NAV.some((n) => n.page === p) ? p : 'dashboard') as Page;
  return { page, params: new URLSearchParams(q ?? '') };
}

export function App({ repo, saveStatus, restoreBytes, wasmUrl }: { repo: Repo; saveStatus: AppCtx['saveStatus']; restoreBytes: (b: Uint8Array) => Promise<void>; wasmUrl?: string }) {
  const version = useRepoVersion(repo);
  const [route, setRoute] = useState(readHash);
  const [toasts, setToasts] = useState<{ id: number; msg: string; kind: 'ok' | 'err' }[]>([]);
  const [confirmState, setConfirmState] = useState<{ opts: ConfirmOptions; resolve: (b: boolean) => void } | null>(null);
  const [editing, setEditing] = useState<RecordRow | null>(null);
  const toastId = useRef(0);

  const lang = repo.getSetting('lang', 'en') as Lang;
  const theme = repo.getSetting('theme', 'system') as Theme;
  const t = useCallback((k: string, v?: Record<string, string | number>) => translate(lang, k, v), [lang]);

  useEffect(() => {
    const h = () => setRoute(readHash());
    window.addEventListener('hashchange', h);
    return () => window.removeEventListener('hashchange', h);
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    root.lang = lang;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => (root.dataset.theme = theme === 'system' ? (mq.matches ? 'dark' : 'light') : theme);
    apply();
    mq.addEventListener('change', apply);
    document.title = translate(lang, 'app.title');
    return () => mq.removeEventListener('change', apply);
  }, [lang, theme]);

  const ctx: AppCtx = useMemo(
    () => ({
      repo,
      lang,
      t,
      version,
      saveStatus,
      toast: (msg, kind = 'ok') => {
        const id = ++toastId.current;
        setToasts((x) => [...x.slice(-3), { id, msg, kind }]);
        setTimeout(() => setToasts((x) => x.filter((y) => y.id !== id)), kind === 'err' ? 6000 : 3000);
      },
      confirm: (opts) => new Promise<boolean>((resolve) => setConfirmState({ opts, resolve })),
      go: (page, params) => {
        location.hash = `#/${page}${params ? '?' + new URLSearchParams(params) : ''}`;
        window.scrollTo(0, 0);
      },
      editRecord: (id) => {
        const r = repo.getRecord(id);
        if (r) setEditing(r);
      },
    }),
    [repo, lang, t, version, saveStatus],
  );

  const { page, params } = route;
  let body: React.ReactNode;
  switch (page) {
    case 'entry':
      body = <EntryPage initialType={(params.get('type') as GameType) || undefined} />;
      break;
    case 'records':
      body = <RecordsPage />;
      break;
    case 'reports':
      body = <ReportsPage />;
      break;
    case 'history':
      body = <HistoryPage />;
      break;
    case 'settings':
      body = <SettingsPage restoreBytes={restoreBytes} wasmUrl={wasmUrl} />;
      break;
    default:
      body = <Dashboard />;
  }

  return (
    <Ctx.Provider value={ctx}>
      <div className="shell">
        <aside className="sidebar">
          <div className="brand">
            <span className="logo">2D</span>
            <span className="brand-text">{t('app.short')}</span>
          </div>
          <nav>
            {NAV.map((n) => (
              <a key={n.page} href={`#/${n.page}`} className={page === n.page ? 'on' : ''} data-testid={`nav-${n.page}`}>
                <span className="nav-ic" aria-hidden>
                  {n.icon}
                </span>
                <span className="nav-tx">{t(n.key)}</span>
              </a>
            ))}
          </nav>
          <div className="side-foot">
            <button className="btn ghost small" onClick={() => repo.setSetting('lang', lang === 'en' ? 'my' : 'en')} data-testid="lang-toggle">
              {lang === 'en' ? 'မြန်မာ' : 'English'}
            </button>
            <button
              className="btn ghost small"
              onClick={() => repo.setSetting('theme', document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark')}
              data-testid="theme-toggle"
              aria-label="theme"
            >
              {document.documentElement.dataset.theme === 'dark' ? '☀' : '☾'}
            </button>
            <span className={'save-dot ' + saveStatus} title={t(saveStatus === 'saved' ? 'c.saved' : saveStatus === 'pending' ? 'c.saving' : 'c.saveError')} data-testid="save-status">
              ● {t(saveStatus === 'saved' ? 'c.saved' : saveStatus === 'pending' ? 'c.saving' : 'c.saveError')}
            </span>
          </div>
        </aside>
        <main className="main">{body}</main>
      </div>
      {editing && <EditRecordModal record={editing} onClose={() => setEditing(null)} />}
      {confirmState && (
        <ConfirmDialog
          opts={confirmState.opts}
          onDone={(ok) => {
            confirmState.resolve(ok);
            setConfirmState(null);
          }}
        />
      )}
      <Toasts items={toasts} />
    </Ctx.Provider>
  );
}
