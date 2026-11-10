import http from 'node:http';
import test from 'node:test';
import assert from 'node:assert/strict';
import { AdbPhone } from '../adb.mjs';
import { ApplogHost, ApplogMaxBytes, ApplogPath, parseApplogResponse } from '../applog.mjs';
import { startRelay } from '../server.mjs';

const token = 'fixture-browser-key-longer-than-24';
const serviceToken = 'fixture-app-log-service-key-32-chars';
const identity = 'PHONE_FIXTURE';
const request_id = '1'.repeat(32);
const stream_id = '2'.repeat(32);
const begin = { op: 'begin', request_id };
const read = { op: 'read', request_id, stream_id, after: 5 };
const ack = { op: 'ack', request_id, stream_id, through: 6 };
const snapshot = { ...begin, stream_id, ack: 5, end: 6 };

async function fixture(t, handle, applog = { token: serviceToken, identity }) {
  const calls = [];
  const relay = await startRelay({
    port: 0, token, basePath: '/qa/', applog, phone: {
      capture: async () => ({ data: Buffer.alloc(24), width: 1, height: 1 }),
      applog: async (request, expected) => {
        calls.push({ request, expected });
        return handle(request);
      },
    },
  });
  t.after(() => relay.close());
  function request(payload, { path = ApplogPath, authorization = `Bearer ${serviceToken}`, headers = {} } = {}) {
    return new Promise((resolve, reject) => {
      const req = http.request(relay.url + path, {
        method: 'POST', headers: { Host: ApplogHost, 'Content-Type': 'application/json', Authorization: authorization, ...headers },
      }, res => {
        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => resolve({ status: res.statusCode, data: JSON.parse(Buffer.concat(chunks)) }));
        res.on('error', reject);
      });
      req.on('error', reject);
      req.end(JSON.stringify(payload));
    });
  }
  return { calls, relay, request };
}

test('the private operation requires a distinct service bearer and rejects browser and public paths', async t => {
  const f = await fixture(t, () => JSON.stringify(snapshot));
  for (const options of [
    { authorization: `Bearer ${token}` },
    { authorization: '' },
    { headers: { Host: '127.0.0.1:27891' } },
    { headers: { Origin: f.relay.url } },
    { headers: { 'Sec-Fetch-Site': 'same-origin' } },
  ]) assert.equal((await f.request(begin, options)).status, 401);
  assert.equal((await f.request(begin, { path: '/qa' + ApplogPath, headers: { Host: new URL(f.relay.url).host } })).status, 404);
  assert.equal((await f.request(begin, { path: ApplogPath + '?unexpected=1', headers: { Host: new URL(f.relay.url).host } })).status, 404);
  for (const payload of [
    { ...begin, request_id: '../foo' },
    { ...begin, extra: true },
    { ...read, after: -1 },
    { ...read, after: Number.MAX_SAFE_INTEGER + 1 },
    { ...ack, stream_id: stream_id.toUpperCase() + 'x' },
  ]) assert.equal((await f.request(payload)).status, 400);
  assert.deepEqual(f.calls, []);
  assert.deepEqual(await f.request(begin), { status: 200, data: snapshot });
  assert.deepEqual(f.calls, [{ request: begin, expected: identity }]);
});

test('disabled and non-phone relays cannot expose the private operation', async t => {
  const f = await fixture(t, () => assert.fail('disabled relay ran ADB'), null);
  assert.equal((await f.request(begin)).status, 404);
  await assert.rejects(startRelay({
    port: 0, token, basePath: '/qa/tablet/', deviceId: 'tablet',
    applog: { token: serviceToken, identity },
    phone: { applog: async () => {} },
  }), /invalid phone app log configuration/);
});

test('one ADB operation at a time and installation never invokes the app log hook', async t => {
  let entered, release;
  const started = new Promise(resolve => { entered = resolve; });
  const pending = new Promise(resolve => { release = resolve; });
  const f = await fixture(t, async () => {
    entered();
    await pending;
    return JSON.stringify(snapshot);
  });
  const first = f.request(begin);
  try {
    await started;
    assert.deepEqual(await f.request(begin), { status: 409, data: { error: 'app_log_busy' } });
    f.relay.control.maintenance = true;
    assert.deepEqual(await f.request(begin), { status: 409, data: { error: 'app_log_installing' } });
  } finally {
    release();
  }
  assert.equal((await first).status, 200);
  assert.equal(f.calls.length, 1);
});

