/* Shop Ref web app. All machining maths comes from the shared Kotlin engine (global `shopref`). */
'use strict';

// The Kotlin/JS UMD bundle registers itself under its Gradle coordinates when loaded by a plain <script>.
const E = globalThis.shopref || globalThis['com.shopref:core'];
const Q = E.quantities();
const OPT = E.options();
const MATS = E.materials();
const MAT = Object.fromEntries(MATS.map((m) => [m.id, m]));
const MILL_TYPE = Object.fromEntries(OPT.millToolTypes.map((m) => [m.id, m]));
const HSS_IDS = ['HSS', 'HSS_CO', 'PM_HSS'];
const GROUPS = E.isoGroups();
const GROUP = Object.fromEntries(GROUPS.map((g) => [g.id, g]));
const PRESETS = E.machinePresets();
const TR = E.dictionary();
const RELEASES = 'https://github.com/Goelcuek/Shop-Ref/releases/latest';
const REPO = 'https://github.com/Goelcuek/Shop-Ref';

// ------------------------------------------------------------------------------ storage & state

const store = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem('shopref.' + key);
      return v == null ? fallback : JSON.parse(v);
    } catch (e) {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem('shopref.' + key, JSON.stringify(value));
    } catch (e) { /* storage unavailable: keep working in memory */ }
  },
};

const browserLang = (navigator.language || 'en').toLowerCase().startsWith('tr') ? 'tr' : 'en';
const S = {
  lang: store.get('lang', browserLang),
  units: store.get('units', 'METRIC'),
  theme: store.get('theme', 'auto'),
  level: store.get('level', 'STANDARD'),
  mill: Object.assign({}, PRESETS[0], store.get('mill', {})),
  lathe: Object.assign({}, PRESETS[PRESETS.length - 1], store.get('lathe', {})),
  material: MAT[store.get('material', '')] ? store.get('material', '') : 'p_low_carbon',
  tab: store.get('tab', 'mill'),
  ref: store.get('ref', 'TAP_DRILLS'),
};

const DEF = {
  mill: {
    toolType: 'SQUARE_END', toolMaterial: 'CARBIDE', coating: 'ALTIN', diameter: 12, flutes: 4, helixDeg: 35, rakeDeg: null,
    cornerRadius: 1, chamferAngle: 90, chamferTipDiameter: 0, leadAngleDeg: null, insertSize: null, fluteLength: null, stickout: null,
    shankDiameter: null, strategy: 'SIDE_MILLING', ap: null, ae: null, climb: true, condition: 'NORMAL', speedPercent: 100, feedPercent: 100,
  },
  drill: { drillType: 'CARBIDE', coating: 'TIALN', diameter: 10, depth: 30, preHoleDiameter: 0, flutes: 6, condition: 'NORMAL', speedPercent: 100, feedPercent: 100, spotDia: 6, spotAngle: 90, spotTip: 0 },
  tap: {
    standard: 'METRIC_COARSE', thread: 'M8×1.25', tapType: 'SPIRAL_FLUTE', chamfer: 'SEMI_BOTTOMING', tapMaterial: 'HSS_CO', coating: 'TICN',
    flutes: 3, blindHole: true, threadDepth: 15, targetPercent: null, drillDiameter: null, condition: 'NORMAL', speedPercent: 100,
    millDia: null, millFlutes: 3,
  },
  turn: {
    grade: 'COATED_CARBIDE', operation: 'MEDIUM', diameter: 50, ap: null, feed: null, noseRadius: 0.8, enteringAngle: 95, rakeDeg: 6,
    lengthOfCut: 100, condition: 'NORMAL', speedPercent: 100, targetRa: 1.6, raNose: 0.8,
  },
};
const IN = {};
for (const k of Object.keys(DEF)) IN[k] = Object.assign({}, DEF[k], store.get('in.' + k, {}));
if (!MILL_TYPE[IN.mill.toolType]) IN.mill.toolType = DEF.mill.toolType;

/**
 * Picking another cutter loads its defaults. Size and tooth count change only when the cutter family's
 * default size differs (so square → ball keeps your diameter); depths, insert size and lead go back to auto.
 */
function applyMillType(id, previous) {
  const T = MILL_TYPE[id];
  const P = MILL_TYPE[previous] || T;
  if (T.defaultDiameter !== P.defaultDiameter) {
    IN.mill.diameter = imperial() ? T.defaultDiameterInch : T.defaultDiameter;
    IN.mill.flutes = T.defaultFlutes;
  }
  Object.assign(IN.mill, { insertSize: null, leadAngleDeg: null, ap: null, ae: null, stickout: null, fluteLength: null, shankDiameter: null, rakeDeg: null });
  if (T.defaultStrategy) IN.mill.strategy = T.defaultStrategy;
  if (T.indexable && id !== 'DISC' && HSS_IDS.includes(IN.mill.toolMaterial)) IN.mill.toolMaterial = 'CARBIDE';
}

let saveTimer = null;
function saveNow() {
  clearTimeout(saveTimer);
  saveTimer = null;
  for (const k of ['lang', 'units', 'theme', 'level', 'mill', 'lathe', 'material', 'tab', 'ref']) store.set(k, S[k]);
  for (const k of Object.keys(IN)) store.set('in.' + k, IN[k]);
}
/** Debounced save while typing; flushed when the page is hidden or closed. */
function persist() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(saveNow, 250);
}
window.addEventListener('pagehide', () => { if (saveTimer) saveNow(); });
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden' && saveTimer) saveNow(); });

// ------------------------------------------------------------------------------ i18n & units

function t(key, ...args) {
  let s = S.lang === 'tr' ? (TR[key] ?? key) : key;
  args.forEach((a, i) => { s = s.split('{' + i + '}').join(String(a)); });
  return s;
}
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const imperial = () => S.units === 'IMPERIAL';
const uLabel = (q) => t(imperial() ? Q[q].imperial : Q[q].metric);
const toDisp = (v, q) => (imperial() ? v * Q[q].toImperial : v);
const fromDisp = (v, q) => (imperial() ? v / Q[q].toImperial : v);
const decimals = (q) => (imperial() ? Q[q].imperialDecimals : Q[q].metricDecimals);

