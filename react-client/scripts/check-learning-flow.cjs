// UI acceptance checks with intercepted API responses; never writes to the real backend.
const { chromium } = require(process.env.PHYSLIVE_PLAYWRIGHT || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const output = process.env.PHYSLIVE_QA_OUTPUT || process.cwd();
const specification = { id: 'qa-spec', schemaId: 'kinematics_1d', topic: 'KINEMATICS', confidence: 1, confirmationState: 'UNRESOLVED', objects: [], relations: [], quantities: [
  { name: 'velocity', normalizedValue: 10, normalizedUnit: 'm/s' }, { name: 'acceleration', normalizedValue: 2, normalizedUnit: 'm/s²' }, { name: 'time', normalizedValue: 5, normalizedUnit: 's' },
], ambiguities: [{ id: 'qa-ambiguity', code: 'surface', question: 'Có bỏ qua ma sát không?', options: ['Bỏ qua ma sát', 'Có ma sát'], status: 'UNRESOLVED' }] };
function simulation(parameters = { initial_velocity: 10, acceleration: 2 }, valid = true) {
  const time = Array.from({ length: 251 }, (_, i) => i / 50);
  const x = time.map(t => parameters.initial_velocity * t + parameters.acceleration * t * t / 2);
  const v = time.map(t => parameters.initial_velocity + parameters.acceleration * t);
  const a = time.map(() => parameters.acceleration);
  return { simulationId: 'qa-sim', simulationRunId: `qa-run-${Math.random()}`, specificationId: 'qa-spec', schemaId: specification.schemaId, validationPassed: valid, computationTimeMs: 3, time, positions: { x }, velocities: { x: v }, accelerations: { x: a }, values: { x, vx: v, ax: a }, adjustableParams: parameters, validation: { passed: valid, tolerance: 0.01, checkpoints: [] } };
}
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1366, height: 768 } });
    page.setDefaultTimeout(8000);
    const runtimeErrors = [];
    page.on('pageerror', error => runtimeErrors.push(error.message));
    let adjustMode = 'error', receivedParams, submittedText = '', currentKind = 'motion';
    const requests = [];
    await page.route('http://localhost:8080/api/**', async route => {
      const url = new URL(route.request().url()), endpoint = url.pathname.replace('/api', '');
      requests.push(endpoint);
      const json = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
      if (endpoint === '/auth/login') return json({ token: 'qa.' + Buffer.from(JSON.stringify({ exp: 9999999999 })).toString('base64') + '.qa', user: { id: 1, email: 'qa@example.test', fullName: 'QA', role: 'TEACHER' } });
      if (endpoint === '/user/me') return json({ id: 1, fullName: 'QA', role: 'TEACHER' });
      if (endpoint === '/problems') { submittedText = route.request().postDataJSON().text; return json({ id: 'qa-problem', originalText: submittedText, sourceMode: 'TEXT' }); }
      if (/\/problems\/qa-problem\/(extract|confirm)/.test(endpoint)) return json({ id: 'qa-problem', originalText: submittedText, sourceMode: 'TEXT', currentSpecification: specification });
      if (endpoint === '/specifications/qa-spec/ambiguities/qa-ambiguity/confirm') {
        specification.ambiguities[0].resolution = route.request().postDataJSON().answer;
        specification.ambiguities[0].status = 'RESOLVED';
        return json(specification);
      }
      if (endpoint === '/simulations') {
        const value = simulation();
        if (currentKind === 'circuit') {
          value.schemaId = 'circuits_rc_charging'; value.positions = {}; value.velocities = {}; value.accelerations = {}; value.adjustableParams = { voltage: 12, resistance: 1000 };
          value.values = { voltage: value.time.map(t => 12 * (1 - Math.exp(-t))), current: value.time.map(t => 0.012 * Math.exp(-t)) };
        }
        if (currentKind === 'spring') value.schemaId = 'dynamics_spring';
        return json(value);
      }
      if (endpoint === '/simulations/adjust') {
        receivedParams = route.request().postDataJSON().adjustableParams;
        if (adjustMode === 'error') return route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"QA offline"}' });
        return json(simulation(receivedParams, adjustMode !== 'invalid'));
      }
      if (endpoint.startsWith('/exports')) return route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
      throw new Error(`Unexpected backend request: ${endpoint}`);
    });
    await page.goto('http://localhost:5173/');
    await page.getByRole('textbox', { name: 'Nội dung đề bài' }).fill('Một xe có vận tốc 10 m/s, gia tốc 2 m/s² trong 5 s.');
    await page.getByRole('button', { name: 'Phân tích đề bài' }).click();
    await page.getByRole('textbox', { name: 'Email', exact: true }).fill('qa@example.test');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('qa-only-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    assert((await page.getByRole('textbox', { name: 'Nội dung đề bài' }).inputValue()).startsWith('Một xe có'), 'Draft survives login navigation');
    await page.screenshot({ path: path.join(output, 'learning-input.png') });
    await page.getByRole('button', { name: 'Phân tích đề bài' }).click();
    await page.getByRole('heading', { name: 'Cùng kiểm tra dữ kiện.' }).waitFor();
    assert(!(await page.getByRole('textbox', { name: 'Nội dung đề bài' }).count()), 'Input is collapsed after extraction');
    const confirm = page.getByRole('checkbox', { name: 'Tôi đã kiểm tra các dữ kiện và giả định.' });
    assert(await confirm.isDisabled(), 'Unanswered ambiguity blocks confirmation');
    await page.getByRole('button', { name: 'Bỏ qua ma sát', exact: true }).click();
    await confirm.check();
    await page.screenshot({ path: path.join(output, 'learning-confirm.png') });
    await page.getByRole('button', { name: 'Mở phòng học tương tác' }).click();
    await page.getByRole('heading', { name: 'Chuyển động trên đường thẳng', exact: true }).waitFor();
    assert(!(await page.locator('.sidebar').count()), 'No dashboard sidebar in the learning workspace');
    await page.locator('#parameter-initial_velocity').fill('18');
    await page.getByRole('button', { name: 'Áp dụng & chạy lại' }).click();
    await page.getByRole('alert').filter({ hasText: 'Chưa thể cập nhật' }).waitFor();
    assert((await page.locator('.learn-readouts').innerText()).includes('10'), 'Existing run retained after network error');
    adjustMode = 'invalid';
    await page.getByRole('button', { name: 'Áp dụng & chạy lại' }).click();
    await page.getByRole('alert').filter({ hasText: 'chưa vượt qua kiểm tra' }).waitFor();
    assert((await page.locator('.learn-readouts').innerText()).includes('10'), 'Invalid new run is not published as verified');
    adjustMode = 'success';
    await page.getByRole('button', { name: 'Áp dụng & chạy lại' }).click();
    await page.waitForFunction(() => document.querySelector('.learn-readouts').innerText.includes('18'));
    assert.equal(receivedParams.initial_velocity, 18, 'Uses actual backend parameter key');
    assert.equal(receivedParams.acceleration, 2, 'Retains the other parameter');
    await page.getByRole('tab', { name: 'Thử nghiệm', exact: true }).focus();
    await page.keyboard.press('ArrowRight');
    assert(await page.getByRole('tab', { name: 'Hiểu bản chất' }).getAttribute('aria-selected') === 'true', 'Arrow-key tab navigation');
    await page.getByRole('tab', { name: 'Bảng số liệu', exact: true }).click();
    await page.getByRole('button', { name: 'Tải PDF', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Chưa tải được dữ liệu' }).waitFor();
    await page.getByRole('tab', { name: 'Đồ thị', exact: true }).click();
    // Different kinds expose relevant controls and units rather than a generic velocity field.
    for (const kind of ['circuit', 'spring']) {
      currentKind = kind;
      await page.getByRole('link', { name: 'Về đề bài', exact: true }).click();
      await page.getByRole('checkbox', { name: 'Tôi đã kiểm tra các dữ kiện và giả định.' }).check();
      await page.getByRole('button', { name: 'Mở phòng học tương tác' }).click();
      await page.locator('.learn-titlebar').waitFor();
      const key = kind === 'circuit' ? 'voltage' : 'amplitude';
      assert(await page.locator(`#parameter-${key}`).isVisible(), `${kind} exposes appropriate parameter`);
      assert(!(await page.locator('#parameter-initial_velocity').count()), `${kind} has no meaningless velocity control`);
      await page.screenshot({ path: path.join(output, `learning-${kind}.png`) });
    }
    assert.deepEqual(runtimeErrors, [], 'No runtime errors across main flow');
    console.log('PASS: login draft, input → review → player, backend parameter contract, errors/retry, validation failure, keyboard tabs, export errors and schema-specific controls. Requests:', requests.length);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
