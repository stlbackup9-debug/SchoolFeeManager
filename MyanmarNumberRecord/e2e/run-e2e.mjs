// End-to-end test: drives the real built app in Chromium and checks every major function.
// Usage: npm run build && npm run build:single && npm run e2e
import { createServer } from 'node:http';
import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join, extname, resolve } from 'node:path';
import * as XLSX from 'xlsx';
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';

// Use a local Playwright if installed (npm i -D playwright), otherwise the global one.
async function loadPlaywright() {
  try {
    return await import('playwright');
  } catch {
    const req = createRequire(join(execSync('npm root -g').toString().trim(), 'noop.js'));
    return req('playwright');
  }
}
const { chromium } = await loadPlaywright();

const ROOT = resolve('dist');
const SHOTS = resolve('e2e/screens');
mkdirSync(SHOTS, { recursive: true });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.woff2': 'font/woff2', '.woff': 'font/woff' };
const server = createServer((req, res) => {
  let p = join(ROOT, decodeURIComponent(req.url.split('?')[0]));
  if (!existsSync(p) || p === ROOT + '/') p = join(ROOT, 'index.html');
  res.writeHead(200, { 'content-type': types[extname(p)] ?? 'application/octet-stream' });
  res.end(readFileSync(p));
}).listen(0);
const URL0 = `http://127.0.0.1:${server.address().port}/`;

let passed = 0;
const failures = [];
async function step(name, fn) {
  try {
    await fn();
    passed++;
    console.log('  ✓', name);
  } catch (e) {
    failures.push(name);
    console.log('  ✗', name, '\n    ', String(e.message ?? e).split('\n').slice(0, 6).join('\n     '));
  }
}
const assert = (c, m) => { if (!c) throw new Error(m); };
const eq = (a, b, m) => { if (a !== b) throw new Error(`${m}: expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`); };

const browser = await chromium.launch();
const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1360, height: 900 } });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));

const statText = async (i) => (await page.locator('.stat .stat-value').nth(i).textContent()).trim();
const toast = async (re) => { await page.getByTestId('toast').filter({ hasText: re }).first().waitFor({ timeout: 5000 }); };
const download = async (click) => { const [d] = await Promise.all([page.waitForEvent('download'), click()]); return { name: d.suggestedFilename(), bytes: readFileSync(await d.path()) }; };
const repoCall = (expr) => page.evaluate(expr);

console.log('E2E against', URL0);
await page.goto(URL0);
await page.getByTestId('nav-dashboard').waitFor();

await step('dashboard starts empty', async () => {
  eq(await statText(0), '0', 'records');
  eq(await statText(1), '0', 'amount');
});

await step('2D bulk entry with R (reverse) saves 4 records', async () => {
  await page.getByTestId('quick-2d').click();
  await page.getByTestId('session-AM').click();
  await page.getByTestId('entry-numbers').fill('12 34');
  await page.getByTestId('entry-expand').check();
  await page.getByTestId('entry-amount').fill('1,000');
  await page.getByTestId('entry-customer').fill('ကိုအောင်');
  const pv = await page.getByTestId('entry-preview').textContent();
  assert(pv.includes('12') && pv.includes('21') && pv.includes('34') && pv.includes('43') && pv.includes('4,000'), 'preview: ' + pv);
  await page.getByTestId('entry-save').click();
  await toast(/4 record/);
  eq(await page.getByTestId('entry-numbers').inputValue(), '', 'number field cleared');
});

await step('duplicate entry is detected and can be cancelled', async () => {
  await page.getByTestId('entry-numbers').fill('12');
  await page.getByTestId('entry-save').click();
  await page.getByText('Possible duplicate entry').waitFor();
  await page.getByRole('button', { name: 'Cancel' }).click();
  eq(await repoCall(() => window.__repo.allTimeCount()), 4, 'count after cancel');
});

await step('invalid number and amount are rejected', async () => {
  await page.getByTestId('entry-numbers').fill('123');
  await page.getByTestId('entry-amount').fill('abc');
  await page.getByTestId('entry-save').click();
  await page.getByText('2D number must be 2 digits').waitFor();
  await page.getByText('Amount must be a whole number').waitFor();
  await page.getByTestId('entry-amount').fill('0');
  await page.getByText('Amount must be greater than 0').waitFor({ state: 'detached' }).catch(() => {});
  eq(await repoCall(() => window.__repo.allTimeCount()), 4, 'nothing saved');
});

await step('3D entry saves', async () => {
  await page.getByTestId('type-3D').click();
  await page.getByTestId('entry-numbers').fill('123');
  await page.getByTestId('entry-amount').fill('500');
  await page.getByTestId('entry-save').click();
  await toast(/1 record/);
  await page.getByTestId('type-2D').click();
  await page.getByTestId('session-PM').click();
  await page.getByTestId('entry-numbers').fill('၀၇'); // Myanmar digits
  await page.getByTestId('entry-amount').fill('2500');
  await page.getByTestId('entry-save').click();
  await toast(/1 record/);
});

