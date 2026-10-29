/*
 * 시김새 창에서 트는 소리(처음 배우는 시김새 11개)를 미리 만든다.
 *
 * 와이어프레임의 합성 소리(guide-screens.js playSig · sigCurves)를 브라우저 없이 그대로 다시 계산한다.
 * 붓끝이 지나는 높이를 단소 소리가 따라가고, 소리 길이·시간표는 앱의 붓질(SigimsaeDrawSeconds 2.16초)과 같다.
 * 와이어프레임은 소리를 0.03초 늦게 시작하지만 여기서는 0초에 시작해 파일 처음 = 붓질 처음이 되게 한다.
 * 잡음은 고정된 씨앗으로 만들어 다시 돌려도 같은 소리가 난다(MP3 바이트는 ffmpeg 판에 따라 조금 다를 수 있다).
 *
 * 사용법(frontend/에서, Node 18 이상과 ffmpeg(libmp3lame)가 PATH에 있어야 한다):
 *     node tools/sigimsae-sound/build_sigimsae_sounds.mjs
 * 결과: shared/src/commonMain/composeResources/files/sigimsae/<이름>.mp3 (모노 44.1kHz 96kbps)
 *
 * 그림 값은 shared/.../feature/guide/Sigimsae.kt 의 BasicSigimsae와 같아야 한다. 그림을 바꾸면 여기도 고치고 다시 만든다.
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '../../shared/src/commonMain/composeResources/files/sigimsae');

/* ───── 시김새 그림(Sigimsae.kt BasicSigimsae) ─────
 * curve: viewBox 72×38, 적힌 음 y=24, 한 음 = unit(기본 6). grace: viewBox 72×48, 본음 y=42, 한 음 = 11.
 * tailMid: 흘림표 꼬리 가운데 줄(소리 높이만 따른다. 그림에는 없다) */
const SIGIMSAE = [
  { file: 'chuseong', name: '추성', curve: 'M4 24H44C54 24 60 22 68 12' },
  { file: 'mineunpyo', name: '미는표', curve: 'M4 34C10 34 14 24 22 24H68' },
  { file: 'toeseong', name: '퇴성', curve: 'M4 24H46L51 32H68', unit: 8 },
  { file: 'heullimpyo', name: '흘림표', curve: 'M4 24H30', tailMid: 'M30 24C44 24 56 29 68 36' },
  { file: 'yoseong', name: '요성·농음', curve: 'M4 24H14Q19 16 24 24T34 24T44 24T54 24T64 24H68', vib: true },
  { file: 'nire', name: '니레', grace: [1] },
  { file: 'nira', name: '니라', grace: [2] },
  { file: 'niro', name: '니로', grace: [3] },
  { file: 'noniro', name: '노니로', grace: [0, 1] },
  { file: 'kkeunneunpyo', name: '끊는표', curve: 'M4 24H16' },
  { file: 'neurimpyo', name: '늘임표', curve: 'M4 24H68' },
];

const GRACE_BASE = 42, GRACE_STEP = 11;
function gracePath(steps) {   // Sigimsae.kt gracePath와 같다
  const xs = steps.map((_, i) => 9 + i * 13), ys = steps.map(s => GRACE_BASE - s * GRACE_STEP);
  const last = xs.length - 1, mx = xs[last] + 14;
  let d = `M${xs[0]} ${ys[0]}`;
  for (let i = 1; i <= last; i++) d += `C${xs[i - 1] + 7} ${ys[i - 1]} ${xs[i] - 7} ${ys[i]} ${xs[i]} ${ys[i]}`;
  return d + `C${xs[last] + 7} ${ys[last]} ${mx - 7} ${GRACE_BASE} ${mx} ${GRACE_BASE}H68`;
}

/* ───── SVG path → 길이를 따라 고른 간격의 점(getPointAtLength 대신) ─────
 * 쓰는 명령(대문자 M H L C Q T)만 읽는다. 곡선은 잘게 펴서 길이를 잰다 */