function fmtN(v, d) {
  if (v == null || Number.isNaN(v)) return '—';
  if (!Number.isFinite(v)) return v > 0 ? '∞' : '−∞';
  const s = v.toFixed(d);
  return s === '-0' || /^-0\.0*$/.test(s) ? s.slice(1) : s;
}
const fmt = (v, q, d = decimals(q)) => (v == null ? '—' : fmtN(toDisp(v, q), d));
const fmtU = (v, q, d) => {
  const u = uLabel(q);
  return fmt(v, q, d) + (u ? ' ' + u : '');
};
function fmtEdit(v, q, extra = 1) {
  if (v == null || !Number.isFinite(v)) return '';
  let s = toDisp(v, q).toFixed(decimals(q) + extra);
  if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
  return s;
}
function parseNum(text, allowFraction) {
  const s = String(text).trim().replace(',', '.');
  if (!s) return null;
  if (/^[-+]?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?$/i.test(s)) return parseFloat(s);
  if (!allowFraction) return NaN;
  let total = 0;
  for (const p of s.replace(/"/g, '').split(/[\s-]+/).filter(Boolean)) {
    if (p.includes('/')) {
      const [a, b] = p.split('/').map(Number);
      if (!Number.isFinite(a) || !Number.isFinite(b) || b === 0) return NaN;
      total += a / b;
    } else {
      const n = Number(p);
      if (!Number.isFinite(n)) return NaN;
      total += n;
    }
  }
  return total;
}

// ------------------------------------------------------------------------------ form widgets

/** Numeric input bound to IN[calc][key] (base units). `auto` names a result field used as placeholder. */
function num(calc, key, label, q, o = {}) {
  const v = IN[calc][key];
  const scale = o.scale || 1;
  const shown = v == null ? '' : fmtEdit(v * scale, q, o.extra ?? 1);
  const unit = q === 'NONE' ? (o.unit || '') : uLabel(q);
  return `<label class="field ${o.cls || ''}"><span class="lbl" title="${esc(t(label))}">${esc(t(label))}</span>
    <span class="inp"><input data-num="${calc}.${key}" data-q="${q}" data-scale="${scale}" ${o.min != null ? `data-min="${o.min}"` : ''}
      ${o.auto ? `data-auto="${o.auto}"` : ''} ${o.int ? 'data-int="1"' : ''} inputmode="decimal" autocomplete="off" value="${esc(shown)}">
    ${unit ? `<em>${esc(unit)}</em>` : ''}</span></label>`;
}

/** Select bound to IN[calc][key] (or S[key] when calc is 'S'). */
function sel(calc, key, label, options, o = {}) {
  const cur = calc === 'S' ? S[key] : IN[calc][key];
  const one = (op) => {
    const id = typeof op === 'object' ? op.id : op;
    const lab = typeof op === 'object' ? (o.raw ? op.label : t(op.label)) : String(op);
    const title = typeof op === 'object' && op.description ? ` title="${esc(t(op.description))}"` : '';
    return `<option value="${esc(id)}"${String(id) === String(cur) ? ' selected' : ''}${title}>${esc(lab)}</option>`;
  };
  // Options carrying a `group` are listed under <optgroup> headings, in first-seen order.
  const groups = [];
  for (const op of options) {
    const name = typeof op === 'object' ? op.group : null;
    let g = groups.find((x) => x.name === name);
    if (!g) groups.push(g = { name, items: [] });
    g.items.push(op);
  }
  const opts = groups.map((g) => (g.name ? `<optgroup label="${esc(t(g.name))}">${g.items.map(one).join('')}</optgroup>` : g.items.map(one).join(''))).join('');
  return `<label class="field ${o.cls || ''}"><span class="lbl">${esc(t(label))}</span>
    <select data-sel="${calc}.${key}" ${o.rerender ? 'data-rerender="1"' : ''} ${o.numeric ? 'data-numeric="1"' : ''}>${opts}</select></label>`;
}

function toggle(calc, key, label, detail) {
  return `<label class="switch field full"><input type="checkbox" data-tog="${calc}.${key}" ${IN[calc][key] ? 'checked' : ''}>
    <span>${esc(t(label))}${detail ? `<small>${esc(t(detail))}</small>` : ''}</span></label>`;
}

function slider(calc, key, label) {
  const v = IN[calc][key];
  return `<div class="slider field full"><span class="lbl">${esc(t(label))}</span>
    <span class="pct ${v !== 100 ? 'off' : ''}" data-reset="${calc}.${key}" title="100%">${Math.round(v)}%</span>
    <input type="range" min="50" max="200" step="5" value="${v}" data-range="${calc}.${key}"></div>`;
}

const card = (title, body, cls = '') => `<section class="card ${cls}"><h3>${title.replace(/µ\w+/g, '<span class="keep">$&</span>')}</h3>${body}</section>`;
const fields = (...items) => `<div class="fields">${items.filter(Boolean).join('')}</div>`;
const note = (text) => `<p class="note">${text}</p>`;

function rows(list) {
  return `<div class="rows">${list.filter(Boolean).map((r) => (r === '-' ? '<div class="sep"></div>'
    : `<div class="l">${esc(r[0])}</div><div class="r ${r[2] || ''}">${esc(r[1])}</div>`)).join('')}</div>`;
}

function adviceHtml(list) {
  if (!list || !list.length) return '';
  return `<div class="advice">${list.map((a) => `<div class="adv ${a.level}">${esc(a.text)}</div>`).join('')}</div>`;
}

function isoBadge(groupId, small) {
  const g = GROUP[groupId];
  return `<span class="iso ${groupId} ${small ? 'sm' : ''}" style="background:${g.color}" title="${esc(t(g.description))}">${g.label}</span>`;
}

function materialButton() {
  const m = MAT[S.material];
  return `<button class="matsel wide" data-action="material">${isoBadge(m.group)}
    <span class="meta"><span class="k">${esc(t('Workpiece material'))}</span>
    <span class="name">${esc(t(m.name))}</span><span class="ex">${esc(m.examples)} · ${esc(t(m.hardness))}</span></span>
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M10 3a7 7 0 0 1 5.6 11.2l5.1 5.1-1.4 1.4-5.1-5.1A7 7 0 1 1 10 3zm0 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10z"/></svg></button>`;
}

function warnClass(ratio) {
  if (ratio > 1) return 'danger';
  if (ratio > 0.85) return 'warn';
  return '';
}
const statusOf = (ratio) => ({ danger: 'critical', warn: 'warning' }[warnClass(ratio)] || '');
/** Same thresholds as the engine's tap advice: below 1.5 danger, below 2.5 caution. */
const tapStatus = (sf) => (sf < 1.5 ? 'critical' : sf < 2.5 ? 'warning' : '');

/** Rounds to [n] significant figures for estimates that should not look more precise than they are. */
function sig(v, n = 2) {
  if (v == null || !Number.isFinite(v)) return fmtN(v, 0);
  if (v === 0) return '0';
  const d = Math.floor(Math.log10(Math.abs(v))) + 1;
  if (d >= n) {
    const step = 10 ** (d - n);
    return String(Math.round(v / step) * step);
  }
  return v.toFixed(n - d);
}
const sigU = (v, q) => sig(toDisp(v, q)) + ' ' + uLabel(q);

// ------------------------------------------------------------------------------ key numbers

function kpiHtml(items) {
  return items.filter(Boolean).map((it) => `<div class="kpi ${it.status || ''} ${it.cls || ''}"><div class="k">${esc(it.k)}</div>
    <div class="v">${esc(it.v)}<span class="u">${esc(it.u || '')}</span></div>
    <div class="c">${it.status ? '<span class="st" aria-hidden="true">!</span>' : ''}${esc(it.c || '')}</div></div>`).join('');
}
const kpi = (k, v, q, extra = {}) => Object.assign({ k: t(k), v: fmt(v, q), u: uLabel(q) }, extra);
/** Tiles marked secondary are left out of the compact strip on phones (six fit; eight would be unreadable). */
const secondary = (item) => Object.assign(item, { cls: 'xs-hide' });
const powerKpi = (power, available) => kpi('Power', power, 'POWER', {
  status: statusOf(power / available), c: t('{0}% of {1} available', Math.round((100 * power) / available), fmtU(available, 'POWER')),
});
const lifeKpi = (life, caption) => ({ k: t('Tool life'), v: sig(life.minutes), u: t('min'), c: caption, status: life.outsideModel ? 'warning' : '' });

/** Tool-life card shared by the calculators: minutes, what they buy, and the model behind them. */
function lifeCard(life, extraRows) {
  return card(esc(t('Tool life (estimate)')), `<div class="big">${esc(sig(life.minutes))}<small>${esc(t('min of cutting'))}</small></div>
    <div class="sub">${esc(t('{0}% of the life at the standard recommendation ({1} min)', Math.round(life.factor * 100), sig(life.referenceMinutes)))}</div>`
    + rows(extraRows)
    + note(esc(t('Extended Taylor: T = Tref·(vref/v)^(1/n)·(href/h)^(0.3/n), n = {0}. Expect ±50% with coolant, runout and work-holding.', String(+life.n.toFixed(3))))), 'life');
}

// ------------------------------------------------------------------------------ force chart

let chartData = null;

function niceStep(range, ticks) {
  const raw = range / ticks;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const norm = raw / mag;
  return (norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10) * mag;
}

/** Force over one revolution: one axis (force), four series, hover/keyboard readout and a table twin. */
function forceChart(samples) {
  // Drawn at the card's real pixel width so text and line weights stay at their design size.
  const host = document.getElementById('results');
  const cs = host ? getComputedStyle(host) : null;
  const inner = host && host.clientWidth ? host.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight) : 760;
  const W = Math.round(Math.min(1600, Math.max(280, inner - 38)));
  const H = W < 560 ? 210 : 260;
  const PL = 54;
  const PR = 10;
  const PT = 10;
  const PB = 28;
  const series = [['fxy', '--s1', '|Fxy|'], ['fx', '--s2', t('Fx feed')], ['fy', '--s3', t('Fy normal')], ['fz', '--s4', t('Fz axial')]];
  const val = (s, k) => toDisp(s[k], 'FORCE');
  let max = 0;
  let min = 0;
  for (const s of samples) for (const [k] of series) { max = Math.max(max, val(s, k)); min = Math.min(min, val(s, k)); }
  const step = niceStep(Math.max(max - min, 1e-6), 4);
  const top = Math.max(step, Math.ceil(max / step) * step);
  const bottom = Math.floor(min / step) * step;
  const x = (a) => PL + (a / 360) * (W - PL - PR);
  const y = (v) => PT + ((top - v) / (top - bottom)) * (H - PT - PB);
  const dec = step < 1 ? 1 : 0;
  let grid = '';
  for (let v = bottom; v <= top + step / 2; v += step) {
    grid += `<line x1="${PL}" x2="${W - PR}" y1="${y(v).toFixed(1)}" y2="${y(v).toFixed(1)}" stroke="var(${Math.abs(v) < step / 2 ? '--axis' : '--grid'})" stroke-width="1"/>`
      + `<text x="${PL - 8}" y="${(y(v) + 4).toFixed(1)}" text-anchor="end">${fmtN(v, dec)}</text>`;
  }
  for (const a of [0, 90, 180, 270, 360]) grid += `<text x="${x(a).toFixed(1)}" y="${H - 8}" text-anchor="${a === 0 ? 'start' : a === 360 ? 'end' : 'middle'}">${a}°</text>`;
  const pts = samples.concat([Object.assign({}, samples[0], { a: 360 })]);
  const lines = series.map(([k, c]) => `<polyline fill="none" stroke="var(${c})" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"
    points="${pts.map((s) => `${x(s.a).toFixed(1)},${y(val(s, k)).toFixed(1)}`).join(' ')}"/>`).join('');
  chartData = { samples, series, x, y, val, W };
  const u = uLabel('FORCE');
  const every = Math.max(1, Math.round(samples.length / 24));
  const tableRows = samples.filter((_, i) => i % every === 0).map((s) => `<tr><td class="n">${fmtN(s.a, 0)}°</td>${series.map(([k]) => `<td class="n">${fmtN(val(s, k), dec)}</td>`).join('')}</tr>`).join('');
  return `<div class="chart">
    <div class="legend">${series.map(([, c, name]) => `<span><i style="background:var(${c})"></i>${esc(name)}</span>`).join('')}<span style="margin-left:auto;color:var(--muted)">${esc(u)}</span></div>
    <svg viewBox="0 0 ${W} ${H}" tabindex="0" role="img" aria-label="${esc(t('Force over one revolution'))}">${grid}${lines}
      <line class="xh" y1="${PT}" y2="${H - PB}" stroke="var(--ink-2)" stroke-width="1" visibility="hidden"/>
      <g class="dots" visibility="hidden">${series.map(([, c]) => `<circle r="4.5" fill="var(${c})" stroke="var(--surface)" stroke-width="2"/>`).join('')}</g>
      <rect class="hit" x="${PL}" y="${PT}" width="${W - PL - PR}" height="${H - PT - PB}" fill="transparent"/></svg>
    <div class="tip"></div>
    <details class="table"><summary>${esc(t('Show as table'))}</summary>${table([t('Angle'), ...series.map(([, , name]) => `${esc(name)} (${esc(u)})`)], tableRows, { num: true })}</details></div>`;
}

/** Hover / keyboard readout: the crosshair snaps to the nearest sample and lists every series. */
function bindChart() {
  const box = document.querySelector('#results .chart');
  if (!box || !chartData) return;
  const svg = box.querySelector('svg');
  const tip = box.querySelector('.tip');
  const xh = svg.querySelector('.xh');
  const dots = svg.querySelector('.dots');
  const { samples, series, x, y, val } = chartData;
  let idx = -1;
  const show = (i) => {
    idx = (i + samples.length) % samples.length;
    const s = samples[idx];
    const px = x(s.a);
    xh.setAttribute('x1', px);
    xh.setAttribute('x2', px);
    xh.setAttribute('visibility', 'visible');
    dots.setAttribute('visibility', 'visible');
    [...dots.children].forEach((c, j) => { c.setAttribute('cx', px); c.setAttribute('cy', y(val(s, series[j][0]))); });
    tip.textContent = '';
    const head = document.createElement('div');
    head.className = 'h';
    head.textContent = `${fmtN(s.a, 0)}°`;
    tip.appendChild(head);
    for (const [k, c, name] of series) {
      const row = document.createElement('div');
      const key = document.createElement('i');
      key.style.background = `var(${c})`;
      const b = document.createElement('b');
      b.textContent = fmtU(s[k], 'FORCE');
      const span = document.createElement('span');
      span.textContent = name;
      row.append(key, b, span);
      tip.appendChild(row);
    }
    tip.style.display = 'block';
    const scale = svg.getBoundingClientRect().width / chartData.W;
    const left = px * scale;
    tip.style.left = (left > box.clientWidth / 2 ? left - tip.offsetWidth - 12 : left + 12) + 'px';
  };
  const hide = () => {
    xh.setAttribute('visibility', 'hidden');
    dots.setAttribute('visibility', 'hidden');
    tip.style.display = 'none';
  };
  svg.addEventListener('pointermove', (e) => {
    const r = svg.getBoundingClientRect();
    const a = ((e.clientX - r.left) / r.width * chartData.W - x(0)) / (x(360) - x(0)) * 360;
    if (a < -5 || a > 365) { hide(); return; }
    show(Math.round((Math.min(359.999, Math.max(0, a)) / 360) * samples.length) % samples.length);
  });
  svg.addEventListener('pointerleave', hide);
  svg.addEventListener('blur', hide);
  svg.addEventListener('focus', () => show(idx < 0 ? 0 : idx));
  svg.addEventListener('keydown', (e) => {
    const stepN = Math.max(1, Math.round(samples.length / 72));
    if (e.key === 'ArrowRight') { show(idx + stepN); e.preventDefault(); }
    if (e.key === 'ArrowLeft') { show(idx - stepN); e.preventDefault(); }
  });
}

// ------------------------------------------------------------------------------ calculators

const common = (calc, machine) => Object.assign({}, IN[calc], { material: S.material, units: S.units, lang: S.lang, machine });

