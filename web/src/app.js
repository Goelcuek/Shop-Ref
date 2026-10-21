/* Shop Ref web app. All machining maths comes from the shared Kotlin engine (global `shopref`). */
'use strict';

// The Kotlin/JS UMD bundle registers itself under its Gradle coordinates when loaded by a plain <script>.
const E = globalThis.shopref || globalThis['com.shopref:core'];
const Q = E.quantities();
const OPT = E.options();
const MATS = E.materials();
const MAT = Object.fromEntries(MATS.map((m) => [m.id, m]));
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
    cornerRadius: 1, chamferAngle: 90, chamferTipDiameter: 0, leadAngleDeg: 45, fluteLength: null, stickout: null,
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
  const opts = options.map((op) => {
    const id = typeof op === 'object' ? op.id : op;
    const lab = typeof op === 'object' ? (o.raw ? op.label : t(op.label)) : String(op);
    const title = typeof op === 'object' && op.description ? ` title="${esc(t(op.description))}"` : '';
    return `<option value="${esc(id)}"${String(id) === String(cur) ? ' selected' : ''}${title}>${esc(lab)}</option>`;
  }).join('');
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
    <span class="pct" data-reset="${calc}.${key}" title="100%">${Math.round(v)}%</span>
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

function summary(items) {
  return `<section class="summary" id="summary">${items.map((it) => `<div class="tile ${it.cls || ''}">
    <div class="k">${esc(it.k)}</div><div class="v">${esc(it.v)}<span class="u">${esc(it.u || '')}</span></div></div>`).join('')}</section>`;
}
const tile = (k, v, q, cls) => ({ k: t(k), v: fmt(v, q), u: uLabel(q), cls });

// ------------------------------------------------------------------------------ force chart

function forceChart(samples) {
  const W = 360;
  const H = 170;
  const keys = [['fz', '--chart-fz', true], ['fx', '--chart-fx'], ['fy', '--chart-fy'], ['fxy', '--chart-fxy']];
  let max = 1e-6;
  let min = 0;
  for (const s of samples) {
    max = Math.max(max, Math.abs(s.fx), Math.abs(s.fy), s.fxy, Math.abs(s.fz));
    min = Math.min(min, s.fx, s.fy, s.fz);
  }
  const top = max * 1.1;
  const bottom = min < 0 ? -max * 1.1 : 0;
  const y = (v) => ((top - v) / (top - bottom)) * H;
  const x = (i) => (i / (samples.length - 1)) * W;
  const grid = [0, 1, 2, 3, 4].map((q) => `<line x1="${(W * q) / 4}" y1="0" x2="${(W * q) / 4}" y2="${H}" stroke="var(--border)" stroke-width="1"/>`).join('');
  const lines = keys.map(([k, c, dash]) => `<polyline fill="none" stroke="var(${c})" stroke-width="2" ${dash ? 'stroke-dasharray="6 5"' : ''}
    vector-effect="non-scaling-stroke" points="${samples.map((s, i) => `${x(i).toFixed(1)},${y(s[k]).toFixed(1)}`).join(' ')}"/>`).join('');
  return `<div class="chart"><div class="legend" style="justify-content:space-between;margin:0 0 4px">
      <span>+${esc(fmtU(top, 'FORCE'))}</span><span>${esc(t('one revolution →'))}</span></div>
    <svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${grid}
      <line x1="0" y1="${y(0)}" x2="${W}" y2="${y(0)}" stroke="var(--muted)" stroke-width="1" vector-effect="non-scaling-stroke"/>${lines}</svg>
    ${bottom < 0 ? `<div class="legend">${esc(fmtU(bottom, 'FORCE'))}</div>` : ''}
    <div class="legend"><span><i style="background:var(--chart-fxy)"></i>|Fxy|</span><span><i style="background:var(--chart-fx)"></i>${esc(t('Fx feed'))}</span>
      <span><i style="background:var(--chart-fy)"></i>${esc(t('Fy normal'))}</span><span><i style="background:var(--chart-fz)"></i>${esc(t('Fz axial'))}</span></div></div>`;
}

// ------------------------------------------------------------------------------ calculators

const common = (calc, machine) => Object.assign({}, IN[calc], { material: S.material, units: S.units, lang: S.lang, machine });