await step('dashboard totals are correct', async () => {
  await page.getByTestId('nav-dashboard').click();
  eq(await statText(0), '6', 'today records');
  eq(await statText(1), '7,000', 'today amount');
  eq(await statText(2), '6,500', '2D total');
  eq(await statText(3), '500', '3D total');
  eq(await statText(4), '6', 'all transactions');
  await page.screenshot({ path: join(SHOTS, 'dashboard-light.png') });
});

await step('records: search, filter, edit, delete, undo', async () => {
  await page.getByTestId('nav-records').click();
  await page.getByTestId('f-number').fill('21');
  await page.getByTestId('records-summary').filter({ hasText: '1 matching' }).waitFor();
  const id = await repoCall(() => window.__repo.queryRecords({ number: '21' }).rows[0].id);
  await page.getByTestId(`edit-${id}`).click();
  await page.getByTestId('edit-amount').fill('2500');
  await page.getByTestId('edit-save').click();
  await toast(/Record updated/);
  await page.getByTestId('records-summary').filter({ hasText: '2,500' }).waitFor();
  await page.getByTestId('f-number').fill('');
  await page.getByTestId('f-type-3D').click();
  await page.getByTestId('records-summary').filter({ hasText: '1 matching records · Total 500' }).waitFor();
  await page.getByTestId('f-type-all').click();
  await page.getByTestId('f-session-PM').click();
  await page.getByTestId('records-summary').filter({ hasText: '1 matching records · Total 2,500' }).waitFor();
  await page.getByTestId('f-session-all').click();
  await page.getByTestId('f-customer').fill('အောင်');
  await page.getByTestId('records-summary').filter({ hasText: '6 matching' }).waitFor();
  await page.getByTestId('f-customer').fill('');
  await page.getByTestId(`del-${id}`).click();
  await page.getByTestId('confirm-ok').click();
  await toast(/1 record\(s\) deleted/);
  eq(await repoCall(() => window.__repo.allTimeCount()), 5, 'after delete');
  await page.getByTestId('nav-history').click();
  await page.locator('[data-testid^="undo-"]').first().click();
  await toast(/restored/);
  eq(await repoCall(() => window.__repo.allTimeCount()), 6, 'after undo');
  eq(await repoCall(() => window.__repo.totals().total), 8500, 'total after edit+undo');
});

await step('records export to Excel', async () => {
  await page.getByTestId('nav-records').click();
  const { name, bytes } = await download(() => page.getByTestId('records-export').click());
  assert(name.endsWith('.xlsx'), name);
  const wb = XLSX.read(bytes);
  const rows = XLSX.utils.sheet_to_json(wb.Sheets.Records, { header: 1 });
  eq(rows.length, 8, 'header + 6 + total');
  eq(rows[7][5], 8500, 'excel total');
  assert(rows.slice(1, 7).some((r) => r[4] === '07'), 'leading zero kept');
});

await step('daily report shows correct totals; Excel + PDF export', async () => {
  await page.getByTestId('nav-reports').click();
  const view = page.getByTestId('report-view');
  await view.getByText('Grand Total').waitFor();
  const foot = await view.locator('table').first().locator('tfoot').textContent();
  assert(foot.includes('8,500') && foot.includes('6'), 'daily foot: ' + foot);
  const x = await download(() => page.getByTestId('rep-xlsx').click());
  const wb = XLSX.read(x.bytes);
  assert(wb.SheetNames.length === 5, 'sheets: ' + wb.SheetNames);
  const summary = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1 });
  eq(summary[summary.length - 1][2], 8500, 'xlsx grand total');
  const p = await download(() => page.getByTestId('rep-pdf').click());
  eq(p.bytes.subarray(0, 4).toString(), '%PDF', 'pdf header');
  writeFileSync(join(SHOTS, 'daily-report.pdf'), p.bytes);
  assert(p.bytes.length > 20000, 'pdf size ' + p.bytes.length);
  await page.screenshot({ path: join(SHOTS, 'report-daily.png') });
});

await step('other reports render', async () => {
  for (const k of ['byNumber', 'byDate', 'bySession', 'monthly', 'customers']) {
    await page.getByTestId('rep-kind').selectOption(k);
    await page.getByTestId('report-view').locator('table').first().waitFor();
  }
  await page.getByTestId('rep-kind').selectOption('byNumber');
  const txt = await page.getByTestId('report-view').textContent();
  assert(txt.includes('2D number grid'), 'grid present');
  await page.getByTestId('rep-kind').selectOption('customers');
  assert((await page.getByTestId('report-view').textContent()).includes('ကိုအောင်'), 'customer row');
});