const CALCS = {
  mill: {
    title: 'Milling',
    compute: () => E.milling(Object.assign(common('mill', S.mill), { chipLoadLevel: S.level })),
    inputs() {
      const i = IN.mill;
      const T = MILL_TYPE[i.toolType];
      const ix = T.indexable;
      const arbor = T.arborMounted || (ix && i.diameter >= 40);
      // Insert cutters take carbide, ceramic or PCD inserts; only a disc (T-slot / keyseat) cutter may be HSS.
      const substrates = (ix && i.toolType !== 'DISC' ? OPT.toolMaterials.filter((m) => !HSS_IDS.includes(m.id)) : OPT.toolMaterials)
        .map((m) => (ix && m.id === 'CARBIDE' ? Object.assign({}, m, { label: 'Carbide insert' }) : m));
      const tool = fields(
        sel('mill', 'toolType', 'Tool type', OPT.millToolTypes, { rerender: true, cls: 'span2' }),
        `<p class="note full typedesc">${esc(t(T.description))}</p>`,
        sel('mill', 'toolMaterial', ix ? 'Insert grade' : 'Substrate', substrates),
        ix ? '' : sel('mill', 'coating', 'Coating', OPT.coatings),
        num('mill', 'diameter', 'Diameter', 'LENGTH', { min: 0.1 }),
        num('mill', 'flutes', ix ? 'Inserts' : 'Flutes', 'NONE', { int: true, min: 1, extra: 0 }),
        T.insertSizeLabel ? num('mill', 'insertSize', T.insertSizeLabel, 'LENGTH', { auto: 'insertSizeAuto', min: 1 }) : '',
        T.hasLeadInput ? num('mill', 'leadAngleDeg', 'Lead angle κr', 'ANGLE', { auto: 'leadAuto', min: 8 }) : '',
        i.toolType === 'CORNER_RADIUS' ? num('mill', 'cornerRadius', 'Corner radius', 'LENGTH', { min: 0 }) : '',
        i.toolType === 'CHAMFER' ? num('mill', 'chamferAngle', 'Included angle', 'ANGLE', { min: 10 }) : '',
        i.toolType === 'CHAMFER' ? num('mill', 'chamferTipDiameter', 'Tip diameter', 'LENGTH', { min: 0 }) : '',
        ix ? '' : num('mill', 'fluteLength', 'Flute length', 'LENGTH', { auto: 'fluteLengthAuto', min: 0.1 }),
        arbor ? '' : num('mill', 'stickout', 'Stick-out', 'LENGTH', { auto: 'stickoutAuto', min: 0.1 }),
        ix ? '' : num('mill', 'helixDeg', 'Helix', 'ANGLE'),
        num('mill', 'rakeDeg', 'Radial rake', 'ANGLE', { auto: 'rakeAuto' }),
        arbor ? '' : num('mill', 'shankDiameter', 'Shank Ø', 'LENGTH', { auto: 'diameter', min: 0.1 }),
        sel('mill', 'condition', 'Edge condition', OPT.toolConditions),
      );
      const cut = fields(
        sel('mill', 'strategy', 'Strategy', OPT.millStrategies, { rerender: true, cls: 'span2' }),
        num('mill', 'ap', 'Axial depth ap', 'LENGTH', { auto: 'apAuto', min: 0.001 }),
        num('mill', 'ae', 'Radial width ae', 'LENGTH', { auto: 'aeAuto', min: 0.001 }),
        toggle('mill', 'climb', 'Climb milling', i.climb ? 'Down-milling (recommended on CNC)' : 'Conventional (up-milling)'),
        sel('S', 'level', 'Chip-load level', OPT.chipLoadLevels, { cls: 'full' }),
        slider('mill', 'speedPercent', 'Speed override'),
        slider('mill', 'feedPercent', 'Chip-load override'),
      ) + note(esc(t('Clear a field to return to the recommended value. Machine: {0}', t(S.mill.name))));
      return materialButton() + card(esc(t('Tool')), tool) + card(esc(t('Cut')), cut);
    },
    kpis: (r) => [
      kpi('Spindle', r.rpm, 'SPINDLE_SPEED'),
      kpi('Feed', r.feed, 'FEED_RATE', { c: t('{0} flutes', IN.mill.flutes) }),
      kpi('Cutting speed', r.vc, 'CUTTING_SPEED'),
      kpi('Chip load', r.fz, 'CHIP_LOAD', { c: t('hmax {0}', fmtU(r.hmax, 'CHIP_LOAD')) }),
      secondary(kpi('Material removal', r.mrr, 'MRR')),
      powerKpi(r.powerKw, r.availablePowerKw),
      lifeKpi(r.toolLife, t('≈ {0} of cut', sigU(r.toolLifePath, 'DISTANCE'))),
      secondary(!r.hasDeflection ? kpi('Peak lateral force', r.peakFxy, 'FORCE')
        : kpi('Tool deflection', r.deflection * 1000, 'DEFLECTION', {
          status: r.deflection > 4 * r.deflectionLimit ? 'critical' : r.deflection > r.deflectionLimit ? 'warning' : '',
          c: t('guideline {0}', fmtU(r.deflectionLimit * 1000, 'DEFLECTION')),
        })),
    ],
    results(r) {
      const i = IN.mill;
      return card(esc(t('Speeds & feeds')), rows([
        [t('Cutting speed'), fmtU(r.vc, 'CUTTING_SPEED'), 'em'],
        [t('Chip load (programmed fz)'), fmtU(r.fz, 'CHIP_LOAD'), 'em'],
        [t('Max chip thickness hmax'), fmtU(r.hmax, 'CHIP_LOAD')],
        [t('Chip-thinning factor'), fmtN(r.chipThinningFactor, 2) + '×'],
        [t('Effective diameter'), fmtU(r.effectiveDiameter, 'LENGTH')],
        [t('Engagement angle'), fmtN(r.engagementAngleDeg, 0) + '°'],
        [t('Depths used: ap'), fmtU(r.ap, 'LENGTH')],
        ['ae', fmtU(r.ae, 'LENGTH')],
        MILL_TYPE[i.toolType].indexable ? [t('Lead angle κr'), fmtN(r.leadAngleDeg, 1) + '°'] : null,
        r.apMaxInsert != null ? [t('Max ap for these inserts'), fmtU(r.apMaxInsert, 'LENGTH')] : null,
      ])) + card(esc(t('Forces & power')), rows([
        [t('Material removal rate'), fmtU(r.mrr, 'MRR'), 'em'],
        [t('Cutting power'), fmtU(r.powerKw, 'POWER'), 'em ' + warnClass(r.powerKw / r.availablePowerKw)],
        [t('Available at this rpm'), fmtU(r.availablePowerKw, 'POWER')],
        [t('Spindle torque (mean)'), fmtU(r.torqueNm, 'TORQUE')],
        [t('Spindle torque (peak)'), fmtU(r.peakTorque, 'TORQUE')],
        '-',
        [t('Peak lateral force |Fxy|'), fmtU(r.peakFxy, 'FORCE'), 'em'],
        [t('Mean lateral force'), fmtU(r.meanFxy, 'FORCE')],
        [t('Mean feed force Fx'), fmtU(r.meanFx, 'FORCE')],
        [t('Mean normal force Fy'), fmtU(r.meanFy, 'FORCE')],
        [t('Mean axial force Fz (pull-up)'), fmtU(r.meanFz, 'FORCE')],
        [t('Mean specific cutting force kc'), fmtU(r.meanKc, 'STRESS')],
        [t('Teeth in cut (avg)'), fmtN(r.teethInCut, 2)],
      ])) + lifeCard(r.toolLife, [
        [t('Feed path within the tool life'), sigU(r.toolLifePath, 'DISTANCE')],
        [t('Metal removed'), sigU(r.toolLifeVolume, 'VOLUME')],
      ]) + (!r.hasDeflection ? '' : card(esc(t('Deflection & stress')), rows([
        [t('Tool deflection'), fmtU(r.deflection * 1000, 'DEFLECTION'), 'em ' + (r.deflection > 4 * r.deflectionLimit ? 'danger' : r.deflection > r.deflectionLimit ? 'warn' : '')],
        [t('Guideline for this strategy'), fmtU(r.deflectionLimit * 1000, 'DEFLECTION')],
        [t('Bending stress'), fmtU(r.bendingStress, 'STRESS'), warnClass(r.bendingStress / r.allowableStress)],
        [t('Allowable bending stress'), fmtU(r.allowableStress, 'STRESS')],
        [t('Torsional stress'), fmtU(r.torsionStress, 'STRESS')],
      ])))
        + card(esc(t('Force over one revolution')), forceChart(r.samples)
          + note(esc(t('Mechanistic model: Kienzle kc with oblique-cutting force ratios, integrated over teeth, helix and rotation. Rigid tool, no runout.'))), 'wide')
        + (i.toolType === 'DISC' ? '' : card(esc(t('Entry moves & finish')), rows([
          [t('Max ramp angle'), fmtN(r.rampAngleDeg, 1) + '°'],
          [t('Ramp / helix feed'), fmtU(r.rampFeed, 'FEED_RATE')],
          r.arborMounted ? null : [t('Plunge feed (centre-cutting)'), fmtU(r.plungeFeed, 'FEED_RATE')],
          r.arborMounted ? null : [t('Helical bore Ø'), fmt(i.diameter * 1.2, 'LENGTH') + ' – ' + fmtU(i.diameter * 1.8, 'LENGTH')],
          r.cuspHeight != null ? [t(i.toolType === 'BALL_END' ? 'Scallop height' : 'Theoretical cusp height'), fmtU(r.cuspHeight * 1000, 'DEFLECTION')] : null,
        ]))) + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
    },
  },

  drill: {
    title: 'Drilling',
    compute: () => E.drilling(common('drill', S.mill)),
    inputs() {
      const i = IN.drill;
      const reamer = i.drillType === 'REAMER';
      const drill = fields(
        sel('drill', 'drillType', 'Tool', OPT.drillTypes, { rerender: true, cls: 'span2' }),
        num('drill', 'diameter', 'Diameter', 'LENGTH', { min: 0.1 }),
        num('drill', 'depth', 'Hole depth', 'LENGTH', { min: 0 }),
        i.drillType === 'INDEXABLE' ? '' : sel('drill', 'coating', 'Coating', OPT.coatings),
        sel('drill', 'condition', 'Edge condition', OPT.toolConditions),
        reamer ? num('drill', 'preHoleDiameter', 'Pre-drilled Ø', 'LENGTH', { auto: 'preHoleAuto', min: 0 })
          : num('drill', 'preHoleDiameter', 'Pre-drilled / core Ø (0 = solid)', 'LENGTH', { min: 0, cls: 'span2' }),
        reamer ? num('drill', 'flutes', 'Flutes', 'NONE', { int: true, min: 2, extra: 0 }) : '',
        slider('drill', 'speedPercent', 'Speed override'),
        slider('drill', 'feedPercent', 'Feed override'),
      ) + `<p class="note" id="nearestDrill"></p>`;
      const spot = fields(
        num('drill', 'spotDia', 'Chamfer Ø', 'LENGTH', { min: 0 }),
        num('drill', 'spotAngle', 'Included angle', 'ANGLE', { min: 20 }),
        num('drill', 'spotTip', 'Tip / pilot Ø (0 = sharp)', 'LENGTH', { min: 0, cls: 'span2' }),
      ) + `<div class="rows" style="margin-top:10px"><div class="l">${esc(t('Plunge depth (Z)'))}</div><div class="r em" id="spotOut"></div></div>`
        + note(esc(t('Spot ≈0.1–0.2 mm larger than the drill so the drill lips, not the chisel edge, start the hole. Use spot angle ≥ drill point angle.')));
      return materialButton() + card(esc(t('Drill')), drill) + card(esc(t('Spot / countersink depth')), spot);
    },
    kpis: (r) => [
      kpi('Spindle', r.rpm, 'SPINDLE_SPEED'),
      kpi('Feed', r.feed, 'FEED_RATE'),
      kpi('Cutting speed', r.vc, 'CUTTING_SPEED'),
      kpi('Feed/rev', r.fn, 'FEED_PER_REV'),
      secondary(kpi('Thrust', r.thrust, 'FORCE')),
      secondary(kpi('Torque', r.torqueNm, 'TORQUE')),
      powerKpi(r.powerKw, r.availablePowerKw),
      lifeKpi(r.toolLife, r.holesPerTool != null ? t('≈ {0} holes', sig(r.holesPerTool)) : ''),
    ],
    results(r) {
      return card(esc(t('Speeds & feeds')), rows([
        [t('Cutting speed'), fmtU(r.vc, 'CUTTING_SPEED'), 'em'],
        [t('Feed per revolution'), fmtU(r.fn, 'FEED_PER_REV'), 'em'],
        [t('Chip thickness per lip'), fmtU(r.chipThickness, 'CHIP_LOAD')],
      ]) + `<div class="peck"><b>${esc(t('Pecking'))}</b><br>${esc(r.peckAdvice)}</div>`)
        + card(esc(t('Forces & power')), rows([
          [t('Torque'), fmtU(r.torqueNm, 'TORQUE'), 'em'],
          [t('Thrust force'), fmtU(r.thrust, 'FORCE'), 'em'],
          [t('Cutting power'), fmtU(r.powerKw, 'POWER'), warnClass(r.powerKw / r.availablePowerKw)],
          [t('Available at this rpm'), fmtU(r.availablePowerKw, 'POWER')],
          [t('Specific cutting force kc'), fmtU(r.kc, 'STRESS')],
          [t('Material removal rate'), fmtU(r.mrr, 'MRR')],
          '-',
          [t('Point length'), fmtU(r.pointLength, 'LENGTH')],
          [t('Cycle time (one hole)'), fmtN(r.timeMin * 60, 1) + ' s'],
        ]) + note(esc(t('Mc = kc·fn·(D² − d0²)/8000; thrust = lip thrust × (1 + chisel-edge factor). kc from the Kienzle law at h = fn/2·sin κr.'))))
        + lifeCard(r.toolLife, r.holesPerTool == null ? [] : [
          [t('Holes per drill'), sig(r.holesPerTool)],
          [t('Length drilled'), sigU(r.toolLifeLength, 'DISTANCE')],
        ])
        + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
    },
    after(r) {
      const i = IN.drill;
      const n = r.nearest;
      const el = document.getElementById('nearestDrill');
      if (el) el.textContent = t('Nearest standard drill: {0} ({1} mm / {2} in) · depth {3}×D', n.label, fmtN(n.mm, 3), fmtN(n.inch, 4), fmtN(i.depth / i.diameter, 1));
      const so = document.getElementById('spotOut');
      if (so) so.textContent = fmtU(E.spotDepth(i.spotDia, i.spotAngle, i.spotTip), 'LENGTH');
    },
  },

  tap: {
    title: 'Tapping',
    compute: () => E.tapping(common('tap', S.mill)),
    inputs() {
      const i = IN.tap;
      const forming = i.tapType === 'FORM';
      const standards = OPT.threadStandards.filter((s) => s.id !== 'NPT');
      const sizes = E.threads(i.standard).map((th) => ({ id: th.designation, label: th.designation }));
      const thread = fields(
        sel('tap', 'standard', 'Standard', standards, { rerender: true, cls: 'span2' }),
        sel('tap', 'thread', 'Size', sizes, { raw: true, rerender: true }),
      ) + `<div id="threadData" style="margin-top:10px"></div>`;
      const tap = fields(
        sel('tap', 'tapType', 'Tap type', OPT.tapTypes, { rerender: true, cls: 'span2' }),
        sel('tap', 'chamfer', 'Chamfer', OPT.tapChamfers),
        num('tap', 'flutes', forming ? 'Lobes' : 'Flutes', 'NONE', { int: true, min: 1, extra: 0 }),
        sel('tap', 'tapMaterial', 'Substrate', OPT.tapSubstrates),
        sel('tap', 'coating', 'Coating', OPT.coatings),
        toggle('tap', 'blindHole', 'Blind hole'),
        num('tap', 'threadDepth', 'Thread depth', 'LENGTH', { min: 0 }),
        num('tap', 'targetPercent', 'Target thread', 'PERCENT', { auto: 'recommendedPercent', min: 1 }),
        num('tap', 'drillDiameter', 'Drill used (blank = recommended)', 'LENGTH', { auto: 'recommendedDrillMm', min: 0.1, extra: 2, cls: 'span2' }),
        sel('tap', 'condition', 'Edge condition', OPT.toolConditions),
        slider('tap', 'speedPercent', 'Speed override'),
      );
      const mill = fields(
        num('tap', 'millDia', 'Cutter Ø', 'LENGTH', { auto: 'millDiaAuto', min: 0.1 }),
        num('tap', 'millFlutes', 'Flutes', 'NONE', { int: true, min: 1, extra: 0 }),
      ) + `<div id="threadMillOut" style="margin-top:10px"></div>`;
      return materialButton() + card(esc(t('Thread')), thread) + card(esc(t('Tap')), tap) + card(esc(t('Thread milling (internal)')), mill);
    },
    kpis: (r) => [
      kpi('Spindle', r.rpm, 'SPINDLE_SPEED'),
      kpi('Feed', r.feed, 'FEED_RATE'),
      secondary(kpi('Cutting speed', r.vc, 'CUTTING_SPEED')),
      kpi('Tap drill', r.drill, 'LENGTH', { c: r.recommendedDrill.label }),
      { k: t('Thread'), v: fmtN(r.percentThread, 0), u: '%', c: t('target {0}%', fmtN(r.targetPercent, 0)) },
      kpi('Torque', r.torqueNm, 'TORQUE', { status: tapStatus(r.safetyFactor), c: t('breaks at ≈{0}', fmtU(r.breakingTorqueNm, 'TORQUE')) }),
      secondary({ k: t('Safety factor'), v: fmtN(r.safetyFactor, 1), u: '', status: tapStatus(r.safetyFactor), c: t('torque vs. tap strength') }),
      lifeKpi(r.toolLife, r.holesPerTap != null ? t('≈ {0} holes', sig(r.holesPerTap)) : ''),
    ],
    results(r) {
      const th = r.thread;
      return card(esc(t('Tap drill')), rows([
        [t('Recommended drill'), `${r.recommendedDrill.label} (${fmtU(r.recommendedDrill.mm, 'LENGTH')})`, 'em'],
        [t('Theoretical Ø for {0}%', fmtN(r.targetPercent, 0)), fmtU(r.idealDrill, 'LENGTH')],
        [t('Thread engagement with drill used'), fmtN(r.percentThread, 0) + '%', 'em'],
        [t(th.isPipe ? 'Chart drill' : 'Chart drill (≈75%, cut tap)'), `${th.tapDrillLabel} (${fmtU(th.tapDrillMm, 'LENGTH')})`],
        [t('Min. drill depth (blind)'), fmtU(r.minDrillDepth, 'LENGTH')],
      ])) + card(esc(t('Speeds, torque & safety')), rows([
        [t('Cutting speed'), fmtU(r.vc, 'CUTTING_SPEED')],
        '-',
        [t('Tapping torque (est.)'), fmtU(r.torqueNm, 'TORQUE'), 'em ' + ({ critical: 'danger', warning: 'warn' }[tapStatus(r.safetyFactor)] || '')],
        [t('Tap breaking torque (est.)'), fmtU(r.breakingTorqueNm, 'TORQUE')],
        [t('Safety factor'), fmtN(r.safetyFactor, 1)],
        [t('Power'), fmtU(r.powerKw, 'POWER')],
        [t('Chip thickness per tooth'), fmtU(r.chipThickness, 'CHIP_LOAD')],
      ]) + note(esc(t('Torque M = kc·A·dm/2 (energy balance over the thread groove, Kienzle kc) × process factors for land friction and chip packing (Cao & Sutherland 2002). Forming ≈ 2× cutting torque.'))))
        + lifeCard(r.toolLife, r.holesPerTap == null ? [] : [[t('Holes per tap'), sig(r.holesPerTap)]])
        + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
    },
    after(r) {
      const th = r.thread;
      const td = document.getElementById('threadData');
      if (td) {
        const pitch = th.isInch ? `${fmtN(th.tpi, th.tpi % 1 === 0 ? 0 : 1)} TPI` : `P ${fmtN(th.pitch, 2)} mm`;
        td.innerHTML = rows([
          [t('Major diameter'), fmtU(th.major, 'LENGTH')],
          [t('Pitch'), pitch],
          [t('Pitch diameter (basic)'), fmtU(th.pitchDiameter, 'LENGTH')],
          [t(th.isInch ? 'Minor Ø (2B min–max)' : 'Minor Ø (6H min–max)'), fmt(th.minorMin, 'LENGTH') + ' – ' + (th.minorMax != null ? fmtU(th.minorMax, 'LENGTH') : '—')],
        ]);
      }
      const i = IN.tap;
      const dia = i.millDia ?? Math.max(1, th.major * 0.7);
      const tm = E.threadMill(Object.assign(common('tap', S.mill), { toolDiameter: dia, flutes: i.millFlutes }));
      const out = document.getElementById('threadMillOut');
      if (out) {
        out.innerHTML = rows([
          [t('Spindle'), fmtU(tm.rpm, 'SPINDLE_SPEED'), 'em'],
          [t('Feed at cutting edge'), fmtU(tm.peripheralFeed, 'FEED_RATE')],
          [t('Programmed (tool-centre) feed'), fmtU(tm.centreFeed, 'FEED_RATE'), 'em'],
          [t('Radial passes'), String(tm.radialPasses)],
        ]) + note(esc(dia >= th.minorMin
          ? t('Cutter must be smaller than the minor diameter ({0}).', fmtU(th.minorMin, 'LENGTH'))
          : t('Centre feed = edge feed × (D − d)/D for internal threads. Use a 3D helix: one pitch of Z per 360°.')));
      }
      r.millDiaAuto = Math.max(1, th.major * 0.7);
      r.recommendedDrillMm = r.recommendedDrill.mm;
    },
  },

  turn: {
    title: 'Turning',
    compute: () => E.turning(common('turn', S.lathe)),
    inputs() {
      const insert = fields(
        sel('turn', 'grade', 'Insert grade', OPT.insertGrades),
        sel('turn', 'operation', 'Operation', OPT.turnOperations, { rerender: true }),
        sel('turn', 'noseRadius', 'Nose radius rε', [0.2, 0.4, 0.8, 1.2, 1.6, 2.4].map((r) => ({ id: r, label: fmtU(r, 'LENGTH', imperial() ? 4 : 1) })), { raw: true, numeric: true }),
        num('turn', 'enteringAngle', 'Entering angle κr', 'ANGLE', { min: 10 }),
        num('turn', 'rakeDeg', 'Rake angle', 'ANGLE'),
        sel('turn', 'condition', 'Edge condition', OPT.toolConditions),
      );
      const cut = fields(
        num('turn', 'diameter', 'Work diameter', 'LENGTH', { min: 0.5 }),
        num('turn', 'lengthOfCut', 'Length of cut', 'LENGTH', { min: 0 }),
        num('turn', 'ap', 'Depth of cut ap', 'LENGTH', { auto: 'apAuto', min: 0.01 }),
        num('turn', 'feed', 'Feed f', 'FEED_PER_REV', { auto: 'feedAuto', min: 0.005 }),
        slider('turn', 'speedPercent', 'Speed override'),
      ) + note(esc(t('Lathe: {0}. Depth of cut is radial (diameter changes by 2·ap).', t(S.lathe.name))));
      const ra = fields(
        num('turn', 'targetRa', 'Target Ra', 'ROUGHNESS', { min: 0.01 }),
        num('turn', 'raNose', 'Nose radius', 'LENGTH', { min: 0.01 }),
      ) + `<div class="rows" style="margin-top:10px"><div class="l">${esc(t('Max feed'))}</div><div class="r em" id="raOut"></div></div>`;
      return materialButton() + card(esc(t('Insert & operation')), insert) + card(esc(t('Cut')), cut) + card(esc(t('Feed for a target finish')), ra);
    },
    kpis: (r) => [
      kpi('Spindle', r.rpm, 'SPINDLE_SPEED'),
      kpi('Feed', r.feedRate, 'FEED_RATE'),
      kpi('Cutting speed', r.vc, 'CUTTING_SPEED'),
      kpi('Feed/rev', r.feed, 'FEED_PER_REV', { c: t('ap {0}', fmtU(r.ap, 'LENGTH')) }),
      secondary(kpi('Cutting force Fc', r.fc, 'FORCE')),
      powerKpi(r.powerKw, r.availablePowerKw),
      secondary(kpi('Theoretical Ra', r.ra, 'ROUGHNESS', { c: t('Rt {0}', fmtU(r.rt, 'ROUGHNESS')) })),
      lifeKpi(r.toolLife, r.passesPerEdge != null ? t('per edge · ≈ {0} passes', sig(r.passesPerEdge)) : t('per edge')),
    ],
    results(r) {
      return card(esc(t('Speeds & feeds')), rows([
        [t('Cutting speed'), fmtU(r.vc, 'CUTTING_SPEED'), 'em'],
        [t('Feed per revolution'), fmtU(r.feed, 'FEED_PER_REV')],
        [t('Depth of cut'), fmtU(r.ap, 'LENGTH')],
        [t('Cycle time'), fmtN(r.timeMin, 2) + ' ' + t('min')],
      ])) + card(esc(t('Forces, power & finish')), rows([
        [t('Cutting force Fc'), fmtU(r.fc, 'FORCE'), 'em'],
        [t('Feed force Ff'), fmtU(r.ff, 'FORCE')],
        [t('Passive (radial) force Fp'), fmtU(r.fp, 'FORCE')],
        [t('Specific cutting force kc'), fmtU(r.kc, 'STRESS')],
        [t('Cutting power'), fmtU(r.powerKw, 'POWER'), 'em ' + warnClass(r.powerKw / r.availablePowerKw)],
        [t('Available at this rpm'), fmtU(r.availablePowerKw, 'POWER')],
        [t('Spindle torque'), fmtU(r.torqueNm, 'TORQUE')],
        [t('Material removal rate'), fmtU(r.mrr, 'MRR')],
        '-',
        [t('Theoretical Ra'), fmtU(r.ra, 'ROUGHNESS'), 'em'],
        [t('Theoretical Rt (peak-to-valley)'), fmtU(r.rt, 'ROUGHNESS')],
      ]) + note(esc(t('Fc = kc1.1·b·h^(1−mc), h = f·sin κr, b = ap/sin κr. Ra ≈ f²/(32·rε); real finish is usually 1.5–3× worse.'))))
        + lifeCard(r.toolLife, r.passesPerEdge == null ? [] : [[t('Passes per edge ({0} each)', fmtU(IN.turn.lengthOfCut, 'LENGTH')), sig(r.passesPerEdge)]])
        + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
    },
    after() {
      const el = document.getElementById('raOut');
      if (el) el.textContent = fmtU(E.feedForRa(IN.turn.targetRa, IN.turn.raNose), 'FEED_PER_REV');
    },
  },
};

