// Deterministic sample data for testing and demonstration.
import type { Repo, RecordInput } from './repo';
import { addDays, today } from '../lib/dates';

const CUSTOMERS = ['ကိုအောင်', 'မမြင့်', 'ဦးဘ', 'Ko Zaw', 'Ma Hla', 'မသီတာ', 'ကိုကျော်', ''];
const AMOUNTS = [500, 1000, 1000, 2000, 3000, 5000, 10000];
const REMARKS = ['', '', '', 'ငွေချေပြီး', 'paid', 'အကြွေး', ''];

/** Small seeded PRNG (mulberry32) so sample data is identical every time. */
function rng(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function buildSampleData(days = 45, end = today(), seed = 2025): RecordInput[] {
  const r = rng(seed);
  const pick = <T,>(a: T[]) => a[Math.floor(r() * a.length)];
  const out: RecordInput[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const date = addDays(end, -i);
    const dow = new Date(date + 'T00:00:00').getDay();
    if (dow === 0 || dow === 6) continue; // 2D runs Monday–Friday
    for (const session of ['AM', 'PM'] as const) {
      const n = 6 + Math.floor(r() * 10);
      for (let k = 0; k < n; k++) {
        out.push({
          type: '2D',
          draw_date: date,
          session,
          number: String(Math.floor(r() * 100)).padStart(2, '0'),
          amount: pick(AMOUNTS),
          customer: pick(CUSTOMERS),
          remark: pick(REMARKS),
        });
      }
    }
    const d = Number(date.slice(8));
    if (d === 1 || d === 16 || r() < 0.15) {
      const n = 3 + Math.floor(r() * 6);
      for (let k = 0; k < n; k++) {
        out.push({
          type: '3D',
          draw_date: date,
          session: null,
          number: String(Math.floor(r() * 1000)).padStart(3, '0'),
          amount: pick(AMOUNTS),
          customer: pick(CUSTOMERS),
          remark: pick(REMARKS),
        });
      }
    }
  }
  return out;
}

export function loadSampleData(repo: Repo): number {
  const rows = buildSampleData();
  repo.addRecords(rows);
  repo.logEvent('SAMPLE', `Loaded ${rows.length} sample records`);
  return rows.length;
}
