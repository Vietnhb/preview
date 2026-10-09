import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';

// Run against Vite. PLAYWRIGHT_MODULE may point to an installed Playwright module URL.
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.PRESENTATION_TEST_URL || 'http://127.0.0.1:5175';
const output = new URL('../../output/presentation-regression/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1024, height: 900 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const load = async (query = '') => {
    await page.goto(base + '/tests/scenePresentation.browser.html' + query);
    await page.locator('.sim-stage__loading').waitFor({ state: 'hidden' });
  };
  await load();
  assert.equal(await page.locator('[role="alert"]').count(), 0);
  assert.equal(await page.locator('.sim-notice').count(), 0);
  assert.equal(await page.locator('.sim-focus-readouts > div').count(), 4);
  assert.match(await page.locator('.sim-focus-readouts').innerText(), /1.714 A/);
  assert.equal(await page.locator('.sim-data-details').getAttribute('open'), null);
  await page.evaluate(() => window.changeValues());
  await page.waitForFunction(() => document.querySelector('.sim-focus-readouts').textContent.includes('3.429 A'));
  assert.equal(await page.locator('.sim-notice').count(), 0, 'updated solver data must still pass cross-check');
  await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click();
  await page.screenshot({ path: new URL('desktop.png', output).pathname.replace(/^\/(\w:)/, '$1'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(350);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile page must not overflow horizontally');
  assert.equal(await page.locator('.sim-notice').count(), 0);
  await page.screenshot({ path: new URL('mobile.png', output).pathname.replace(/^\/(\w:)/, '$1'), fullPage: true });
  await page.evaluate(() => document.documentElement.dataset.theme = 'light');
  await page.waitForTimeout(350);
  assert.equal(await page.locator('.sim-notice').count(), 0);
  await page.locator('.sim-data-details > summary').click();
  assert.match(await page.locator('.sim-readouts').innerText(), /6 Ω/);
  await load('?legacy');
  assert.equal(await page.locator('.sim-notice').count(), 0, 'old scenes work without annotations or labelAnchor');
  await load('?renamed');
  assert.equal(await page.locator('.sim-notice').count(), 0, 'arbitrary identifiers and another quantity must render');
  assert.match(await page.locator('.sim-focus-readouts').innerText(), /1.714 Pa/);
  await load('?bad-anchor');
  await page.locator('.sim-notice').waitFor();
  assert.match(await page.locator('.sim-notice').textContent(), /labelAnchor must be inside/);
  await load('?invalid');
  await page.locator('.sim-notice').waitFor();
  assert.match(await page.locator('.sim-notice').textContent(), /unknown solver field/i);
  assert.equal(await page.getByRole('tab', { name: 'Chuẩn học thuật' }).getAttribute('aria-selected'), 'true');
  assert.deepEqual(errors, []);
  console.log('PASS: browser render, recompute, pause, resize, light theme, full data, legacy scene, invalid-field rejection.');
} finally {
  await browser.close();
}