let lastResult = null;
let renderedView = '';

/** Re-rendering a view keeps the scroll position of its panes (and of the page on phones). */
function keepScroll(viewKey, renderFn) {
  const same = renderedView === viewKey;
  const panes = same ? [...document.querySelectorAll('#view .pane')].map((p) => p.scrollTop) : [];
  const pageY = same ? window.scrollY : 0;
  renderFn();
  renderedView = viewKey;
  if (!same) return;
  document.querySelectorAll('#view .pane').forEach((p, i) => { p.scrollTop = panes[i] || 0; });
  window.scrollTo(0, pageY);
}

function renderCalc() {
  const c = CALCS[S.tab];
  keepScroll('calc:' + S.tab, () => {
    document.getElementById('view').innerHTML = `<div class="shell">
      <aside class="pane inputs-pane" aria-label="${esc(t('Inputs'))}">${c.inputs()}</aside>
      <section class="pane right" aria-live="polite"><div class="kpis" id="kpis"></div><div class="alerts advice" id="alerts"></div>
        <div class="rgrid" id="results"></div>${footer()}</section></div>`;
    recompute();
  });
}

function recompute() {
  const c = CALCS[S.tab];
  let r;
  try {
    r = c.compute();
  } catch (e) {
    console.error(e);
    return;
  }
  lastResult = r;
  if (c.after) c.after(r);
  document.getElementById('kpis').innerHTML = kpiHtml(c.kpis(r));
  // Cautions and dangers sit right under the key numbers so they are never below the fold; notes go last.
  document.getElementById('alerts').innerHTML = r.advice.filter((a) => a.level !== 'INFO').map((a) => `<div class="adv ${a.level}">${esc(a.text)}</div>`).join('');
  document.getElementById('results').innerHTML = c.results(r);
  bindChart();
  for (const el of document.querySelectorAll('input[data-auto]')) {
    const [calc, key] = el.dataset.num.split('.');
    const autoKey = el.dataset.auto;
    const auto = autoKey === 'diameter' ? IN[calc].diameter : r[autoKey];
    el.placeholder = auto == null ? '' : t('auto') + ' ' + fmtEdit(auto, el.dataset.q, 0);
    if (IN[calc][key] == null && document.activeElement !== el) el.value = '';
  }
  persist();
}

