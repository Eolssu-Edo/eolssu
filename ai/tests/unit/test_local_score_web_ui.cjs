const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function screen() {
  const elements = {};
  function element(id) {
    return elements[id] ||= {
      id, value: '', defaultValue: '', dataset: {}, hidden: false, disabled: false,
      textContent: '', className: '', files: [], listeners: {},
      addEventListener(name, fn) { this.listeners[name] = fn; },
      reportValidity() { return true; },
      replaceChildren() {},
      insertRow() { return { insertCell() { return {}; } }; },
    };
  }
  const sliderIds = ['pitch-cent', 'duration-ratio', 'onset-spread', 'pitch-penalty',
    'timing-penalty', 'large-error', 'weight-pitch', 'weight-timing', 'weight-duration'];
  const defaults = ['50', '25', '4', '0', '0', '1', '1', '1', '1'];
  const sliders = sliderIds.map((id, i) => Object.assign(element(id), {
    value: defaults[i], defaultValue: defaults[i], dataset: { unit: '' },
  }));
  element('audio').files = ['recording'];
  element('song').value = 'ngc-standard-2022-p173';
  element('bpm').value = '96';
  element('start').value = '0';
  element('experiment').querySelectorAll = () => sliders;
  const document = {
    getElementById: element,
    querySelector(selector) {
      if (selector.startsWith('#')) return element(selector.slice(1));
      const match = selector.match(/^output\[for="(.+)"\]$/);
      if (match) return element('output-' + match[1]);
      throw new Error('unknown selector: ' + selector);
    },
  };
  const pending = [];
  function fetch() { return new Promise(resolve => pending.push(resolve)); }
  const html = fs.readFileSync(path.join(__dirname, '../../evaluation/local_score_web.html'), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  vm.runInNewContext(script, { document, fetch, URLSearchParams, Number, Math, Error });
  return { element, pending };
}

const result = { status: 'DONE', total: 81, pitch: 91, timing: 93, duration: 60,
  tuning_offset_cent: -25, tuning_out_of_range: false, notes: [{idx: 0, verdict: 'CORRECT'}] };

test('녹음 분석 중 입력을 바꾸면 이전 결과를 표시하지 않는다', async () => {
  const s = screen();
  const request = s.element('form').listeners.submit({preventDefault() {}});
  s.element('audio').listeners.input();
  s.pending.shift()({ok: true, json: async () => result});
  await request;
  assert.equal(s.element('results').hidden, true);
  assert.equal(s.element('experiment').hidden, true);
});

test('기준을 바꾼 뒤 늦게 도착한 비교 결과는 표시하지 않는다', async () => {
  const s = screen();
  const first = s.element('form').listeners.submit({preventDefault() {}});
  s.pending.shift()({ok: true, json: async () => result});
  await first;
  assert.equal(s.element('results').hidden, false);
  const comparison = s.element('compare').listeners.click();
  s.element('pitch-cent').value = '40';
  s.element('pitch-cent').listeners.input();
  s.pending.shift()({ok: true, json: async () => ({result, onset_tolerance_ms: 335})});
  await comparison;
  assert.equal(s.element('comparison').hidden, true);
  assert.equal(s.element('compare').disabled, false);
});
