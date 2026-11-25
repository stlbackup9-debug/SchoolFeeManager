import { describe, expect, it } from 'vitest';
import { parseNumberList, permutations3D, reverse2D, validateAmount, validateDate, validateNumber } from '../src/lib/validation';
import { addDays, endOfMonth, startOfWeek } from '../src/lib/dates';

describe('validation', () => {
  it('validates 2D numbers 00–99', () => {
    expect(validateNumber('2D', '00')).toEqual({ ok: true, value: '00' });
    expect(validateNumber('2D', '99')).toEqual({ ok: true, value: '99' });
    expect(validateNumber('2D', ' 07 ')).toEqual({ ok: true, value: '07' });
    expect(validateNumber('2D', '7').ok).toBe(false);
    expect(validateNumber('2D', '100').ok).toBe(false);
    expect(validateNumber('2D', 'ab').ok).toBe(false);
    expect(validateNumber('2D', '-1').ok).toBe(false);
    expect(validateNumber('2D', '').ok).toBe(false);
  });
  it('validates 3D numbers 000–999', () => {
    expect(validateNumber('3D', '000')).toEqual({ ok: true, value: '000' });
    expect(validateNumber('3D', '999')).toEqual({ ok: true, value: '999' });
    expect(validateNumber('3D', '99').ok).toBe(false);
    expect(validateNumber('3D', '1000').ok).toBe(false);
    expect(validateNumber('3D', '1.5').ok).toBe(false);
  });
  it('accepts Myanmar digits', () => {
    expect(validateNumber('2D', '၂၅')).toEqual({ ok: true, value: '25' });
    expect(validateAmount('၁,၀၀၀')).toEqual({ ok: true, value: 1000 });
  });
  it('validates amounts', () => {
    expect(validateAmount('1,000')).toEqual({ ok: true, value: 1000 });
    expect(validateAmount(500)).toEqual({ ok: true, value: 500 });
    expect(validateAmount('0').ok).toBe(false);
    expect(validateAmount('-5').ok).toBe(false);
    expect(validateAmount('10.5').ok).toBe(false);
    expect(validateAmount('abc').ok).toBe(false);
    expect(validateAmount('').ok).toBe(false);
    expect(validateAmount('100000001').ok).toBe(false);
    expect(validateAmount('1e5').ok).toBe(false);
  });
  it('validates dates', () => {
    expect(validateDate('2026-02-28').ok).toBe(true);
    expect(validateDate('2026-02-30').ok).toBe(false);
    expect(validateDate('2024-02-29').ok).toBe(true);
    expect(validateDate('26-1-1').ok).toBe(false);
  });
  it('reverses and permutes', () => {
    expect(reverse2D('12')).toBe('21');
    expect(permutations3D('123').sort()).toEqual(['123', '132', '213', '231', '312', '321']);
    expect(permutations3D('112').sort()).toEqual(['112', '121', '211']);
    expect(permutations3D('555')).toEqual(['555']);
  });
  it('parses number lists', () => {
    expect(parseNumberList('2D', '12 34,56 12')).toEqual({ numbers: ['12', '34', '56'], invalid: [] });
    expect(parseNumberList('2D', '12 3 x', false)).toEqual({ numbers: ['12'], invalid: ['3', 'x'] });
    expect(parseNumberList('2D', '12 55', true).numbers).toEqual(['12', '21', '55']);
    expect(parseNumberList('3D', '123', true).numbers.length).toBe(6);
  });
  it('date helpers', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
    expect(endOfMonth('2024-02-10')).toBe('2024-02-29');
    expect(startOfWeek('2026-11-25')).toBe('2026-11-23'); // Wed -> Mon
    expect(startOfWeek('2026-11-29')).toBe('2026-11-23'); // Sun -> Mon
  });
});