// ------------------------------------------------------------------------------ reference pages

const REF_PAGES = [
  ['TAP_DRILLS', 'Tap drill charts', 'Metric, UNC/UNF/UNEF, NPT and BSP — cut & form tap drills, minor Ø limits'],
  ['THREAD_PERCENT', 'Thread engagement', 'Percent thread for any drill, drill for any %'],
  ['DRILL_SIZES', 'Drill sizes', 'Number, letter, fractional and metric drills'],
  ['CLEARANCE', 'Clearance holes', 'ISO 273 metric and inch close/free fits'],
  ['COUNTERBORES', 'Counterbores & countersinks', 'Socket head and flat head screws, countersink depth'],
  ['FITS', 'Fits & tolerances', 'ISO 286 hole/shaft limits and fit calculator'],
  ['HARDNESS', 'Hardness conversion', 'HRC ↔ HV ↔ HB ↔ approx. tensile strength'],
  ['SURFACE', 'Surface finish', 'Ra/RMS, N-grades and process capability'],
  ['CALCULATORS', 'Quick calculators', 'rpm ↔ surface speed, feed ↔ chip load, unit conversions'],
  ['MATERIALS', 'Material data', 'Kienzle constants and base speeds for every material'],
  ['FORMULAS', 'Formulas & references', 'Equations used by the calculators with academic citations'],
];

const R_DEF = {
  std: 'METRIC_COARSE', pctStd: 'UNC', pctThread: '1/4-20 UNC', pctDrill: null, series: '', near: null, screws: 'METRIC',
  fitSize: 25, hole: 'H', hg: 7, shaft: 'g', sg: 6, hrc: 45, ra: 1.6, vc: 100, dia: 10, rpm: 3000, fz: 0.05, z: 4, feed: 600,
  conv: 'LENGTH', base: 25.4, cskDia: 12, cskAngle: 90, cskTip: 0,
};
const R = Object.assign({}, R_DEF, store.get('ref.state', {}));

/**
 * Round inch equivalents of the metric defaults, in groups that switch together. A value is only
 * swapped while it still equals the other system's default, so nothing the user typed is touched.
 */
const INCH_DEFAULTS = [
  ['mill', { diameter: 12.7 }], ['mill', { cornerRadius: 0.762 }],
  ['drill', { diameter: 9.525 }], ['drill', { depth: 28.575 }], ['drill', { spotDia: 6.35 }],
  ['tap', { standard: 'UNC', thread: '1/4-20 UNC' }], ['tap', { threadDepth: 12.7 }],
  ['turn', { diameter: 50.8 }], ['turn', { lengthOfCut: 101.6 }], ['turn', { targetRa: 63 / 39.37007874 }],
  ['ref', { std: 'UNC' }], ['ref', { screws: 'INCH' }], ['ref', { fitSize: 25.4 }], ['ref', { vc: 91.44 }], ['ref', { dia: 12.7 }],
  ['ref', { fz: 0.0508 }], ['ref', { feed: 609.6 }], ['ref', { cskDia: 12.7 }],
];
function swapUnitDefaults(toImperial) {
  const same = (a, b) => (typeof a === 'number' && typeof b === 'number' ? Math.abs(a - b) < 1e-9 : a === b);
  for (const [calc, inch] of INCH_DEFAULTS) {
    const target = calc === 'ref' ? R : IN[calc];
    const metric = calc === 'ref' ? R_DEF : DEF[calc];
    const keys = Object.keys(inch);
    const from = (k) => (toImperial ? metric[k] : inch[k]);
    if (keys.every((k) => same(target[k], from(k)))) for (const k of keys) target[k] = toImperial ? inch[k] : metric[k];
  }
}
function setUnits(units) {
  if (units === S.units) return;
  S.units = units;
  swapUnitDefaults(units === 'IMPERIAL');
  store.set('ref.state', R);
}
if (S.units === 'IMPERIAL') swapUnitDefaults(true);

const len = (mm) => fmt(mm, 'LENGTH');
const drillSub = (label, mm) => (!imperial() && / mm$/.test(label) ? fmtN(mm / 25.4, 4) + ' in' : len(mm));
const chips = (name, items, cur) => `<div class="chips">${items.map(([id, label]) => `<button class="chip ${String(id) === String(cur) ? 'on' : ''}" data-chip="${name}" data-v="${esc(id)}">${esc(label)}</button>`).join('')}</div>`;
const table = (head, body, opts = {}) => `<div class="tablewrap ${opts.tall ? 'tall' : ''}"><table><thead><tr>${head.map((h, i) => `<th class="${opts.num && i > 0 ? 'n' : ''}">${h}</th>`).join('')}</tr></thead><tbody>${body}</tbody></table></div>`;
const rnum = (key, label, q, o = {}) => {
  const v = R[key];
  const unit = q === 'NONE' ? (o.unit || '') : uLabel(q);
  return `<label class="field ${o.cls || ''}"><span class="lbl">${esc(t(label))}</span><span class="inp"><input data-rnum="${key}" data-q="${q}" ${o.sys ? `data-sys="${o.sys}"` : ''}
    inputmode="decimal" autocomplete="off" value="${esc(v == null ? '' : (o.sys ? fmtSys(v, q, o.sys) : fmtEdit(v, q, 0)))}" ${o.ph ? `placeholder="${esc(o.ph)}"` : ''}>${unit || o.sys ? `<em>${esc(o.sys ? t(o.sys === 'IMPERIAL' ? Q[q].imperial : Q[q].metric) : unit)}</em>` : ''}</span></label>`;
};
function fmtSys(v, q, sys) {
  const val = sys === 'IMPERIAL' ? v * Q[q].toImperial : v;
  let s = val.toFixed((sys === 'IMPERIAL' ? Q[q].imperialDecimals : Q[q].metricDecimals) + 2);
  if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
  return s;
}

