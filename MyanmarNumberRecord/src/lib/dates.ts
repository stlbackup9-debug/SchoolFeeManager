// Dates are stored as local calendar dates "YYYY-MM-DD" (no time zone shifts).

const pad = (n: number) => String(n).padStart(2, '0');

export function toISODate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function today(): string {
  return toISODate(new Date());
}

export function parseISODate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(s: string, n: number): string {
  const d = parseISODate(s);
  d.setDate(d.getDate() + n);
  return toISODate(d);
}

/** Monday of the week containing `s`. */
export function startOfWeek(s: string): string {
  const d = parseISODate(s);
  const dow = (d.getDay() + 6) % 7; // Mon=0 .. Sun=6
  d.setDate(d.getDate() - dow);
  return toISODate(d);
}

export function startOfMonth(s: string): string {
  return s.slice(0, 8) + '01';
}

export function endOfMonth(s: string): string {
  const d = parseISODate(startOfMonth(s));
  d.setMonth(d.getMonth() + 1);
  d.setDate(0);
  return toISODate(d);
}

/** Inclusive list of dates from a to b. */
export function dateRange(a: string, b: string): string[] {
  const out: string[] = [];
  for (let d = a; d <= b && out.length < 4000; d = addDays(d, 1)) out.push(d);
  return out;
}

/** Default 2D session for "now": Morning until 12:01, then Evening. */
export function defaultSession(now = new Date()): 'AM' | 'PM' {
  return now.getHours() * 60 + now.getMinutes() <= 12 * 60 + 1 ? 'AM' : 'PM';
}

export function nowStamp(): string {
  const d = new Date();
  return `${toISODate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function fileStamp(): string {
  const d = new Date();
  return `${toISODate(d).replace(/-/g, '')}-${pad(d.getHours())}${pad(d.getMinutes())}`;
}
