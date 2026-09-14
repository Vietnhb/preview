const { chromium } = require(process.env.PHYSLIVE_PLAYWRIGHT);
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto('https://mathos-ochre.vercel.app/', { waitUntil: 'networkidle' });
  console.log((await page.locator('body').innerText()).slice(0, 16000));
  console.log(await page.getByText('Laboratory', { exact: true }).evaluateAll(nodes => nodes.map(n => ({ tag: n.tagName, href: n.getAttribute('href') }))));
  await page.getByRole('link', { name: 'Laboratory', exact: true }).click();
  await page.waitForLoadState('networkidle');
  console.log((await page.locator('body').innerText()).slice(0, 12000));
  await page.screenshot({ path: process.env.PHYSLIVE_REFERENCE_PNG, fullPage: true });
  await browser.close();
})().catch(error => { console.error(error); process.exitCode = 1; });
