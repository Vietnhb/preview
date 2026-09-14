// Run with PHYSLIVE_PLAYWRIGHT pointing to an installed Playwright package.
const { chromium } = require(process.env.PHYSLIVE_PLAYWRIGHT || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const output = process.env.PHYSLIVE_QA_OUTPUT || process.cwd();
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://localhost:5173/player?example=motion');
    await page.getByRole('button', { name: 'Phát mô phỏng', exact: true }).waitFor();
    await page.screenshot({ path: path.join(output, 'learning-desktop.png') });
    for (const size of [{ width: 1440, height: 900 }, { width: 1366, height: 768 }, { width: 1024, height: 768 }, { width: 390, height: 844 }, { width: 360, height: 740 }]) {
      await page.setViewportSize(size);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      const metrics = await page.evaluate(() => ({ width: innerWidth, height: innerHeight, documentWidth: document.documentElement.scrollWidth, documentHeight: document.documentElement.scrollHeight, playback: document.querySelector('.learn-playback').getBoundingClientRect().toJSON(), graph: document.querySelector('.learn-analysis').getBoundingClientRect().toJSON() }));
      console.log('layout', size, metrics);
      assert(metrics.documentWidth <= size.width + 1, 'No horizontal page overflow');
      assert(metrics.documentHeight <= size.height + 1, 'No vertical page overflow');
      assert(metrics.playback.bottom <= size.height && metrics.graph.bottom <= size.height, 'Playback and graph fit viewport');
      if (size.width === 390) await page.screenshot({ path: path.join(output, 'learning-mobile.png') });
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.getByRole('button', { name: 'Phát mô phỏng', exact: true }).click();
    await page.waitForTimeout(1000);
    await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click();
    const playedTime = Number(await page.getByRole('slider', { name: 'Thời gian mô phỏng', exact: true }).inputValue());
    assert(playedTime > 0.8 && playedTime < 1.5, `Playback follows real seconds with 20ms samples: ${playedTime}`);
    await page.getByRole('button', { name: 'Về đầu mô phỏng', exact: true }).click();
    await page.getByLabel('Tốc độ phát').selectOption('2');
    await page.getByRole('button', { name: 'Phát mô phỏng', exact: true }).click();
    await page.waitForTimeout(800);
    await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click();
    assert(Number(await page.getByRole('slider', { name: 'Thời gian mô phỏng', exact: true }).inputValue()) > 1.4, '2x playback');
    await page.getByRole('slider', { name: 'Thời gian mô phỏng', exact: true }).fill('5');
    await page.getByRole('button', { name: 'Phát lại', exact: true }).click();
    await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click();
    assert(Number(await page.getByRole('slider', { name: 'Thời gian mô phỏng', exact: true }).inputValue()) < 1, 'Replay restarts at the beginning');
    await page.locator('#parameter-initial_velocity').fill('');
    assert(await page.getByRole('button', { name: 'Áp dụng & chạy lại', exact: true }).isDisabled(), 'Empty number rejected');
    await page.locator('#parameter-initial_velocity').fill('26');
    await page.locator('#parameter-acceleration').fill('0');
    assert(await page.locator('.learn-apply-note').innerText().then(text => text.includes('chưa áp dụng')), 'Pending edits are clear');
    await page.getByRole('button', { name: 'Áp dụng & chạy lại', exact: true }).click();
    await page.getByRole('button', { name: 'Gia tốc', exact: true }).click();
    assert(await page.locator('.scene-arrow.red').count() === 0, 'Zero acceleration has no arrow');
    await page.getByRole('slider', { name: 'Thời gian mô phỏng', exact: true }).fill('5');
    assert((await page.locator('.learn-readouts').innerText()).includes('130'), 'Updated sample x(5) = 130m');
    await page.getByRole('tab', { name: 'Hiểu bản chất', exact: true }).click();
    await page.getByRole('button', { name: 'Xem gợi ý giải thích', exact: true }).click();
    assert(await page.locator('#learning-answer').isVisible(), 'Academic explanation is expandable');
    await page.screenshot({ path: path.join(output, 'learning-explanation.png') });
    await page.getByRole('tab', { name: 'Bảng số liệu', exact: true }).click();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Tải CSV', exact: true }).click();
    const download = await downloadPromise;
    assert(download.suggestedFilename().endsWith('.csv'), 'Example CSV export works');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: 'Thử nghiệm', exact: true }).click();
    assert(await page.locator('#parameter-initial_velocity').isVisible(), 'Mobile experiment reachable without scrolling the page');
    await page.screenshot({ path: path.join(output, 'learning-mobile-controls.png') });
    await page.getByRole('button', { name: 'Quan sát', exact: true }).click();
    assert(await page.getByRole('button', { name: 'Phát lại', exact: true }).isVisible(), 'Mobile returns to previous playback position');
    assert.deepEqual(errors, [], 'No browser runtime errors');
    console.log('PASS: layouts, playback timing, speed, replay, edits, zero vectors, explanation, CSV and mobile navigation.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