let backupBytes;
await step('backup, sample data, clear all, restore', async () => {
  await page.getByTestId('nav-settings').click();
  const b = await download(() => page.getByTestId('backup').click());
  backupBytes = b.bytes;
  eq(b.bytes.subarray(0, 15).toString(), 'SQLite format 3', 'backup header');
  await page.getByTestId('load-sample').click();
  await page.getByTestId('confirm-ok').click();
  await toast(/sample records added/);
  const n = await repoCall(() => window.__repo.allTimeCount());
  assert(n > 100, 'sample count ' + n);
  await page.getByTestId('clear-all').click();
  assert(await page.getByTestId('confirm-ok').isDisabled(), 'needs DELETE');
  await page.getByTestId('confirm-text').fill('DELETE');
  await page.getByTestId('confirm-ok').click();
  await toast(/All records deleted/);
  eq(await repoCall(() => window.__repo.allTimeCount()), 0, 'cleared');
  await page.getByTestId('restore-file').setInputFiles({ name: 'b.sqlite', mimeType: 'application/octet-stream', buffer: b.bytes });
  await page.getByText('Restore backup with 6 records').waitFor();
  await page.getByTestId('confirm-ok').click();
  await toast(/Database restored/);
  eq(await repoCall(() => window.__repo.totals().total), 8500, 'restored total');
});

await step('restore rejects a non-database file', async () => {
  await page.getByTestId('restore-file').setInputFiles({ name: 'x.sqlite', mimeType: 'application/octet-stream', buffer: Buffer.from('hello world, not sqlite') });
  await toast(/not a SQLite/);
});

await step('Myanmar language and dark mode', async () => {
  await page.getByTestId('lang-my').click();
  await page.getByTestId('nav-dashboard').filter({ hasText: 'ပင်မစာမျက်နှာ' }).waitFor();
  await page.getByTestId('theme-dark').click();
  await page.getByTestId('nav-reports').click();
  const mp = await download(() => page.getByTestId('rep-pdf').click());
  writeFileSync(join(SHOTS, 'daily-report-my.pdf'), mp.bytes);
  eq(await page.evaluate(() => document.documentElement.dataset.theme), 'dark', 'theme');
  await page.getByTestId('nav-dashboard').click();
  await page.waitForTimeout(300);
  await page.screenshot({ path: join(SHOTS, 'dashboard-dark-my.png') });
  await page.getByTestId('nav-entry').click();
  await page.getByTestId('entry-form').waitFor();
  await page.getByTestId('entry-numbers').fill('25 52');
  await page.getByTestId('entry-amount').fill('1000');
  await page.screenshot({ path: join(SHOTS, 'entry-dark-my.png') });
});

await step('data persists after reload (autosave)', async () => {
  await page.waitForTimeout(600);
  await page.reload();
  await page.getByTestId('nav-dashboard').waitFor();
  eq(await repoCall(() => window.__repo.totals().total), 8500, 'after reload');
  eq(await page.evaluate(() => document.documentElement.dataset.theme), 'dark', 'theme persisted');
  await page.getByTestId('lang-toggle').click();
  await page.getByTestId('theme-toggle').click();
});

await step('mobile layout has no horizontal page scroll', async () => {
  const m = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  const mp = await m.newPage();
  mp.on('pageerror', (e) => errors.push(e.message));
  await mp.goto(URL0);
  await mp.getByTestId('nav-dashboard').waitFor();
  await mp.evaluate(() => { window.__repo.addRecords([{ type: '2D', draw_date: new Date().toISOString().slice(0, 10), session: 'AM', number: '55', amount: 1000 }]); });
  for (const p of ['dashboard', 'entry', 'records', 'reports', 'history', 'settings']) {
    await mp.goto(URL0 + '#/' + p);
    await mp.waitForTimeout(250);
    const over = await mp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    assert(over <= 0, `${p} overflows by ${over}px`);
    if (['dashboard', 'entry', 'records'].includes(p)) await mp.screenshot({ path: join(SHOTS, `mobile-${p}.png`) });
  }
  await m.close();
});

await step('single-file desktop build works from file:// and persists', async () => {
  const f = resolve('dist-single/index.html');
  assert(existsSync(f), 'dist-single missing (run npm run build:single)');
  const c = await browser.newContext({ acceptDownloads: true });
  const sp = await c.newPage();
  sp.on('pageerror', (e) => errors.push('single: ' + e.message));
  await sp.goto('file://' + f);
  await sp.getByTestId('nav-entry').click();
  await sp.getByTestId('entry-numbers').fill('99');
  await sp.getByTestId('entry-amount').fill('300');
  await sp.getByTestId('entry-save').click();
  await sp.getByTestId('toast').first().waitFor();
  await sp.waitForTimeout(600);
  await sp.reload();
  await sp.getByTestId('nav-dashboard').waitFor();
  const n = await sp.evaluate(() => window.__repo.queryRecords({ number: '99' }).rows.length);
  assert(n >= 1, 'persisted in file:// mode');
  await sp.goto('file://' + f + '#/reports');
  const p = await (async () => { const [d] = await Promise.all([sp.waitForEvent('download'), sp.getByTestId('rep-pdf').click()]); return readFileSync(await d.path()); })();
  eq(p.subarray(0, 4).toString(), '%PDF', 'pdf from single file');
  await c.close();
});

await step('no JavaScript errors', async () => {
  const real = errors.filter((e) => !/favicon/.test(e));
  assert(real.length === 0, real.join('\n'));
});

await browser.close();
server.close();
console.log(`\n${passed} passed, ${failures.length} failed`);
process.exit(failures.length ? 1 : 0);