const REF = {
  TAP_DRILLS() {
    const stds = OPT.threadStandards.map((s) => [s.id, t(s.label)]);
    const list = E.threads(R.std);
    const std = OPT.threadStandards.find((s) => s.id === R.std);
    const u = uLabel('LENGTH');
    let html;
    if (std.isPipe) {
      html = table([t('Size'), t('Major Ø') + ` (${u})`, t('Tap drill'), t(R.std === 'NPT' ? 'With reamer' : 'Pitch')],
        list.map((th) => `<tr><td><b>${esc(th.designation)}</b></td><td class="n">${len(th.major)}</td><td class="n">${esc(th.tapDrillLabel)}<small>${drillSub(th.tapDrillLabel, th.tapDrillMm)}</small></td>
          <td class="n">${esc(th.reamedDrillLabel || `${fmtN(th.tpi, 0)} TPI`)}</td></tr>`).join(''), { num: true });
    } else {
      html = table([t('Thread'), t('Pitch'), t('Cut tap (≈75%)'), t('Form tap (65%)'), t('Minor Ø') + ` (${u})`],
        list.map((th) => `<tr><td><b>${esc(th.designation)}</b></td><td class="n">${th.isInch ? fmtN(th.tpi, th.tpi % 1 ? 1 : 0) + ' TPI' : fmtN(th.pitch, 2) + ' mm'}</td>
          <td class="n">${esc(th.tapDrillLabel)}<small>${drillSub(th.tapDrillLabel, th.tapDrillMm)}</small></td><td class="n">${esc(th.formDrill.label)}<small>${drillSub(th.formDrill.label, th.formDrill.mm)}</small></td>
          <td class="n">${len(th.minorMin)} – ${th.minorMax != null ? len(th.minorMax) : '—'}</td></tr>`).join(''), { num: true });
    }
    return chips('std', stds, R.std) + html + note(esc(t('Cut-tap drills are the common chart sizes (≈75% thread). Form-tap drills use d = D − 0.0068·%·P. Minor Ø shows the internal-thread limits (6H metric, 2B unified).')));
  },
  THREAD_PERCENT() {
    const stds = OPT.threadStandards.filter((s) => !s.isPipe);
    const list = E.threads(R.pctStd);
    let th = list.find((x) => x.designation === R.pctThread);
    if (!th) { th = list[0]; R.pctThread = th.designation; R.pctDrill = null; }
    const drill = R.pctDrill ?? th.tapDrillMm;
    const p = E.threadPercent(th.major, th.pitch, drill);
    const near = E.nearestDrill(drill, 'all');
    const set = th.isInch ? 'inch' : 'metric';
    const rowsHtml = [50, 55, 60, 65, 70, 75, 80, 85].map((pct) => [pct, false]).concat([55, 60, 65, 70].map((pct) => [pct, true])).map(([pct, form]) => {
      const exact = E.drillForPercent(th.major, th.pitch, pct, form);
      const d = E.nearestDrill(exact, set);
      const actual = E.threadPercent(th.major, th.pitch, d.mm)[form ? 'form' : 'cut'];
      return `<tr><td>${esc(t(form ? '{0}% form' : '{0}% cut', pct))}</td><td class="n">${len(exact)}</td><td class="n">${esc(d.label)}<small>${len(d.mm)}</small></td><td class="n">${fmtN(actual, 0)}%</td></tr>`;
    }).join('');
    return `<div class="grid2">${card(esc(t('Thread & drill')), fields(
      `<label class="field span2"><span class="lbl">${esc(t('Standard'))}</span><select data-rsel="pctStd">${stds.map((s) => `<option value="${s.id}" ${s.id === R.pctStd ? 'selected' : ''}>${esc(t(s.label))}</option>`).join('')}</select></label>`,
      `<label class="field"><span class="lbl">${esc(t('Size'))}</span><select data-rsel="pctThread">${list.map((x) => `<option ${x.designation === th.designation ? 'selected' : ''}>${esc(x.designation)}</option>`).join('')}</select></label>`,
      rnum('pctDrill', 'Drill Ø', 'LENGTH', { ph: fmtEdit(th.tapDrillMm, 'LENGTH', 0) }),
    ) + note(esc(t('Nearest standard drill: {0} ({1})', near.label, fmtU(near.mm, 'LENGTH')))) + rows([
      [t('Cut tap engagement'), fmtN(p.cut, 1) + '%', 'em'],
      [t('Form tap engagement'), fmtN(p.form, 1) + '%', 'em'],
    ]))}${card(esc(t('Drill for a target engagement')), table(['%', t('Exact Ø'), t('Nearest drill'), t('Actual')], rowsHtml, { num: true })
      + note(esc(t('Above ≈75% thread, strength barely increases while tapping torque rises sharply. Use 60–65% in tough materials (stainless, titanium, nickel alloys).'))))}</div>`;
  },
  DRILL_SIZES() {
    const series = [['', t('All')]].concat(OPT.drillSeries.map((s) => [s.id, t(s.label)]));
    const all = E.drills().filter((d) => !R.series || d.series === R.series);
    let nearest = null;
    if (R.near != null && all.length) nearest = all.reduce((a, b) => (Math.abs(b.mm - R.near) < Math.abs(a.mm - R.near) ? b : a));
    const lbl = Object.fromEntries(OPT.drillSeries.map((s) => [s.id, t(s.label)]));
    return chips('series', series, R.series) + `<div class="fields" style="max-width:420px">${rnum('near', 'Find nearest drill to…', 'LENGTH', { cls: 'span2' })}</div>`
      + table([t('Drill'), 'mm', 'inch', t('Series')], all.map((d) => `<tr class="${nearest && d === nearest ? 'hl' : ''}" ${nearest && d === nearest ? 'id="nearRow"' : ''}><td><b>${esc(d.label)}</b></td>
        <td class="n">${fmtN(d.mm, 3)}</td><td class="n">${fmtN(d.inch, 4)}</td><td class="n">${esc(lbl[d.series])}</td></tr>`).join(''), { num: true, tall: true });
  },
  CLEARANCE() {
    const h = E.holes();
    const u = uLabel('LENGTH');
    const tbl = R.screws === 'METRIC'
      ? table([t('Screw'), t('Fine') + ` (${u})`, t('Medium'), t('Coarse')], h.metricClearance.map((c) => `<tr><td><b>${c.size}</b></td><td class="n">${len(c.close)}</td><td class="n">${len(c.normal)}</td><td class="n">${c.loose != null ? len(c.loose) : '—'}</td></tr>`).join(''), { num: true })
        + note(esc(t('ISO 273 series: fine (close fit), medium (normal), coarse (loose).')))
      : table([t('Screw'), t('Close fit') + ` (${u})`, t('Free fit')], h.inchClearance.map((c) => `<tr><td><b>${c.size}</b></td><td class="n">${esc(c.closeLabel)}<small>${len(c.close)}</small></td><td class="n">${esc(c.normalLabel)}<small>${len(c.normal)}</small></td></tr>`).join(''), { num: true });
    return chips('screws', [['METRIC', t('Metric (ISO)')], ['INCH', t('Inch (UNC/UNF)')]], R.screws) + tbl;
  },
  COUNTERBORES() {
    const h = E.holes();
    const metric = R.screws === 'METRIC';
    const cb = metric ? h.metricCounterbores : h.inchCounterbores;
    const cs = metric ? h.metricCountersinks : h.inchCountersinks;
    const u = uLabel('LENGTH');
    return chips('screws', [['METRIC', t('Metric (ISO)')], ['INCH', t('Inch (UNC/UNF)')]], R.screws)
      + `<div class="grid2"><div>${card(esc(t('Socket head cap screws ({0})', u)), table([t('Screw'), t('Head Ø'), t("C'bore Ø"), t('Depth'), t('Thru hole')],
        cb.map((c) => `<tr><td><b>${c.size}</b></td><td class="n">${len(c.head)}</td><td class="n">${len(c.cbore)}</td><td class="n">${len(c.depth)}</td><td class="n">${len(c.clearance)}</td></tr>`).join(''), { num: true }))}</div>
      <div style="display:flex;flex-direction:column;gap:16px">${card(esc(t('Flat (countersunk) head screws ({0})', u)), table([t('Screw'), t('Head Ø'), t('Angle'), t('Thru hole'), t('Head depth†'), t('Spot depth*')],
        cs.map((c) => `<tr><td><b>${c.size}</b></td><td class="n">${len(c.head)}</td><td class="n">${fmtN(c.angle, 0)}°</td><td class="n">${len(c.clearance)}</td><td class="n">${len(c.headDepth)}</td><td class="n">${len(c.spotDepth)}</td></tr>`).join(''), { num: true })
        + note(esc(t('†Countersink depth from the surface to the screw\'s nominal Ø for a flush head (= head height).')))
        + note(esc(t('*Plunge depth of a sharp-pointed countersink/spot drill of the same angle for a head slightly below flush.'))))}
      ${card(esc(t('Countersink calculator')), fields(rnum('cskDia', 'Countersink Ø', 'LENGTH'), rnum('cskAngle', 'Angle', 'ANGLE'), rnum('cskTip', 'Tool tip Ø (0 = sharp)', 'LENGTH', { cls: 'span2' }))
        + rows([[t('Plunge depth'), fmtU(E.countersinkDepth(R.cskDia, R.cskAngle, R.cskTip), 'LENGTH'), 'em']]))}</div></div>`;
  },
  FITS() {
    const info = E.fitsInfo();
    const f = E.fit(R.hole, R.hg, R.shaft, R.sg, R.fitSize);
    const common = info.commonFits.find((c) => c.hole === R.hole && c.holeGrade === R.hg && c.shaft === R.shaft && c.shaftGrade === R.sg);
    const dev = (um) => (um > 0 ? '+' : '') + fmtN(um, um % 1 ? 1 : 0);
    const lim = (tol) => { const d = imperial() ? 5 : 3; return fmt(R.fitSize + tol.upper / 1000, 'LENGTH', d) + ' / ' + fmt(R.fitSize + tol.lower / 1000, 'LENGTH', d) + ' ' + uLabel('LENGTH'); };
    const clr = (um) => (imperial() ? (um > 0 ? '+' : '') + fmtN(um / 25400, 5) + ' in' : dev(um) + ' µm');
    const grades = [5, 6, 7, 8, 9, 10, 11];
    const selL = (key, letters) => `<select data-rsel="${key}">${letters.map((l) => `<option ${l === R[key] ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
    const selG = (key) => `<select data-rsel="${key}" data-numeric="1">${grades.map((g) => `<option value="${g}" ${g === R[key] ? 'selected' : ''}>IT${g}</option>`).join('')}</select>`;
    const type = f ? { CLEARANCE: t('Clearance'), TRANSITION: t('Transition'), INTERFERENCE: t('Interference') }[f.type] : '';
    const it = E.itGrades(R.fitSize);
    return `<div class="grid2">${card(esc(t('Fit')), fields(rnum('fitSize', 'Nominal size', 'LENGTH', { cls: 'span2' }),
      `<label class="field"><span class="lbl">${esc(t('Hole'))}</span>${selL('hole', info.holeLetters)}</label>`, `<label class="field"><span class="lbl">IT</span>${selG('hg')}</label>`,
      `<label class="field"><span class="lbl">${esc(t('Shaft'))}</span>${selL('shaft', info.shaftLetters)}</label>`, `<label class="field"><span class="lbl">IT</span>${selG('sg')}</label>`)
      + `<div class="chips" style="margin-top:12px">${info.commonFits.map((c) => `<button class="chip ${common === c ? 'on' : ''}" data-fit="${c.label}" title="${esc(t(c.name))}">${c.label}</button>`).join('')}</div>`
      + (common ? note(`<b>${esc(t(common.name))}</b>: ${esc(t(common.use))}`) : ''))}
      ${f ? card(esc(t('{0} at {1}')).replace('{0}', `<span class="keep">${R.hole}${R.hg} / ${R.shaft}${R.sg}</span>`).replace('{1}', `<span class="keep">${esc(fmtU(R.fitSize, 'LENGTH'))}</span>`), rows([
        [t('Hole deviations'), `${dev(f.hole.upper)} / ${dev(f.hole.lower)} µm`], [t('Hole limits'), lim(f.hole), 'em'],
        [t('Shaft deviations'), `${dev(f.shaft.upper)} / ${dev(f.shaft.lower)} µm`], [t('Shaft limits'), lim(f.shaft), 'em'], '-',
        [t('Max clearance'), clr(f.maxClearance)], [t('Min clearance'), clr(f.minClearance)], [t('Fit type'), type, 'em'],
      ]) + note(esc(t('Negative clearance = interference.'))))
      : card(esc(t('Fit')), note(esc(t('This combination is not available (sizes up to 500 mm; K/M/N holes up to IT8, P/R/S up to IT7).')))) }</div>`
      + card(esc(t('Standard tolerance grades at this size (µm)')), table(it.map((g) => 'IT' + g.grade), `<tr>${it.map((g) => `<td class="n">${g.value ?? '—'}</td>`).join('')}</tr>`)
        + note(esc(t('ISO 286-1. Typical: IT6–IT7 ground/reamed, IT8–IT9 fine turned/milled, IT11 general machining.'))));
  },
  HARDNESS() {
    const rowsH = E.hardness();
    const hv = E.hvFromHrc(R.hrc);
    const near = rowsH.reduce((a, b) => (Math.abs(b.hrc - R.hrc) < Math.abs(a.hrc - R.hrc) ? b : a));
    return `<div class="grid2">${card(esc(t('Convert from HRC')), fields(rnum('hrc', 'Rockwell C', 'NONE', { unit: 'HRC' })) + rows([
      [t('Vickers HV'), hv != null ? fmtN(hv, 0) : t('out of range'), 'em'],
      [t('Brinell HB (≈)'), near.hb != null ? String(near.hb) : '—'],
      [t('Tensile strength (≈)'), near.tensile != null ? `${near.tensile} MPa / ${fmtN(near.tensile * 0.1450377, 0)} ksi` : '—'],
    ]) + note(esc(t('Approximate conversions for non-austenitic steels (ASTM E140). Tensile ≈ 3.45·HB.'))))}
    ${table(['HRC', 'HV', 'HB', 'Rm MPa (≈)'], rowsH.map((r) => `<tr class="${r === near ? 'hl' : ''}"><td><b>${r.hrc}</b></td><td class="n">${r.hv}</td><td class="n">${r.hb ?? '—'}</td><td class="n">${r.tensile ?? '—'}</td></tr>`).join(''), { num: true, tall: true })}</div>`;
  },
  SURFACE() {
    const s = E.surface();
    const grade = s.grades.reduce((a, b) => (Math.abs(Math.log(b.ra / R.ra)) < Math.abs(Math.log(a.ra / R.ra)) ? b : a));
    const trim = (v) => String(+v.toFixed(3));
    return `<div class="grid2">${card(esc(t('Convert')), fields(rnum('ra', 'Ra', 'ROUGHNESS', { sys: 'METRIC' }), rnum('ra', 'Ra', 'ROUGHNESS', { sys: 'IMPERIAL' })) + rows([
      [t('Rq (RMS) ≈ 1.11·Ra'), fmtU(R.ra * 1.11, 'ROUGHNESS')],
      [t('Rz ≈ 4–7·Ra (typical 5.5)'), fmtU(R.ra * 5.5, 'ROUGHNESS')],
      [t('Nearest ISO grade'), grade.grade, 'em'],
    ]))}${table([t('Grade'), 'Ra µm', 'Ra µin'], s.grades.map((g) => `<tr class="${g === grade ? 'hl' : ''}"><td><b>${g.grade}</b></td><td class="n">${trim(g.ra)}</td><td class="n">${g.uin}</td></tr>`).join(''), { num: true })}</div>`
      + table([t('Process'), 'Ra min µm', 'Ra max µm'], s.processes.map((p) => `<tr><td>${esc(t(p.process))}</td><td class="n">${trim(p.min)}</td><td class="n">${trim(p.max)}</td></tr>`).join(''), { num: true });
  },
  CALCULATORS() {
    const conv = ['LENGTH', 'CUTTING_SPEED', 'FEED_RATE', 'FEED_PER_REV', 'POWER', 'FORCE', 'TORQUE', 'STRESS', 'MRR', 'ROUGHNESS'];
    const rpm = (1000 * R.vc) / (Math.PI * R.dia);
    const vc = (Math.PI * R.dia * R.rpm) / 1000;
    return `<div class="grid2">${card(esc(t('Spindle speed from cutting speed')), fields(rnum('vc', 'Cutting speed', 'CUTTING_SPEED'), rnum('dia', 'Diameter', 'LENGTH'))
      + rows([[t('Spindle speed'), fmtU(rpm, 'SPINDLE_SPEED'), 'em']]))}
      ${card(esc(t('Cutting speed from spindle speed')), fields(rnum('rpm', 'Spindle', 'SPINDLE_SPEED'), rnum('dia', 'Diameter', 'LENGTH')) + rows([[t('Cutting speed'), fmtU(vc, 'CUTTING_SPEED'), 'em']]))}
      ${card(esc(t('Feed ↔ chip load')), fields(rnum('fz', 'Chip load fz', 'CHIP_LOAD'), rnum('z', 'Flutes', 'NONE'), rnum('feed', 'Feed rate', 'FEED_RATE', { cls: 'span2' }))
        + rows([[t('Feed at {0}', fmtU(R.rpm, 'SPINDLE_SPEED')), fmtU(R.fz * R.z * R.rpm, 'FEED_RATE'), 'em'], [t('Chip load at {0}', fmtU(R.rpm, 'SPINDLE_SPEED')), fmtU(R.rpm > 0 ? R.feed / (R.z * R.rpm) : 0, 'CHIP_LOAD'), 'em']]))}
      ${card(esc(t('Unit converter')), fields(`<label class="field span2"><span class="lbl">${esc(t('Quantity'))}</span><select data-rsel="conv">${conv.map((q) => `<option value="${q}" ${q === R.conv ? 'selected' : ''}>${esc(t(Q[q].metric))} ↔ ${esc(Q[q].imperial)}</option>`).join('')}</select></label>`,
        rnum('base', 'Metric', R.conv, { sys: 'METRIC' }), rnum('base', 'Imperial', R.conv, { sys: 'IMPERIAL' })) + note(esc(t('Edit either side. Inch lengths accept fractions such as 1-1/4.'))))}</div>`;
  },
  MATERIALS() {
    const vcU = (v) => (v > 0 ? fmt(v, 'CUTTING_SPEED') : '—');
    const sp = uLabel('CUTTING_SPEED');
    return table(['ISO', t('Material data'), 'kc1.1 N/mm²', 'mc', `${t('Solid carbide')} (${sp})`, `HSS (${sp})`, 'hmax/D', 'fn/D', `${t('Tap')} (${sp})`, `${t('Turning')} (${sp})`],
      MATS.map((m) => `<tr><td>${isoBadge(m.group, true)}</td><td><b>${esc(t(m.name))}</b><small>${esc(m.examples)} · ${esc(t(m.hardness))}</small>${m.notes ? `<small>${esc(t(m.notes))}</small>` : ''}</td>
        <td class="n">${m.kc11}</td><td class="n">${fmtN(m.mc, 2)}</td><td class="n">${vcU(m.vcCarbide)}</td><td class="n">${vcU(m.vcHss)}</td><td class="n">${fmtN(m.fzPerD, 4)}</td>
        <td class="n">${fmtN(m.drillFnPerD, 3)}</td><td class="n">${m.tapVc > 0 ? vcU(m.tapVc) : esc(t('thread mill'))}</td><td class="n">${vcU(m.turnVc)}</td></tr>`).join(''), { num: true, tall: true })
      + note(esc(t('Speeds: uncoated tools, base values before coating/engagement corrections. kc1.1 referenced to 0° rake.')));
  },
  FORMULAS() {
    return `<div class="grid2">${card(esc(t('Formulas')), E.formulas().map((f) => `<div style="padding:6px 0 10px"><b>${esc(t(f.title))}</b>
      <div class="formula">${esc(f.formula)}</div><div>${esc(t(f.notes))}</div><div class="note">${esc(t('Refs:'))} ${esc(f.refs.join(', '))}</div></div>`).join(''))}
      ${card(esc(t('References')), E.citations().map((c) => `<div class="cite"><b>[${esc(c.key)}]</b> ${esc(c.text)}<div class="note">${esc(t('Used for: {0}', t(c.usedFor)))}</div></div>`).join(''))}</div>`;
  },
};

function renderRef() {
  const page = REF_PAGES.find((p) => p[0] === S.ref) || REF_PAGES[0];
  keepScroll('ref', () => {
    document.getElementById('view').innerHTML = `<div class="shell ref">
      <nav class="pane refnav">${REF_PAGES.map(([id, title, desc]) => `<button class="${id === page[0] ? 'on' : ''}" data-ref="${id}">${esc(t(title))}<small>${esc(t(desc))}</small></button>`).join('')}</nav>
      <section class="pane refbody"><h2>${esc(t(page[1]))}</h2><div id="refContent">${REF[page[0]]()}</div>${footer()}</section></div>`;
  });
  store.set('ref.state', R);
}

function refreshRef(focusKey) {
  const active = document.activeElement;
  const key = active && active.dataset ? active.dataset.rnum : null;
  const sys = active && active.dataset ? active.dataset.sys : null;
  const typed = key ? active.value : null;
  const pos = key ? active.selectionStart : null;
  document.getElementById('refContent').innerHTML = REF[S.ref]();
  if (key && focusKey !== false) {
    const el = document.querySelector(`[data-rnum="${key}"]${sys ? `[data-sys="${sys}"]` : ':not([data-sys])'}`);
    if (el) {
      el.value = typed;
      el.focus();
      try { el.setSelectionRange(pos, pos); } catch (e) { /* non-text input */ }
    }
  }
  store.set('ref.state', R);
}

// ------------------------------------------------------------------------------ modals

function openModal(title, bodyHtml, onMount) {
  const root = document.getElementById('modalRoot');
  root.innerHTML = `<div class="modal-back" data-close="1"><div class="modal" role="dialog" aria-modal="true"><header><h2>${esc(title)}</h2>
    <button class="icon-btn" data-close="1" aria-label="${esc(t('Close'))}"><svg viewBox="0 0 24 24"><path d="M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4-5.6-5.6L19 6.4 17.6 5 12 10.6z"/></svg></button></header>
    <div class="body">${bodyHtml}</div></div></div>`;
  if (onMount) onMount(root.querySelector('.modal'));
}
function closeModal() { document.getElementById('modalRoot').innerHTML = ''; }

function openMaterialPicker() {
  let group = '';
  let query = '';
  const list = () => {
    const q = query.trim().toLowerCase();
    return MATS.filter((m) => (!group || m.group === group) && (!q || [m.name, t(m.name), m.examples, m.family, t(m.family)].some((s) => s.toLowerCase().includes(q))))
      .map((m) => `<button class="matrow ${m.id === S.material ? 'on' : ''}" data-mat="${m.id}">${isoBadge(m.group)}<span class="meta"><b>${esc(t(m.name))}</b>
        <div class="ex">${esc(m.examples)} · ${esc(t(m.hardness))}</div></span></button>`).join('');
  };
  const groupChips = () => chips('mgroup', [['', t('All')]].concat(GROUPS.map((g) => [g.id, `${g.label} · ${t(g.description)}`])), group);
  openModal(t('Select material'), `<input class="search" id="matSearch" placeholder="${esc(t('Search: 6061, 304, Inconel, PEEK…'))}" autocomplete="off">
    <div id="matGroups">${groupChips()}</div><div class="matlist" id="matList">${list()}</div>`, (m) => {
    const search = m.querySelector('#matSearch');
    search.focus();
    search.addEventListener('input', () => { query = search.value; m.querySelector('#matList').innerHTML = list(); });
    m.addEventListener('click', (e) => {
      const chip = e.target.closest('[data-chip="mgroup"]');
      if (chip) { group = chip.dataset.v; m.querySelector('#matGroups').innerHTML = groupChips(); m.querySelector('#matList').innerHTML = list(); return; }
      const row = e.target.closest('[data-mat]');
      if (row) { S.material = row.dataset.mat; closeModal(); render(); }
    });
  });
}

function machineEditor(which, title) {
  const m = S[which];
  const f = (key, label, q, o = {}) => {
    const v = o.scale ? m[key] * o.scale : m[key];
    return `<label class="field"><span class="lbl">${esc(t(label))}</span><span class="inp"><input data-mach="${which}.${key}" data-q="${q}" data-scale="${o.scale || 1}" inputmode="decimal" value="${esc(fmtEdit(v, q, 0))}">
      <em>${esc(q === 'NONE' ? (o.unit || '') : uLabel(q))}</em></span></label>`;
  };
  return card(esc(t(title)), fields(
    `<label class="field span2"><span class="lbl">${esc(t('Load preset'))}</span><select data-preset="${which}"><option value="">—</option>${PRESETS.map((p, i) => `<option value="${i}">${esc(t(p.name))}</option>`).join('')}</select></label>`,
    `<label class="field span2"><span class="lbl">${esc(t('Name'))}</span><span class="inp"><input data-machname="${which}" value="${esc(t(m.name))}"></span></label>`,
    f('maxRpm', 'Max rpm', 'SPINDLE_SPEED'), f('minRpm', 'Min rpm', 'SPINDLE_SPEED'), f('powerKw', 'Spindle power', 'POWER'),
    f('maxTorqueNm', 'Max torque (0 = n/a)', 'TORQUE'), f('maxFeed', 'Max feed', 'FEED_RATE'), f('efficiency', 'Efficiency', 'NONE', { scale: 100, unit: '%' }),
    `<label class="field span2"><span class="lbl">${esc(t('Rigidity'))}</span><select data-rig="${which}">${OPT.rigidity.map((r) => `<option value="${r.id}" ${r.id === m.rigidity ? 'selected' : ''}>${esc(t(r.label))}</option>`).join('')}</select></label>`,
  ) + note(esc(t('Rigidity scales recommended depths and chip loads. Max torque enables the constant-torque region below base speed.'))));
}

function openSettings() {
  const body = () => card(esc(t('Language')), chips('lang', [['en', 'English'], ['tr', 'Türkçe']], S.lang))
    + card(esc(t('Units')), chips('units', [['METRIC', t('Metric (mm, m/min, kW, N)')], ['IMPERIAL', t('Inch (in, SFM, hp, lbf)')]], S.units))
    + card(esc(t('Theme')), chips('theme', [['auto', t('System')], ['light', t('Light')], ['dark', t('Dark')]], S.theme))
    + card(esc(t('Milling recommendations')), `<div class="fields">${sel('S', 'level', 'Chip-load level', OPT.chipLoadLevels, { cls: 'full' })}</div>`
      + note(esc(t('Scales the recommended chip load and cutting speed. Conservative ≈ cautious catalogue starting values; Aggressive ≈ high-end catalogue / HEM practice on a rigid machine with a good holder.'))))
    + machineEditor('mill', 'Milling machine') + machineEditor('lathe', 'Lathe')
    + card(esc(t('About')), `<p>${esc(t('Shop Ref — machining reference and feeds & speeds calculator.'))}</p>`
      + note(esc(t("Recommendations are starting points computed from published cutting-mechanics models and typical catalogue data (see Reference → Formulas & references). Always follow your tool manufacturer's data, start conservatively and watch the cut. Forces assume a rigid tool without runout or chatter."))));
  openModal(t('Settings'), body(), (m) => {
    const rerenderBody = () => { m.querySelector('.body').innerHTML = body(); m.querySelector('header h2').textContent = t('Settings'); };
    m.addEventListener('click', (e) => {
      const chip = e.target.closest('[data-chip]');
      if (!chip) return;
      const name = chip.dataset.chip;
      if (name === 'lang' || name === 'units' || name === 'theme') {
        if (name === 'units') setUnits(chip.dataset.v);
        else S[name] = chip.dataset.v;
        applyTheme();
        render();
        rerenderBody();
      }
    });
    m.addEventListener('change', (e) => {
      const el = e.target;
      if (el.dataset.preset != null && el.value !== '') {
        S[el.dataset.preset] = Object.assign({}, PRESETS[+el.value]);
        render();
        rerenderBody();
      } else if (el.dataset.rig) {
        S[el.dataset.rig].rigidity = el.value;
        render();
      } else if (el.dataset.sel === 'S.level') {
        S.level = el.value;
        render();
      }
    });
    m.addEventListener('input', (e) => {
      const el = e.target;
      if (el.dataset.mach) {
        const [which, key] = el.dataset.mach.split('.');
        const v = parseNum(el.value, false);
        if (v != null && Number.isFinite(v) && v >= 0) {
          S[which][key] = fromDisp(v, el.dataset.q) / (+el.dataset.scale || 1);
          if (key === 'efficiency') S[which][key] = Math.min(1, Math.max(0.1, S[which][key]));
          render();
        }
      } else if (el.dataset.machname) {
        S[el.dataset.machname].name = el.value;
        persist();
      }
    });
  });
}

// ------------------------------------------------------------------------------ shell

const TABS = [['mill', 'Milling'], ['drill', 'Drilling'], ['tap', 'Tapping'], ['turn', 'Turning'], ['ref', 'Reference']];

function footer() {
  return `<footer class="site">${esc(t('Same calculation engine as the Android app.'))} <a href="${RELEASES}">${esc(t('Download the Android app'))}</a> · <a href="${REPO}">GitHub</a></footer>`;
}

function renderHeader() {
  document.documentElement.lang = S.lang;
  document.getElementById('tabs').innerHTML = TABS.map(([id, label]) => `<button class="tab ${S.tab === id ? 'active' : ''}" data-tab="${id}">${esc(t(label))}</button>`).join('');
  document.getElementById('langSeg').innerHTML = [['en', 'EN'], ['tr', 'TR']].map(([id, l]) => `<button class="${S.lang === id ? 'on' : ''}" data-lang="${id}">${l}</button>`).join('');
  document.getElementById('unitSeg').innerHTML = [['METRIC', 'mm'], ['IMPERIAL', 'inch']].map(([id, l]) => `<button class="${S.units === id ? 'on' : ''}" data-units="${id}">${l}</button>`).join('');
  const sb = document.getElementById('settingsBtn');
  sb.title = t('Settings');
  sb.setAttribute('aria-label', t('Settings'));
}

function applyTheme() {
  if (S.theme === 'auto') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', S.theme);
}

function render() {
  renderHeader();
  if (S.tab === 'ref') renderRef();
  else renderCalc();
  saveNow();
}

// ------------------------------------------------------------------------------ events

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-tab],[data-lang],[data-units],[data-action],[data-ref],[data-chip],[data-reset],[data-fit],[data-close],#settingsBtn');
  if (!el) return;
  if (el.dataset.close && e.target === el) { closeModal(); return; }
  if (el.dataset.close && el.tagName === 'BUTTON') { closeModal(); return; }
  if (el.closest('.modal')) return; // handled by the modal's own listener
  if (el.id === 'settingsBtn') { openSettings(); return; }
  if (el.dataset.tab) { S.tab = el.dataset.tab; render(); window.scrollTo(0, 0); return; }
  if (el.dataset.lang) { S.lang = el.dataset.lang; render(); return; }
  if (el.dataset.units) { setUnits(el.dataset.units); render(); return; }
  if (el.dataset.action === 'material') { openMaterialPicker(); return; }
  if (el.dataset.ref) {
    S.ref = el.dataset.ref;
    renderRef();
    document.querySelector('.refbody').scrollTop = 0;
    persist();
    return;
  }
  if (el.dataset.reset) {
    const [calc, key] = el.dataset.reset.split('.');
    IN[calc][key] = 100;
    renderCalc();
    return;
  }
  if (el.dataset.fit) {
    const m = /^([A-Z]+)(\d+)\/([a-z]+)(\d+)$/.exec(el.dataset.fit);
    Object.assign(R, { hole: m[1], hg: +m[2], shaft: m[3], sg: +m[4] });
    refreshRef(false);
    return;
  }
  if (el.dataset.chip) {
    const name = el.dataset.chip;
    if (name === 'std') R.std = el.dataset.v;
    else if (name === 'series') R.series = el.dataset.v;
    else if (name === 'screws') R.screws = el.dataset.v;
    refreshRef(false);
  }
});

