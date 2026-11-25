// Persists the SQLite database file in IndexedDB (works in desktop browsers,
// the single-file desktop build and the Android WebView), with automatic
// debounced saving and rolling daily snapshots for recovery.

const IDB_NAME = 'mm-number-record';
const STORE = 'kv';
const MAIN_KEY = 'db';
const SNAP_PREFIX = 'snap:';
export const MAX_SNAPSHOTS = 7;

function openIdb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore<T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openIdb();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = fn(tx.objectStore(STORE));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

export const storage = {
  async load(): Promise<Uint8Array | null> {
    const v = await withStore<Uint8Array | undefined>('readonly', (s) => s.get(MAIN_KEY));
    return v ? new Uint8Array(v) : null;
  },
  async save(bytes: Uint8Array): Promise<void> {
    await withStore('readwrite', (s) => s.put(bytes, MAIN_KEY));
  },
  async listSnapshots(): Promise<string[]> {
    const keys = (await withStore<IDBValidKey[]>('readonly', (s) => s.getAllKeys())) as string[];
    return keys.filter((k) => k.startsWith(SNAP_PREFIX)).map((k) => k.slice(SNAP_PREFIX.length)).sort().reverse();
  },
  async loadSnapshot(name: string): Promise<Uint8Array | null> {
    const v = await withStore<Uint8Array | undefined>('readonly', (s) => s.get(SNAP_PREFIX + name));
    return v ? new Uint8Array(v) : null;
  },
  async saveSnapshot(name: string, bytes: Uint8Array): Promise<void> {
    await withStore('readwrite', (s) => s.put(bytes, SNAP_PREFIX + name));
    const all = await this.listSnapshots();
    for (const old of all.slice(MAX_SNAPSHOTS)) await withStore('readwrite', (s) => s.delete(SNAP_PREFIX + old));
  },
  async requestPersistence(): Promise<void> {
    try {
      await navigator.storage?.persist?.();
    } catch {
      /* not supported */
    }
  },
};

/** Debounced autosaver. `getBytes` is called lazily so the latest state is saved. */
export class AutoSaver {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private saving: Promise<void> = Promise.resolve();
  public status: 'saved' | 'pending' | 'error' = 'saved';
  constructor(private getBytes: () => Uint8Array, private onStatus: (s: AutoSaver['status']) => void, private delay = 250) {}

  schedule() {
    this.setStatus('pending');
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.flush(), this.delay);
  }

  async flush(): Promise<void> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.saving = this.saving.then(async () => {
      try {
        await storage.save(this.getBytes());
        this.setStatus('saved');
      } catch (e) {
        console.error('Autosave failed', e);
        this.setStatus('error');
      }
    });
    return this.saving;
  }

  private setStatus(s: AutoSaver['status']) {
    this.status = s;
    this.onStatus(s);
  }
}
