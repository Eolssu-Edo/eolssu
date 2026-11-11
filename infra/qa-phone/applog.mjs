import { Rejected } from './control.mjs';

export const ApplogPath = '/_internal/qa-perf/snapshot';
export const ApplogHost = '127.0.0.1:27890';
export const ApplogMaxBytes = 128 * 1024;
export const ApplogTokenPattern = /^[A-Za-z0-9_-]{32,128}$/;
const ApplogMaxEvents = 128;
const idPattern = /^[a-f0-9]{32}$/;
const isId = value => typeof value === 'string' && idPattern.test(value);
const isCursor = value => Number.isSafeInteger(value) && value >= 0;
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

export function parseApplogRequest(value) {
  const { op, request_id, stream_id, after, through } = value;
  const fields = Object.keys(value).length;
  if (isId(request_id)) {
    if (op === 'begin' && fields === 2) return { op, request_id };
    if (op === 'read' && fields === 4 && isId(stream_id) && isCursor(after)) {
      return { op, request_id, stream_id, after };
    }
    if (op === 'ack' && fields === 4 && isId(stream_id) && isCursor(through)) {
      return { op, request_id, stream_id, through };
    }
  }
  throw new Rejected('app_log_invalid_request', 400);
}

export function parseApplogResponse(raw, request) {
  const unavailable = () => new Rejected('app_log_unavailable', 503);
  if (Buffer.byteLength(raw) > ApplogMaxBytes) throw unavailable();

  let value;
  try {
    value = JSON.parse(raw);
  } catch {
    throw unavailable();
  }
  const { op, request_id } = request;
  if (!isObject(value) || value.op !== op || value.request_id !== request_id) throw unavailable();
  if (value.error === 'snapshot_unavailable') throw new Rejected('app_log_snapshot_unavailable', 409);
  if (value.error || !isId(value.stream_id)) throw unavailable();

  const { stream_id, ack, end, events } = value;
  if (op === 'begin' && isCursor(ack) && isCursor(end) && end >= ack) {
    return { op, request_id, stream_id, ack, end };
  }
  if (stream_id !== request.stream_id) throw unavailable();
  if (op === 'ack' && isCursor(ack) && ack === request.through) {
    return { op, request_id, ack };
  }
  if (op === 'read' && Array.isArray(events) && events.length <= ApplogMaxEvents) {
    let previous = request.after;
    for (const entry of events) {
      if (!isObject(entry) || !isCursor(entry.seq) || entry.seq <= previous || !isObject(entry.event)) {
        throw unavailable();
      }
      previous = entry.seq;
    }
    return { op, request_id, stream_id, events: events.map(({ seq, event }) => ({ seq, event })) };
  }
  throw unavailable();
}
