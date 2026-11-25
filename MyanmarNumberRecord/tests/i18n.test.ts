import { describe, expect, it } from 'vitest';
import { translate } from '../src/lib/i18n';
import * as fs from 'node:fs';

describe('i18n', () => {
  it('interpolates', () => {
    expect(translate('en', 'e.savedN', { n: 3 })).toBe('3 record(s) saved');
    expect(translate('my', 'e.savedN', { n: 3 })).toBe('စာရင်း 3 ခု သိမ်းပြီးပါပြီ');
  });
  it('every key used in the UI exists', () => {
    const src = fs.readdirSync('src/ui').map((f) => fs.readFileSync('src/ui/' + f, 'utf8')).join('\n') + fs.readFileSync('src/lib/reports.ts', 'utf8');
    const keys = new Set([...src.matchAll(/\bt\(\s*'([a-z0-9]+\.[A-Za-z0-9]+)'/g)].map((m) => m[1]));
    const missing = [...keys].filter((k) => translate('en', k) === k);
    expect(missing).toEqual([]);
  });
});