document.addEventListener('input', (e) => {
  const el = e.target;
  if (el.closest('.modal')) return;
  if (el.dataset.num) {
    const [calc, key] = el.dataset.num.split('.');
    const q = el.dataset.q;
    const v = parseNum(el.value, imperial() && q === 'LENGTH');
    const box = el.parentElement;
    if (v === null) {
      box.classList.remove('bad');
      if (el.dataset.auto) { IN[calc][key] = null; recompute(); }
      return;
    }
    let base = fromDisp(v, q) / (+el.dataset.scale || 1);
    if (el.dataset.int) base = Math.round(base);
    const ok = Number.isFinite(v) && (el.dataset.min == null || base >= +el.dataset.min);
    box.classList.toggle('bad', !ok);
    if (!ok) return;
    IN[calc][key] = base;
    recompute();
  } else if (el.dataset.range) {
    const [calc, key] = el.dataset.range.split('.');
    IN[calc][key] = +el.value;
    const pct = el.parentElement.querySelector('.pct');
    pct.textContent = el.value + '%';
    pct.classList.toggle('off', +el.value !== 100);
    recompute();
  } else if (el.dataset.rnum) {
    const key = el.dataset.rnum;
    const q = el.dataset.q;
    const v = parseNum(el.value, q === 'LENGTH' && (el.dataset.sys ? el.dataset.sys === 'IMPERIAL' : imperial()));
    if (v === null && (key === 'near' || key === 'pctDrill')) { R[key] = null; refreshRef(); return; }
    if (v == null || !Number.isFinite(v)) return;
    const sys = el.dataset.sys || S.units;
    R[key] = q === 'NONE' ? v : (sys === 'IMPERIAL' ? v / Q[q].toImperial : v);
    refreshRef();
  }
});