const CALCS = {
  mill: {
    title: 'Milling',
    compute: () => E.milling(Object.assign(common('mill', S.mill), { chipLoadLevel: S.level })),
    inputs() {
      const i = IN.mill;
      const indexable = i.toolType === 'INDEXABLE';
      const tool = fields(
        sel('mill', 'toolType', 'Tool type', OPT.millToolTypes, { rerender: true, cls: 'span2' }),
        sel('mill', 'toolMaterial', 'Substrate', OPT.toolMaterials),
        indexable ? '' : sel('mill', 'coating', 'Coating', OPT.coatings),
        num('mill', 'diameter', 'Diameter', 'LENGTH', { min: 0.1 }),
        num('mill', 'flutes', indexable ? 'Inserts' : 'Flutes', 'NONE', { int: true, min: 1, extra: 0 }),
        i.toolType === 'CORNER_RADIUS' ? num('mill', 'cornerRadius', 'Corner radius', 'LENGTH', { min: 0 }) : '',
        i.toolType === 'CHAMFER' ? num('mill', 'chamferAngle', 'Included angle', 'ANGLE', { min: 10 }) : '',
        i.toolType === 'CHAMFER' ? num('mill', 'chamferTipDiameter', 'Tip diameter', 'LENGTH', { min: 0 }) : '',
        indexable ? num('mill', 'leadAngleDeg', 'Lead angle κr (90° = square shoulder)', 'ANGLE', { min: 10, cls: 'span2' }) : '',
        indexable ? '' : num('mill', 'fluteLength', 'Flute length', 'LENGTH', { auto: 'fluteLengthAuto', min: 0.1 }),
        indexable ? '' : num('mill', 'stickout', 'Stick-out', 'LENGTH', { auto: 'stickoutAuto', min: 0.1 }),
        indexable ? '' : num('mill', 'helixDeg', 'Helix', 'ANGLE'),
        num('mill', 'rakeDeg', 'Radial rake', 'ANGLE', { auto: 'rakeAuto' }),
        indexable ? '' : num('mill', 'shankDiameter', 'Shank Ø', 'LENGTH', { auto: 'diameter', min: 0.1 }),
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
    summary: (r) => summary([
      tile('Spindle', r.rpm, 'SPINDLE_SPEED'), tile('Feed', r.feed, 'FEED_RATE'),
      tile('Power', r.powerKw, 'POWER', warnClass(r.powerKw / r.availablePowerKw)),
      tile('Speed', r.vc, 'CUTTING_SPEED'), tile('Chip load', r.fz, 'CHIP_LOAD'), tile('MRR', r.mrr, 'MRR'),
    ]),
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
        i.speedPercent !== 100 ? [t('Est. tool life vs. recommended'), fmtN(r.relativeToolLife * 100, 0) + '%'] : null,
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
        ...(i.toolType === 'INDEXABLE' ? [] : ['-',
          [t('Tool deflection'), fmtU(r.deflection * 1000, 'DEFLECTION'), 'em'],
          [t('Bending stress'), fmtU(r.bendingStress, 'STRESS'), warnClass(r.bendingStress / r.allowableStress)],
          [t('Torsional stress'), fmtU(r.torsionStress, 'STRESS')]]),
      ])) + card(esc(t('Force over one revolution')), forceChart(r.samples)
        + note(esc(t('Mechanistic model: Kienzle kc with oblique-cutting force ratios, integrated over teeth, helix and rotation. Rigid tool, no runout.'))))
        + card(esc(t('Entry moves & finish')), rows([
          [t('Max ramp angle'), fmtN(r.rampAngleDeg, 1) + '°'],
          [t('Ramp / helix feed'), fmtU(r.rampFeed, 'FEED_RATE')],
          [t('Plunge feed (centre-cutting)'), fmtU(r.plungeFeed, 'FEED_RATE')],
          [t('Helical bore Ø'), fmt(i.diameter * 1.2, 'LENGTH') + ' – ' + fmtU(i.diameter * 1.8, 'LENGTH')],
          r.cuspHeight != null ? [t(i.toolType === 'BALL_END' ? 'Scallop height' : 'Theoretical cusp height'), fmtU(r.cuspHeight * 1000, 'DEFLECTION')] : null,
        ])) + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
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
    summary: (r) => summary([
      tile('Spindle', r.rpm, 'SPINDLE_SPEED'), tile('Feed', r.feed, 'FEED_RATE'),
      tile('Power', r.powerKw, 'POWER', warnClass(r.powerKw / r.availablePowerKw)),
      tile('Speed', r.vc, 'CUTTING_SPEED'), tile('Feed/rev', r.fn, 'FEED_PER_REV'), tile('Thrust', r.thrust, 'FORCE'),
    ]),
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
    summary: (r) => summary([
      tile('Spindle', r.rpm, 'SPINDLE_SPEED'), tile('Feed', r.feed, 'FEED_RATE'),
      tile('Torque', r.torqueNm, 'TORQUE', warnClass(1.5 / r.safetyFactor)),
      tile('Speed', r.vc, 'CUTTING_SPEED'), tile('Drill', r.drill, 'LENGTH'),
      { k: t('Thread'), v: fmtN(r.percentThread, 0), u: '%' },
    ]),
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
        [t('Tapping torque (est.)'), fmtU(r.torqueNm, 'TORQUE'), 'em ' + warnClass(1.5 / r.safetyFactor)],
        [t('Tap breaking torque (est.)'), fmtU(r.breakingTorqueNm, 'TORQUE')],
        [t('Safety factor'), fmtN(r.safetyFactor, 1)],
        [t('Power'), fmtU(r.powerKw, 'POWER')],
        [t('Chip thickness per tooth'), fmtU(r.chipThickness, 'CHIP_LOAD')],
      ]) + note(esc(t('Torque M = kc·A·dm/2 (energy balance over the thread groove, Kienzle kc) × process factors for land friction and chip packing (Cao & Sutherland 2002). Forming ≈ 2× cutting torque.'))))
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
    summary: (r) => summary([
      tile('Spindle', r.rpm, 'SPINDLE_SPEED'), tile('Feed', r.feedRate, 'FEED_RATE'),
      tile('Power', r.powerKw, 'POWER', warnClass(r.powerKw / r.availablePowerKw)),
      tile('Speed', r.vc, 'CUTTING_SPEED'), tile('Feed/rev', r.feed, 'FEED_PER_REV'), tile('Ra', r.ra, 'ROUGHNESS'),
    ]),
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
        + adviceHtml(r.advice.filter((a) => a.level === 'INFO'));
    },
    after() {
      const el = document.getElementById('raOut');
      if (el) el.textContent = fmtU(E.feedForRa(IN.turn.targetRa, IN.turn.raNose), 'FEED_PER_REV');
    },
  },
};

