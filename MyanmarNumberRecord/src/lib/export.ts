// File output: Excel (.xlsx), PDF, printing and saving files on web / Android.
import * as XLSX from 'xlsx';
import { Capacitor } from '@capacitor/core';
import type { ReportDoc } from './reports';
import { reportHtml } from './reports';
import type { T } from './i18n';
import type { RecordRow } from '../db/repo';

export const isNative = () => Capacitor.isNativePlatform();

function toBase64(bytes: Uint8Array): string {
  let s = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) s += String.fromCharCode(...bytes.subarray(i, i + chunk));
  return btoa(s);
}

/** Saves a file. Browser/desktop: normal download. Android: share sheet (save to Files, Drive, Excel…). */
export async function saveFile(name: string, data: Uint8Array | Blob, mime: string): Promise<string> {
  const bytes = data instanceof Blob ? new Uint8Array(await data.arrayBuffer()) : data;
  if (isNative()) {
    const { Filesystem, Directory } = await import('@capacitor/filesystem');
    const { Share } = await import('@capacitor/share');
    const res = await Filesystem.writeFile({ path: `exports/${name}`, data: toBase64(bytes), directory: Directory.Cache, recursive: true });
    try {
      // Also keep a copy in the app's Documents folder.
      await Filesystem.writeFile({ path: `2D3D/${name}`, data: toBase64(bytes), directory: Directory.Documents, recursive: true });
    } catch {
      /* optional */
    }
    await Share.share({ title: name, files: [res.uri] }).catch(() => undefined);
    return name;
  }
  const blob = new Blob([bytes], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
  return name;
}

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

function sheetName(title: string, used: Set<string>): string {
  let base = title.replace(/[[\]:*?/\\]/g, ' ').trim().slice(0, 28) || 'Sheet';
  let name = base;
  for (let i = 2; used.has(name); i++) name = `${base.slice(0, 26)} ${i}`;
  used.add(name);
  return name;
}

/** Builds the workbook bytes for a report (one sheet per section). Exported for tests. */
export function reportWorkbook(doc: ReportDoc): Uint8Array {
  const wb = XLSX.utils.book_new();
  const used = new Set<string>();
  for (const s of doc.sections) {
    const aoa: (string | number)[][] = [];
    if (doc.business) aoa.push([doc.business]);
    aoa.push([doc.title]);
    aoa.push([doc.subtitle]);
    aoa.push([s.title]);
    aoa.push([]);
    aoa.push(s.columns.map((c) => c.label));
    for (const r of s.rows) aoa.push(s.columns.map((c) => r[c.key] ?? ''));
    if (s.totals) aoa.push(s.columns.map((c) => s.totals![c.key] ?? ''));
    const ws = XLSX.utils.aoa_to_sheet(aoa);
    ws['!cols'] = s.columns.map((c) => ({ wch: c.kind === 'text' || !c.kind ? 22 : 14 }));
    // Number formats: thousands separators for money, keep 2D/3D numbers as text (leading zeros).
    const headerRow = aoa.length - s.rows.length - (s.totals ? 1 : 0);
    for (let ri = headerRow; ri < aoa.length; ri++) {
      s.columns.forEach((c, ci) => {
        const cell = ws[XLSX.utils.encode_cell({ r: ri, c: ci })];
        if (!cell) return;
        if (c.kind === 'money' && cell.t === 'n') cell.z = '#,##0';
        if (c.kind === 'pct' && cell.t === 'n') cell.z = '0.00"%"';
        if (c.kind === 'num') cell.t = 's';
      });
    }
    XLSX.utils.book_append_sheet(wb, ws, sheetName(s.title, used));
  }
  return new Uint8Array(XLSX.write(wb, { type: 'array', bookType: 'xlsx' }));
}

export async function exportReportXlsx(doc: ReportDoc): Promise<string> {
  return saveFile(`${doc.fileBase}.xlsx`, reportWorkbook(doc), XLSX_MIME);
}

export function recordsWorkbook(rows: RecordRow[], t: T): Uint8Array {
  const header = ['ID', t('c.type'), t('c.date'), t('c.session'), t('c.number'), t('c.amount'), t('c.customer'), t('c.remark'), t('c.created')];
  const aoa: (string | number)[][] = [header];
  for (const r of rows)
    aoa.push([r.id, r.type, r.draw_date, r.session === 'AM' ? t('c.morning') : r.session === 'PM' ? t('c.evening') : '', r.number, r.amount, r.customer, r.remark, r.created_at]);
  aoa.push(['', '', '', '', t('c.total'), rows.reduce((s, r) => s + r.amount, 0), '', '', '']);
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [8, 6, 12, 10, 8, 14, 22, 22, 20].map((wch) => ({ wch }));
  for (let ri = 1; ri < aoa.length; ri++) {
    const n = ws[XLSX.utils.encode_cell({ r: ri, c: 4 })];
    if (n) n.t = 's';
    const a = ws[XLSX.utils.encode_cell({ r: ri, c: 5 })];
    if (a && a.t === 'n') a.z = '#,##0';
  }
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Records');
  return new Uint8Array(XLSX.write(wb, { type: 'array', bookType: 'xlsx' }));
}

export async function exportRecordsXlsx(rows: RecordRow[], t: T, name: string): Promise<string> {
  return saveFile(name, recordsWorkbook(rows, t), XLSX_MIME);
}

// ---------- PDF ----------

/**
 * Renders the report HTML with the browser (so Myanmar text is shaped correctly),
 * captures it with html2canvas and splits it into A4 pages at row boundaries.
 */
export async function reportPdfBytes(doc: ReportDoc, t: T): Promise<Uint8Array> {
  const [{ default: html2canvas }, { jsPDF }] = await Promise.all([import('html2canvas'), import('jspdf')]);
  const host = document.createElement('div');
  host.className = 'pdf-host';
  host.innerHTML = reportHtml(doc, t);
  document.body.appendChild(host);
  try {
    await document.fonts?.ready;
    const W = 794; // A4 width in CSS px at 96 dpi
    const pageH = Math.floor((W * 297) / 210);
    const margin = 36;
    const usable = pageH - margin * 2;
    const hostTop = host.getBoundingClientRect().top;
    // Candidate break points: bottom of every row / heading.
    const stops = Array.from(host.querySelectorAll('tr, h2, h3, .rep-head, .rep-sec'))
      .map((el) => el.getBoundingClientRect().bottom - hostTop)
      .sort((a, b) => a - b);
    const total = host.scrollHeight;
    const breaks: number[] = [0];
    while (total - breaks[breaks.length - 1] > usable) {
      const start = breaks[breaks.length - 1];
      const candidates = stops.filter((s) => s > start + 40 && s <= start + usable);
      breaks.push(candidates.length ? candidates[candidates.length - 1] : start + usable);
    }
    breaks.push(total);
    const scale = 2;
    const canvas = await html2canvas(host, { scale, backgroundColor: '#ffffff', windowWidth: W, width: W, height: total });
    const pdf = new jsPDF({ unit: 'px', format: [W, pageH], hotfixes: ['px_scaling'] });
    for (let i = 0; i < breaks.length - 1; i++) {
      const y0 = Math.floor(breaks[i]);
      const h = Math.ceil(breaks[i + 1] - y0);
      if (h <= 0) continue;
      const slice = document.createElement('canvas');
      slice.width = W * scale;
      slice.height = h * scale;
      slice.getContext('2d')!.drawImage(canvas, 0, y0 * scale, W * scale, h * scale, 0, 0, W * scale, h * scale);
      if (i > 0) pdf.addPage([W, pageH]);
      pdf.addImage(slice.toDataURL('image/jpeg', 0.92), 'JPEG', 0, margin, W, h);
      pdf.setFontSize(9);
      pdf.setTextColor(120);
      pdf.text(`${i + 1} / ${breaks.length - 1}`, W - margin, pageH - 14, { align: 'right' });
    }
    return new Uint8Array(pdf.output('arraybuffer'));
  } finally {
    host.remove();
  }
}

export async function exportReportPdf(doc: ReportDoc, t: T): Promise<string> {
  return saveFile(`${doc.fileBase}.pdf`, await reportPdfBytes(doc, t), 'application/pdf');
}

/** Prints a report. Android WebView cannot print directly, so there we create a PDF to print from. */
export async function printReport(doc: ReportDoc, t: T): Promise<void> {
  if (isNative()) {
    await exportReportPdf(doc, t);
    return;
  }
  let root = document.getElementById('print-root');
  if (!root) {
    root = document.createElement('div');
    root.id = 'print-root';
    document.body.appendChild(root);
  }
  root.innerHTML = reportHtml(doc, t);
  document.body.classList.add('printing');
  const done = () => {
    document.body.classList.remove('printing');
    root!.innerHTML = '';
    window.removeEventListener('afterprint', done);
  };
  window.addEventListener('afterprint', done);
  await document.fonts?.ready;
  window.print();
}
