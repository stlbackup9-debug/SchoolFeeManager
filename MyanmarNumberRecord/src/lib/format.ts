export function fmtMoney(n: number): string {
  return Math.round(n || 0).toLocaleString('en-US');
}

export function fmtInt(n: number): string {
  return (n || 0).toLocaleString('en-US');
}

const MM_DIGITS = '၀၁၂၃၄၅၆၇၈၉';
export function toMyanmarDigits(s: string): string {
  return s.replace(/\d/g, (d) => MM_DIGITS[Number(d)]);
}