let lastResult = null;

function renderCalc() {
  const c = CALCS[S.tab];
  const view = document.getElementById('view');
  view.innerHTML = `<div id="sumSlot"></div><div class="calc"><div class="inputs">${c.inputs()}</div>
    <aside class="results" id="results"></aside></div>${footer()}`;
  recompute();
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
  document.getElementById('sumSlot').innerHTML = c.summary(r);
  // Cautions and dangers lead the results panel so they are never below its fold; notes stay at the end.
  document.getElementById('results').innerHTML = adviceHtml(r.advice.filter((a) => a.level !== 'INFO')) + c.results(r);
  const sum = document.getElementById('summary');
  document.documentElement.style.setProperty('--summary-h', (sum ? sum.offsetHeight : 0) + 'px');
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

const R = {
  std: 'METRIC_COARSE', pctStd: 'UNC', pctThread: '1/4-20 UNC', pctDrill: null, series: '', near: null, screws: 'METRIC',
  fitSize: 25, hole: 'H', hg: 7, shaft: 'g', sg: 6, hrc: 45, ra: 1.6, vc: 100, dia: 10, rpm: 3000, fz: 0.05, z: 4, feed: 600,
  conv: 'LENGTH', base: 25.4, cskDia: 12, cskAngle: 90, cskTip: 0,
};
Object.assign(R, store.get('ref.state', {}));

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
      <div style="display:flex;flex-direction:column;gap:16px">${card(esc(t('Flat (countersunk) head screws ({0})', u)), table([t('Screw'), t('Head Ø'), t('Angle'), t('Thru hole'), t('Spot depth*')],
        cs.map((c) => `<tr><td><b>${c.size}</b></td><td class="n">${len(c.head)}</td><td class="n">${fmtN(c.angle, 0)}°</td><td class="n">${len(c.clearance)}</td><td class="n">${len(c.spotDepth)}</td></tr>`).join(''), { num: true })
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
  const view = document.getElementById('view');
  const page = REF_PAGES.find((p) => p[0] === S.ref) || REF_PAGES[0];
  view.innerHTML = `<div class="ref"><nav class="refnav">${REF_PAGES.map(([id, title, desc]) => `<button class="${id === page[0] ? 'on' : ''}" data-ref="${id}">${esc(t(title))}<small>${esc(t(desc))}</small></button>`).join('')}</nav>
    <div class="refbody"><h2>${esc(t(page[1]))}</h2><div id="refContent">${REF[page[0]]()}</div></div></div>${footer()}`;
  document.documentElement.style.setProperty('--summary-h', '0px');
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
        S[name] = chip.dataset.v;
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
  if (el.dataset.units) { S.units = el.dataset.units; render(); return; }
  if (el.dataset.action === 'material') { openMaterialPicker(); return; }
  if (el.dataset.ref) { S.ref = el.dataset.ref; renderRef(); persist(); return; }
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
    el.parentElement.querySelector('.pct').textContent = el.value + '%';
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
    if (calc === 'S') S[key] = value;
    else IN[calc][key] = value;
    if (calc === 'mill' && key === 'strategy') { IN.mill.ap = null; IN.mill.ae = null; }
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

window.addEventListener('resize', () => {
  const sum = document.getElementById('summary');
  document.documentElement.style.setProperty('--summary-h', (sum ? sum.offsetHeight : 0) + 'px');
});

applyTheme();
render();