function flatten(d) {
  const tokens = d.match(/[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?/g);
  const pts = [];
  let i = 0, cmd = '', x = 0, y = 0, qx = null, qy = null;
  const num = () => +tokens[i++];
  const bezier = (fn) => { for (let k = 1; k <= 256; k++) pts.push(fn(k / 256)); };
  while (i < tokens.length) {
    if (/[A-Za-z]/.test(tokens[i])) cmd = tokens[i++];
    if (cmd === 'M') { x = num(); y = num(); pts.push([x, y]); cmd = 'L'; qx = null; }
    else if (cmd === 'L') { x = num(); y = num(); pts.push([x, y]); qx = null; }
    else if (cmd === 'H') { x = num(); pts.push([x, y]); qx = null; }
    else if (cmd === 'V') { y = num(); pts.push([x, y]); qx = null; }
    else if (cmd === 'C') {
      const [x0, y0, x1, y1, x2, y2, x3, y3] = [x, y, num(), num(), num(), num(), num(), num()];
      bezier(t => { const u = 1 - t; return [u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3, u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3]; });
      x = x3; y = y3; qx = null;
    } else if (cmd === 'Q' || cmd === 'T') {
      const [x0, y0] = [x, y];
      let x1, y1;
      if (cmd === 'Q') { x1 = num(); y1 = num(); } else if (qx === null) { x1 = x0; y1 = y0; } else { x1 = 2 * x0 - qx; y1 = 2 * y0 - qy; }
      const x2 = num(), y2 = num();
      bezier(t => { const u = 1 - t; return [u * u * x0 + 2 * u * t * x1 + t * t * x2, u * u * y0 + 2 * u * t * y1 + t * t * y2]; });
      x = x2; y = y2; qx = x1; qy = y1;
    } else throw new Error(`읽지 못하는 path 명령 ${cmd}: ${d}`);
  }
  return pts;
}
function samplePath(d, step) {
  const poly = flatten(d), acc = [0];
  for (let k = 1; k < poly.length; k++) acc.push(acc[k - 1] + Math.hypot(poly[k][0] - poly[k - 1][0], poly[k][1] - poly[k - 1][1]));
  const L = acc[acc.length - 1], at = (s) => {
    let k = 1; while (k < acc.length - 1 && acc[k] < s) k++;
    const span = acc[k] - acc[k - 1], f = span > 0 ? (s - acc[k - 1]) / span : 0;
    return [poly[k - 1][0] + (poly[k][0] - poly[k - 1][0]) * f, poly[k - 1][1] + (poly[k][1] - poly[k - 1][1]) * f];
  };
  const pts = [];
  for (let s = 0; s < L; s += step) pts.push(at(s));
  pts.push(at(L));
  return pts;
}
function yAt(pts, x) {
  if (!pts || x < pts[0][0] || x > pts[pts.length - 1][0]) return null;
  let i = 1; while (i < pts.length - 1 && pts[i][0] < x) i++;
  const [ax, ay] = pts[i - 1], [bx, by] = pts[i];
  return bx === ax ? by : ay + (by - ay) * (x - ax) / (bx - ax);
}

/* ───── 붓끝 시간표와 소리 높이·세기(와이어프레임 sigHead · sigFreq · settle · sigCurves) ───── */
const SIG_DRAW = 2.16;
function cubicY(p, x1, y1, x2, y2) {
  let lo = 0, hi = 1, u = p;
  for (let k = 0; k < 24; k++) { u = (lo + hi) / 2; if (3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u < p) lo = u; else hi = u; }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}
const sigHead = (t) => t >= SIG_DRAW ? 999 : -5 + 80 * cubicY(Math.max(0, t) / SIG_DRAW, .5, 0, .35, 1);
const HWANG = 622.25;                     // 단소로 부는 황(黃) ≈ E♭5
const PYEONG = [-5, -3, 0, 2, 5, 7, 9];   // 평조: 황에서 반음 수
function sigFreq(step) {
  const k = step + 2, i = Math.max(0, Math.min(PYEONG.length - 2, Math.floor(k)));
  return HWANG * 2 ** ((PYEONG[i] + (PYEONG[i + 1] - PYEONG[i]) * (k - i)) / 12);
}
const SETTLE = 2.5;
function settle(s) {
  const n = Math.floor(s), u = s - n;
  return n + (u < .5 ? .5 * (2 * u) ** SETTLE : 1 - .5 * (2 - 2 * u) ** SETTLE);
}
function sigCurves(it) {
  const grace = !!it.grace;
  const base = grace ? GRACE_BASE : 24, unit = grace ? GRACE_STEP : (it.unit || 6), N = 160;
  const pts = samplePath(grace ? gracePath(it.grace) : it.curve, .35);
  const tailPts = it.tailMid ? samplePath(it.tailMid, .5) : null;
  const freq = new Float32Array(N), gain = new Float32Array(N);
  let f = 0, g = 0;
  for (let i = 0; i < N; i++) {
    const x = sigHead(i / (N - 1) * SIG_DRAW * .999);
    let y = yAt(pts, x), loud = 1, flow = !!it.vib;
    if (y === null && tailPts) { y = yAt(tailPts, x); flow = true; if (y !== null) loud = (1 - (x - tailPts[0][0]) / (68 - tailPts[0][0])) ** 1.6; }
    const target = y === null ? 0 : loud;
    g += (target - g) * (target > g ? .3 : .12);
    gain[i] = g;
    if (y !== null) f = sigFreq(flow ? (base - y) / unit : settle((base - y) / unit));
    freq[i] = f;
  }
  const first = freq.find(v => v > 0) || HWANG;
  for (let i = 0; i < N && !freq[i]; i++) freq[i] = first;
  return { freq, gain };
}

/* ───── Web Audio 흉내(명세의 계산식대로) ───── */
const RATE = 44100, NYQ = RATE / 2;
const D = SIG_DRAW, LENGTH = Math.round((D + .4) * RATE);   // 와이어프레임: 붓질 2.16초 + 0.4초 뒤 모든 소리를 멈춘다

/** setValueCurveAtTime(values, 0, D): 사이는 직선으로 잇고 D 뒤로는 마지막 값을 유지한다. */
function curveAt(values, t) {
  if (t >= D) return values[values.length - 1];
  const p = t / D * (values.length - 1), k = Math.floor(p);
  return values[k] + (values[k + 1] - values[k]) * (p - k);
}

function mulberry32(seed) {
  return () => { seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
/** 2초짜리 고른 잡음(-1~1)을 되풀이하고, 갈래마다 다른 자리에서 시작한다(와이어프레임 noise()). */
function makeNoise(seed) {
  const rnd = mulberry32(seed), buf = new Float32Array(RATE * 2);
  for (let i = 0; i < buf.length; i++) buf[i] = rnd() * 2 - 1;
  let nth = 0;
  return () => {
    const off = Math.round(((nth++ * .37) % 1.9) * RATE), out = new Float32Array(LENGTH);
    for (let i = 0; i < LENGTH; i++) out[i] = buf[(off + i) % buf.length];
    return out;
  };
}
/** BiquadFilterNode(Audio EQ Cookbook). bandpass의 Q는 그대로, lowpass의 Q는 dB다. */
function biquad(input, type, hz, q) {
  const w0 = 2 * Math.PI * hz / RATE, cos = Math.cos(w0), sin = Math.sin(w0);
  let b0, b1, b2, a0, a1, a2;
  if (type === 'bandpass') {
    const alpha = sin / (2 * q);
    [b0, b1, b2, a0, a1, a2] = [alpha, 0, -alpha, 1 + alpha, -2 * cos, 1 - alpha];
  } else {
    const alpha = sin / (2 * 10 ** (q / 20));
    [b0, b1, b2, a0, a1, a2] = [(1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha];
  }
  const out = new Float32Array(input.length);
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < input.length; i++) {
    const x = input[i], y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
    out[i] = y; x2 = x1; x1 = x; y2 = y1; y1 = y;
  }
  return out;
}

/* 소리 짜임(와이어프레임 playSig): 국립국악원 정악 '상령산' 단소 트랙에서 잰 배음·숨 세기. 녹음은 싣지 않고 잰 숫자만 쓴다 */
const DANSO_HARM = [0, 1, .036, .201, .021, .037, .010, .012, .004, .004, .002];
const DANSO_AIR = [[600, .75, 2.2e-4], [1400, 1.41, 5.8e-4], [2800, 1.41, 1.7e-4], [5600, 1.41, 1.4e-4]];
const WHITE = .577, TONE_RMS = .7;
const lvl = (want, bw) => want / (WHITE * Math.sqrt(bw / NYQ));

/** PeriodicWave(real 0, imag DANSO_HARM)의 한 주기. Web Audio처럼 가장 큰 값이 1이 되게 맞춘다. */
const TABLE = (() => {
  const n = 4096, table = new Float32Array(n);
  for (let i = 0; i < n; i++) { let v = 0; DANSO_HARM.forEach((a, k) => { v += a * Math.sin(2 * Math.PI * k * i / n); }); table[i] = v; }
  const peak = table.reduce((m, v) => Math.max(m, Math.abs(v)), 0);
  return table.map(v => v / peak);
})();

function render(it) {
  const { freq, gain } = sigCurves(it);
  const noise = makeNoise(0x5161);
  const air = noise();
  const drift = (hz, want) => { const s = biquad(noise(), 'lowpass', hz, .7), g = lvl(want, hz * 1.1); return s.map(v => v * g); };
  const detune = drift(4, 5), wobble = drift(6, .05);   // 음높이 ±5센트 남짓, 세기 ±5% 남짓
  const breath = new Float32Array(LENGTH);
  DANSO_AIR.forEach(([hz, q, energy]) => {
    const band = biquad(air, 'bandpass', hz, q), g = lvl(TONE_RMS * Math.sqrt(energy), hz / q);
    for (let i = 0; i < LENGTH; i++) breath[i] += band[i] * g;
  });
  const puff = gain.map((g, i) => 1 + 10 * Math.max(0, g - gain[Math.max(0, i - 3)]));   // 첫소리에서만 숨을 세게
  const out = new Float32Array(LENGTH);
  let phase = 0;
  for (let i = 0; i < LENGTH; i++) {
    const t = i / RATE;
    const tube = TABLE[Math.floor(phase * TABLE.length) % TABLE.length] * (1 + wobble[i]);
    phase = (phase + curveAt(freq, t) * 2 ** (detune[i] / 1200) / RATE) % 1;
    out[i] = (tube + breath[i] * curveAt(puff, t)) * curveAt(gain, t) * .3;
  }
  return out;
}

function wav(samples) {
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((v, i) => data.writeInt16LE(Math.round(Math.max(-1, Math.min(1, v)) * 32767), i * 2));
  const head = Buffer.alloc(44);
  head.write('RIFF', 0); head.writeUInt32LE(36 + data.length, 4); head.write('WAVE', 8);
  head.write('fmt ', 12); head.writeUInt32LE(16, 16); head.writeUInt16LE(1, 20); head.writeUInt16LE(1, 22);
  head.writeUInt32LE(RATE, 24); head.writeUInt32LE(RATE * 2, 28); head.writeUInt16LE(2, 32); head.writeUInt16LE(16, 34);
  head.write('data', 36); head.writeUInt32LE(data.length, 40);
  return Buffer.concat([head, data]);
}

mkdirSync(OUT_DIR, { recursive: true });
const work = mkdtempSync(join(tmpdir(), 'sigimsae-'));
try {
  for (const it of SIGIMSAE) {
    const samples = render(it), peak = samples.reduce((m, v) => Math.max(m, Math.abs(v)), 0);
    const src = join(work, `${it.file}.wav`), dst = join(OUT_DIR, `${it.file}.mp3`);
    writeFileSync(src, wav(samples));
    execFileSync('ffmpeg', ['-loglevel', 'error', '-y', '-i', src, '-map_metadata', '-1', '-c:a', 'libmp3lame', '-b:a', '96k', '-ac', '1', '-ar', String(RATE), dst]);
    console.log(`${it.name}\t${it.file}.mp3\tpeak ${peak.toFixed(3)}`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}