document.addEventListener('change', (e) => {
  const el = e.target;
  if (el.closest('.modal')) return;
  if (el.dataset.sel) {
    const [calc, key] = el.dataset.sel.split('.');
    const value = el.dataset.numeric ? +el.value : el.value;
    const previous = calc === 'S' ? S[key] : IN[calc][key];
    if (calc === 'S') S[key] = value;
    else IN[calc][key] = value;
    if (calc === 'mill' && key === 'strategy') { IN.mill.ap = null; IN.mill.ae = null; }
    if (calc === 'mill' && key === 'toolType') applyMillType(value, previous);
    if (calc === 'turn' && key === 'operation') { IN.turn.ap = null; IN.turn.feed = null; }
    if (calc === 'tap' && key === 'standard') { IN.tap.thread = E.threads(value)[0].designation; IN.tap.drillDiameter = null; IN.tap.millDia = null; }
    if (calc === 'tap' && (key === 'thread' || key === 'tapType')) { IN.tap.drillDiameter = null; IN.tap.targetPercent = null; IN.tap.millDia = null; }
    if (el.dataset.rerender) renderCalc();
    else recompute();
  } else if (el.dataset.tog) {
    const [calc, key] = el.dataset.tog.split('.');
    IN[calc][key] = el.checked;
    renderCalc();
  } else if (el.dataset.rsel) {
    const key = el.dataset.rsel;
    R[key] = el.dataset.numeric ? +el.value : el.value;
    if (key === 'pctStd') { R.pctThread = E.threads(R.pctStd)[0].designation; R.pctDrill = null; }
    if (key === 'pctThread') R.pctDrill = null;
    if (key === 'conv') R.base = Q[R.conv].toImperial > 0 ? 1 / Q[R.conv].toImperial : 1;
    refreshRef(false);
  }
});

document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });

// Blur re-formats a numeric field so it shows the stored value (e.g. after typing a fraction).
document.addEventListener('focusout', (e) => {
  const el = e.target;
  if (!el.dataset || !el.dataset.num) return;
  const [calc, key] = el.dataset.num.split('.');
  const v = IN[calc][key];
  if (v != null && !el.parentElement.classList.contains('bad')) el.value = fmtEdit(v * (+el.dataset.scale || 1), el.dataset.q, el.dataset.int ? 0 : 1);
});


let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => { if (S.tab !== 'ref') recompute(); }, 150);
});

applyTheme();
render();
