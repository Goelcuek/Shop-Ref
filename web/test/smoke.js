// Browser smoke test for the bundled web app: node web/test/smoke.js web/dist/index.html
// Needs the `playwright` package and a Chromium build (npx playwright install chromium).
'use strict';
const { chromium } = require('playwright');
const path = require('path');

let failures = 0;
const check = (cond, msg) => {
  console.log((cond ? 'ok   ' : 'FAIL ') + msg);
  if (!cond) failures += 1;
};

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (width, height) => {
    const page = await browser.newPage({ viewport: { width, height } });
    page.on('pageerror', (e) => errors.push(e.message));
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    await page.goto('file://' + path.resolve(process.argv[2] || 'web/dist/index.html'));
    return page;
  };
  const p = await open(1440, 900);
  const tile = (i) => p.$eval(`#kpis .kpi:nth-child(${i}) .v`, (e) => e.firstChild.textContent);
  const lifeMin = () => p.$eval('#results .life .big', (e) => +e.firstChild.textContent);
  const blur = () => p.click('footer.site');

  // Every calculator and reference page renders, in both unit systems.
  for (const units of ['IMPERIAL', 'METRIC']) {
    await p.click(`[data-units="${units}"]`);
    for (const tab of ['drill', 'tap', 'turn', 'mill']) {
      await p.click(`[data-tab="${tab}"]`);
      check(await p.$$eval('#results .card', (c) => c.length) >= 2, `${tab} results render (${units})`);
    }
    await p.click('[data-tab="ref"]');
    for (const ref of await p.$$eval('[data-ref]', (els) => els.map((e) => e.dataset.ref))) {
      await p.click(`[data-ref="${ref}"]`);
      check((await p.textContent('#refContent')).trim().length > 50, `reference ${ref} renders (${units})`);
    }
    await p.click('[data-tab="mill"]');
  }

  // The pinned summary updates live while typing.
  const rpm0 = +(await tile(1));
  await p.click('[data-num="mill.diameter"]', { clickCount: 3 });
  await p.keyboard.type('6');
  const rpm1 = +(await tile(1));
  check(rpm1 > rpm0 * 1.5, `rpm rises when diameter 12 → 6 mm (${rpm0} → ${rpm1})`);
  await p.$eval('.right', (e) => { e.scrollTop = 900; });
  const top = await p.$eval('#kpis', (e) => e.getBoundingClientRect().top);
  check(top >= 50 && top <= 60, `key numbers stay pinned while results scroll (top ${top})`);
  check(await p.evaluate(() => window.scrollY) === 0, 'desktop page itself does not scroll');

  // Tool life: the standard recommendation gives the reference life; a faster spindle shortens it (Taylor).
  // (12 mm keeps the spindle below the machine limit, which would otherwise slow the cut and lengthen life.)
  await p.fill('[data-num="mill.diameter"]', '12');
  check(await lifeMin() === 45, 'tool life at the recommendation is the 45 min carbide basis');
  await p.$eval('[data-range="mill.speedPercent"]', (e) => { e.value = 120; e.dispatchEvent(new Event('input', { bubbles: true })); });
  check(await lifeMin() === 22, `speed +20% → ≈22 min (got ${await lifeMin()})`);
  await p.click('[data-reset="mill.speedPercent"]');

  // Force chart: hover readout lists every series; a table view exists.
  await p.hover('#results .chart svg', { position: { x: 300, y: 80 } });
  check(await p.$eval('#results .chart .tip', (e) => e.style.display === 'block' && e.children.length === 5), 'chart hover shows angle + 4 series');
  check(await p.$$eval('#results .chart details tbody tr', (r) => r.length) >= 20, 'chart has a table view');

  // Typing is not disturbed by re-rendering.
  await p.click('[data-num="mill.diameter"]', { clickCount: 3 });
  await p.keyboard.type('6.');
  check(await p.inputValue('[data-num="mill.diameter"]') === '6.', 'trailing "." survives in a calculator field');
  await p.keyboard.type('35');
  await blur();
  check(await p.inputValue('[data-num="mill.diameter"]') === '6.35', 'value kept after blur');

  // Untouched defaults switch to round inch sizes; inch fractions parse; clearing returns to the recommendation.
  await p.click('[data-units="IMPERIAL"]');
  await p.click('[data-tab="drill"]');
  check(await p.inputValue('[data-num="drill.diameter"]') === '0.375', 'default drill becomes 3/8" in inch mode');
  check(/≈ \d+ holes/.test(await p.textContent('#kpis')), 'drilling shows holes per drill');
  await p.click('[data-tab="mill"]');
  await p.click('[data-num="mill.diameter"]', { clickCount: 3 });
  await p.keyboard.type('1/2');
  await blur();
  check(await p.inputValue('[data-num="mill.diameter"]') === '0.5', 'inch fraction 1/2 parsed');
  await p.fill('[data-num="mill.ap"]', '0.25');
  await p.fill('[data-num="mill.ap"]', '');
  check(/^auto 0\.\d+$/.test(await p.getAttribute('[data-num="mill.ap"]', 'placeholder')), 'cleared field shows the auto value');

  // Reference calculators.
  await p.click('[data-tab="ref"]');
  await p.click('[data-ref="CALCULATORS"]');
  await p.click('[data-units="METRIC"]');
  const dia = p.locator('[data-rnum="dia"]').first();
  await dia.click({ clickCount: 3 });
  await p.keyboard.type('20.');
  check(await dia.inputValue() === '20.', 'trailing "." survives in a reference field');
  await p.keyboard.type('5');
  check(/1553 rpm/.test(await p.textContent('#refContent')), '100 m/min at Ø20.5 mm = 1553 rpm');
  await p.locator('[data-rnum="base"][data-sys="IMPERIAL"]').fill('1-1/4');
  check(await p.inputValue('[data-rnum="base"][data-sys="METRIC"]') === '31.75', '1-1/4 in = 31.75 mm');
  await p.click('[data-ref="FITS"]');
  check((await p.$$eval('.card h3', (h) => h.map((e) => e.innerText))).some((x) => x.includes('H7 / g6')), 'fit heading keeps the shaft letter lower-case');

  // Every milling cutter calculates; insert cutters get their own fields and defaults.
  await p.click('[data-tab="mill"]');
  const types = await p.$$eval('[data-sel="mill.toolType"] option', (o) => o.map((x) => x.value));
  check(types.length >= 12, `${types.length} milling cutter types`);
  for (const id of types) {
    await p.selectOption('[data-sel="mill.toolType"]', id);
    const ok = await p.$$eval('#kpis .kpi .v', (v) => v.length === 8 && v.every((x) => /\d/.test(x.textContent)));
    check(ok, `${id} calculates`);
  }
  await p.selectOption('[data-sel="mill.toolType"]', 'FACE_MILL_45');
  check(await p.inputValue('[data-num="mill.diameter"]') === '63', 'face mill loads its 63 mm default');
  check(await p.$('[data-num="mill.stickout"]') === null, 'shell mill has no stick-out field');
  check(await p.$('[data-num="mill.insertSize"]') !== null, 'insert size field shown');
  check(/1\.41×/.test(await p.textContent('#results')), '45° lead gives √2 chip thinning');
  await p.selectOption('[data-sel="mill.toolType"]', 'SQUARE_END');

  // Material picker.
  await p.click('[data-tab="mill"]');
  await p.click('[data-action="material"]');
  await p.fill('#matSearch', 'inconel 718');
  await p.click('.matrow');
  check((await p.textContent('.matsel .name')).includes('Nickel alloy'), 'material picker selects Inconel 718');

  // Turkish persists across reload and reaches the engine's advice.
  await p.click('[data-lang="tr"]');
  await p.reload();
  check((await p.textContent('[data-tab="mill"]')) === 'Frezeleme', 'Turkish persists across reload');
  await p.fill('[data-num="mill.stickout"]', '80');
  const advice = await p.$$eval('.adv', (els) => els.map((e) => e.textContent));
  check(advice.some((a) => /Sarkma/.test(a)), 'advice is translated');

  // Phone width: no horizontal page scroll.
  const m = await open(390, 844);
  check(await m.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'no horizontal scroll at 390 px');
  check(await m.$$eval('#kpis .kpi', (k) => k.filter((e) => e.offsetParent).length) === 6, 'six key numbers on a phone');
  await m.evaluate(() => window.scrollTo(0, 1500));
  const mtop = await m.$eval('#kpis', (e) => e.getBoundingClientRect().top);
  check(mtop > 0 && mtop < 120, `phone key numbers stay pinned (top ${mtop})`);

  check(errors.length === 0, 'no JavaScript errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await browser.close();
  if (failures) {
    console.log(`${failures} check(s) failed`);
    process.exit(1);
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
