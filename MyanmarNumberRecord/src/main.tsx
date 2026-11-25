import { createRoot } from 'react-dom/client';
import { useEffect, useState } from 'react';
import wasmUrl from 'sql.js/dist/sql-wasm.wasm?url';
import '@fontsource/noto-sans-myanmar/400.css';
import '@fontsource/noto-sans-myanmar/700.css';
import './styles.css';
import { Db } from './db/database';
import { Repo } from './db/repo';
import { AutoSaver, storage } from './db/storage';
import { today, fileStamp } from './lib/dates';
import { App } from './ui/App';
import { isNative } from './lib/export';

async function boot() {
  const root = createRoot(document.getElementById('root')!);
  try {
    await storage.requestPersistence();
    const bytes = await storage.load();
    const db = await Db.create(bytes, wasmUrl);
    const repo = new Repo(db);

    // Daily snapshot of the database as it was when the app was opened.
    const snaps = await storage.listSnapshots();
    if (bytes && !snaps.some((s) => s.startsWith(today()))) await storage.saveSnapshot(today(), bytes);
    if (!bytes) await storage.save(db.export());

    let setStatus: (s: AutoSaver['status']) => void = () => {};
    const saver = new AutoSaver(() => repo.db.export(), (s) => setStatus(s));
    repo.onChange(() => saver.schedule());
    const flush = () => void saver.flush();
    document.addEventListener('visibilitychange', () => document.visibilityState === 'hidden' && flush());
    window.addEventListener('pagehide', flush);
    if (isNative()) {
      // Android: save when the app goes to the background; hardware back button navigates back.
      const { App: CapApp } = await import('@capacitor/app');
      void CapApp.addListener('pause', flush);
      void CapApp.addListener('backButton', ({ canGoBack }) => {
        if (canGoBack && location.hash && !/^#\/?(dashboard)?$/.test(location.hash)) history.back();
        else void saver.flush().then(() => CapApp.exitApp());
      });
    }
    window.addEventListener('beforeunload', (e) => {
      if (saver.status === 'pending') {
        flush();
        e.preventDefault();
      }
    });

    const restoreBytes = async (b: Uint8Array) => {
      await storage.saveSnapshot(`before-restore-${fileStamp()}`, repo.db.export());
      const fresh = await Db.create(b, wasmUrl); // runs migrations on older backups
      repo.swap(fresh);
      repo.logEvent('RESTORE', 'Database restored from backup');
      await saver.flush();
    };

    function Root() {
      const [status, set] = useState<AutoSaver['status']>('saved');
      useEffect(() => {
        setStatus = set;
      }, []);
      return <App repo={repo} saveStatus={status} restoreBytes={restoreBytes} wasmUrl={wasmUrl} />;
    }
    root.render(<Root />);
    (window as unknown as { __repo: Repo }).__repo = repo; // for automated tests / debugging
  } catch (e) {
    console.error(e);
    root.render(
      <div style={{ padding: 24, fontFamily: 'sans-serif' }}>
        <h2>Database error</h2>
        <pre style={{ whiteSpace: 'pre-wrap' }}>{String((e as Error)?.stack ?? e)}</pre>
      </div>,
    );
  }
}

void boot();
