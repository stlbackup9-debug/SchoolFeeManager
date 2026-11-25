import { createContext, useContext, useEffect, useState } from 'react';
import type { Repo } from '../db/repo';
import type { Lang, T } from '../lib/i18n';

export interface ConfirmOptions {
  title: string;
  message: React.ReactNode;
  ok?: string;
  danger?: boolean;
  requireText?: string;
}

export interface AppCtx {
  repo: Repo;
  lang: Lang;
  t: T;
  version: number;
  toast: (msg: string, kind?: 'ok' | 'err') => void;
  confirm: (o: ConfirmOptions) => Promise<boolean>;
  go: (page: Page, params?: Record<string, string>) => void;
  editRecord: (id: number) => void;
  saveStatus: 'saved' | 'pending' | 'error';
}

export type Page = 'dashboard' | 'entry' | 'records' | 'reports' | 'history' | 'settings';

export const Ctx = createContext<AppCtx | null>(null);

export function useApp(): AppCtx {
  const c = useContext(Ctx);
  if (!c) throw new Error('AppCtx missing');
  return c;
}

/** Re-renders when the repository changes. */
export function useRepoVersion(repo: Repo): number {
  const [v, setV] = useState(0);
  useEffect(() => repo.onChange(() => setV((x) => x + 1)), [repo]);
  return v;
}

export function errMsg(t: T, e: unknown): string {
  const key = (e as { key?: string })?.key ?? (e as Error)?.message;
  if (key && key.startsWith('err.')) return t(key);
  return t('err.generic', { m: String((e as Error)?.message ?? e) });
}
