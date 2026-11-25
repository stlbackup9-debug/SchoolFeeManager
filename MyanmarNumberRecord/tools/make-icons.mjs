// Generates the Android launcher icons and splash screens from SVG (run once: node tools/make-icons.mjs).
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import { join } from 'node:path';
import { readdirSync, existsSync } from 'node:fs';

const req = createRequire(join(execSync('npm root -g').toString().trim(), 'noop.js'));
const { chromium } = await import('playwright').catch(() => req('playwright'));
const RES = 'android/app/src/main/res';
const BRAND = '#0f766e';
const mark = (s) => `<text x="50%" y="50%" dy="0.35em" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-weight="800" font-size="${s}" fill="#fff" letter-spacing="-1">2D·3D</text>`;
const full = (round) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><${round ? 'circle cx="50" cy="50" r="50"' : 'rect width="100" height="100" rx="22"'} fill="${BRAND}"/>${mark(24)}<rect x="22" y="66" width="56" height="5" rx="2.5" fill="#5eead4"/></svg>`;
const fg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">${mark(19)}<rect x="34" y="66" width="40" height="4" rx="2" fill="#5eead4"/></svg>`;
const sizes = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const browser = await chromium.launch();
const page = await browser.newPage();
async function render(svg, w, h, file, bg = 'transparent') {
  await page.setViewportSize({ width: w, height: h });
  await page.setContent(`<html><body style="margin:0;background:${bg}"><div style="width:${w}px;height:${h}px;display:grid;place-items:center">${svg.replace('<svg ', `<svg width="${Math.min(w, h)}" height="${Math.min(w, h)}" `)}</div></body></html>`);
  await page.screenshot({ path: file, omitBackground: bg === 'transparent' });
}
for (const [d, k] of Object.entries(sizes)) {
  await render(full(false), 48 * k, 48 * k, `${RES}/mipmap-${d}/ic_launcher.png`);
  await render(full(true), 48 * k, 48 * k, `${RES}/mipmap-${d}/ic_launcher_round.png`);
  await render(fg, 108 * k, 108 * k, `${RES}/mipmap-${d}/ic_launcher_foreground.png`);
}
// Splash screens: brand colour with the logo centred.
const splash = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">${mark(24)}</svg>`;
for (const dir of readdirSync(RES).filter((x) => x.startsWith('drawable'))) {
  const f = `${RES}/${dir}/splash.png`;
  if (!existsSync(f)) continue;
  const dims = execSync(`file ${f}`).toString().match(/(\d+) x (\d+)/);
  const [w, h] = [Number(dims[1]), Number(dims[2])];
  await render(splash.replace('viewBox="0 0 100 100"', 'viewBox="-60 -60 220 220"'), w, h, f, BRAND);
}
await browser.close();
console.log('icons done');