test('mismatched or invalid snapshots are rejected and raw ADB failures are not returned', async t => {
  const f = await fixture(t, request => {
    if (request.op === 'begin') throw new Error('stderr with sensitive fixture');
    if (request.op === 'read') return JSON.stringify({ ...read, request_id: '0'.repeat(32), events: [] });
    return JSON.stringify({ ...ack, error: 'snapshot_unavailable' });
  });
  assert.deepEqual(await f.request(begin), { status: 503, data: { error: 'app_log_unavailable' } });
  assert.deepEqual(await f.request(read), { status: 503, data: { error: 'app_log_unavailable' } });
  assert.deepEqual(await f.request(ack), { status: 409, data: { error: 'app_log_snapshot_unavailable' } });
});

test('read and ACK are checked for matching stream, range and response budget', () => {
  const entry = { seq: 6, event: { name: 'resource_load' } };
  const page = { op: 'read', request_id, stream_id, events: [entry] };
  assert.deepEqual(parseApplogResponse(JSON.stringify(page), read), page);
  assert.deepEqual(parseApplogResponse(JSON.stringify({ op: 'ack', request_id, stream_id, ack: 6 }), ack),
    { op: 'ack', request_id, ack: 6 });
  for (const value of [
    { ...page, stream_id: '3'.repeat(32) },
    { ...page, events: [entry, entry] },
    { ...page, events: [{ ...entry, seq: 5 }] },
    { ...page, events: [{ ...entry, event: { padding: '가'.repeat(ApplogMaxBytes / 3) } }] },
  ]) assert.throws(() => parseApplogResponse(JSON.stringify(value), read), /app_log_unavailable/);
  assert.throws(() => parseApplogResponse(JSON.stringify({ op: 'ack', request_id, stream_id, ack: 7 }), ack),
    /app_log_unavailable/);
});

test('ADB uses only the verified physical transport and cleans exactly its own response file', async () => {
  let currentIdentity = identity;
  let failRead = false;
  const calls = [];
  const phone = new AdbPhone({
    serial: '192.0.2.2:5555', executor: async (_file, args, options) => {
      calls.push({ args, options });
      if (args.includes('devices')) return { stdout: '192.0.2.2:5555 device transport_id:7\n' };
      if (args.includes('ro.serialno')) return { stdout: currentIdentity + '\n' };
      if (args.includes('cat')) {
        if (failRead) throw new Error('no response');
        return { stdout: Buffer.from(JSON.stringify(snapshot)) };
      }
      return { stdout: '' };
    },
  });
  await phone.applog(read, identity);
  const file = `cache/qa-perf/${request_id}-read.json`;
  const adb = ['-P', '5037', '-t', '7'];
  const runAs = ['run-as', 'com.ssafy.eolssu.qa', '--user', '0'];
  assert.deepEqual(calls.slice(2).map(call => call.args), [
    [...adb, 'shell', ...runAs, 'rm', '-f', file],
    [...adb, 'shell', 'am', 'broadcast', '--user', '0', '--receiver-registered-only',
      '-a', 'com.ssafy.eolssu.qa.APPLOG', '-p', 'com.ssafy.eolssu.qa',
      '--es', 'op', 'read', '--es', 'request_id', request_id, '--es', 'stream_id', stream_id, '--el', 'cursor', '5'],
    [...adb, 'exec-out', ...runAs, 'cat', file],
    [...adb, 'shell', ...runAs, 'rm', '-f', file],
  ]);
  assert.equal(calls[4].options.maxBuffer, ApplogMaxBytes);
  failRead = true;
  await assert.rejects(phone.applog(ack, identity), /no response/);
  assert.deepEqual(calls.at(-1).args, [...adb, 'shell', ...runAs, 'rm', '-f', `cache/qa-perf/${request_id}-ack.json`]);
  currentIdentity = 'WRONG_PHONE';
  const before = calls.length;
  await assert.rejects(phone.applog(begin, identity), /identity mismatch/);
  assert.deepEqual(calls.slice(before).map(call => call.args), [[...adb, 'shell', 'getprop', 'ro.serialno']]);
});
