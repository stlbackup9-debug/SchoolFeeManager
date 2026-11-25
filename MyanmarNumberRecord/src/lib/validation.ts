// Input validation and number-expansion rules for 2D / 3D entries.
// All functions are pure so they can be unit-tested without a database.

export type GameType = '2D' | '3D';
export type Session = 'AM' | 'PM';

export const MAX_AMOUNT = 100_000_000; // per single entry (Kyat)
export const MIN_AMOUNT = 1;
export const MAX_TEXT = 200;

export function digitsFor(type: GameType): number {
  return type === '2D' ? 2 : 3;
}

/** Convert Myanmar digits (၀-၉) to ASCII digits so users can type either. */
export function normalizeDigits(s: string): string {
  return s.replace(/[၀-၉]/g, (c) => String(c.charCodeAt(0) - 0x1040));
}

/** Validates one number. Returns the normalised number string or an error key. */
export function validateNumber(type: GameType, raw: string): { ok: true; value: string } | { ok: false; error: string } {
  const v = normalizeDigits(String(raw ?? '').trim());
  const n = digitsFor(type);
  if (v === '') return { ok: false, error: 'err.numberRequired' };
  if (!/^\d+$/.test(v)) return { ok: false, error: 'err.numberDigits' };
  if (v.length !== n) return { ok: false, error: type === '2D' ? 'err.number2d' : 'err.number3d' };
  return { ok: true, value: v };
}

/** Parses an amount: digits with optional thousands separators; must be a whole number of Kyat. */
export function validateAmount(raw: string | number): { ok: true; value: number } | { ok: false; error: string } {
  const s = normalizeDigits(String(raw ?? '').trim()).replace(/[,\s]/g, '');
  if (s === '') return { ok: false, error: 'err.amountRequired' };
  if (!/^\d+$/.test(s)) return { ok: false, error: 'err.amountWhole' };
  const value = Number(s);
  if (!Number.isSafeInteger(value) || value < MIN_AMOUNT) return { ok: false, error: 'err.amountPositive' };
  if (value > MAX_AMOUNT) return { ok: false, error: 'err.amountMax' };
  return { ok: true, value };
}

export function validateDate(raw: string): { ok: true; value: string } | { ok: false; error: string } {
  const s = String(raw ?? '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return { ok: false, error: 'err.date' };
  const [y, m, d] = s.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return { ok: false, error: 'err.date' };
  if (y < 2000 || y > 2100) return { ok: false, error: 'err.date' };
  return { ok: true, value: s };
}

export function validateSession(type: GameType, s: string | null | undefined): { ok: true; value: Session | null } | { ok: false; error: string } {
  if (type === '3D') return { ok: true, value: null };
  if (s === 'AM' || s === 'PM') return { ok: true, value: s };
  return { ok: false, error: 'err.session' };
}

export function cleanText(s: string | null | undefined): string {
  return String(s ?? '').replace(/\s+/g, ' ').trim().slice(0, MAX_TEXT);
}

/** "12" -> "21" */
export function reverse2D(n: string): string {
  return n.split('').reverse().join('');
}

/** All distinct digit arrangements of a 3D number: "123" -> 6 numbers, "112" -> 3, "111" -> 1. */
export function permutations3D(n: string): string[] {
  const [a, b, c] = n.split('');
  const all = [a + b + c, a + c + b, b + a + c, b + c + a, c + a + b, c + b + a];
  return Array.from(new Set(all));
}

/**
 * Parses a free-form list of numbers ("12 34,56") into validated numbers.
 * With `expand` the 2D reverse ("R") or 3D permutations are added.
 * Duplicates are removed, original order is kept.
 */
export function parseNumberList(
  type: GameType,
  raw: string,
  expand = false,
): { numbers: string[]; invalid: string[] } {
  const parts = normalizeDigits(raw)
    .split(/[\s,.;/+\-]+/)
    .map((p) => p.trim())
    .filter(Boolean);
  const out: string[] = [];
  const invalid: string[] = [];
  for (const p of parts) {
    const r = validateNumber(type, p);
    if (!r.ok) {
      invalid.push(p);
      continue;
    }
    const list = expand ? (type === '2D' ? [r.value, reverse2D(r.value)] : permutations3D(r.value)) : [r.value];
    for (const n of list) if (!out.includes(n)) out.push(n);
  }
  return { numbers: out, invalid };
}
