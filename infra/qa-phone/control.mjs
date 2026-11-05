import { randomUUID } from 'node:crypto';
export class Rejected extends Error {
  constructor(message, status = 409) { super(message); this.status = status; }
}
export function validateAction(a) {
  if (!a || typeof a !== 'object') throw new Rejected('invalid action', 400);
  const point = (x, y) => Number.isFinite(x) && Number.isFinite(y) && x >= 0 && x <= 1 && y >= 0 && y <= 1;
  if (a.kind === 'tap' && point(a.x, a.y)) return { kind: 'tap', x: a.x, y: a.y };
  if (a.kind === 'swipe' && point(a.x, a.y) && point(a.x2, a.y2) && Number.isInteger(a.duration) && a.duration >= 50 && a.duration <= 1000)
    return { kind: 'swipe', x: a.x, y: a.y, x2: a.x2, y2: a.y2, duration: a.duration };
  if (a.kind === 'key' && ['BACK','HOME','APP_SWITCH','ENTER','DEL','TAB','DPAD_UP','DPAD_DOWN','DPAD_LEFT','DPAD_RIGHT'].includes(a.key))
    return { kind: 'key', key: a.key };
  if (a.kind === 'text' && typeof a.text === 'string' && a.text.length>0&&a.text.length<=128&&!/[\p{C}]/u.test(a.text))
    return { kind: 'text', text: a.text };
  throw new Rejected('unsupported input (text: 1-128 characters; control characters excluded)', 400);
}
export class Control {
  constructor({ input, now = Date.now, grace = 10000 }) {
    this.sendInput = input; this.now = now; this.grace = grace;
    this.requestedAt=new Map();this.responseMs=30000;
    this.decisions=new Map();this.clients = new Map(); this.requests = []; this.owner = null; this.online = false;
    this.maintenance = false; this.epoch = randomUUID(); this.seq = 0; this.tail = Promise.resolve(); this.pending = 0;
  }
  join(id, name) { this.clients.set(id, { name, seen: this.now() }); }
  touch(id) { const c = this.clients.get(id); if (c) c.seen = this.now(); }
  serial(fn) {
    if (this.pending >= 32) return Promise.reject(new Rejected('busy; retry', 429));
    this.pending++;
    const result = this.tail.then(fn);
    this.tail = result.catch(() => {}).finally(() => this.pending--);
    return result;
  }
  live(id) { const c = this.clients.get(id); return c && this.now() - c.seen <= this.grace; }
  reset(owner = null) { this.owner = owner; this.epoch = randomUUID(); this.seq = 0;for(const id of this.requests)this.requestedAt.set(id,this.now()); }
  expire() {
    if (this.owner && !this.live(this.owner)) this.reset();
    this.requests = this.requests.filter(id => this.live(id));
    for(const id of this.requestedAt.keys())if(!this.requests.includes(id))this.requestedAt.delete(id);
  }
  sweep() { return this.serial(() => {
    this.expire();const target=this.requests[0];
    if(this.online&&!this.maintenance&&!this.transitionPending&&target&&(!this.owner||this.now()-this.requestedAt.get(target)>=this.responseMs)){
      this.requests.shift();this.requestedAt.delete(target);this.reset(target);
    }
  }); }
  setOnline(value) { return this.serial(() => { this.online = value; if (!value) { this.reset(); this.requests = []; } }); }
  leave(id) { return this.serial(() => { this.clients.delete(id);this.decisions.delete(id); this.expire(); }); }
  checkMaintenance() { if (this.maintenance) throw new Rejected('APK 적용 중입니다',409); }
  request(id) {
    this.checkMaintenance();
    return this.serial(() => {
      this.checkMaintenance();
      this.expire();
      if (!this.online) throw new Rejected('device offline');
      if (!this.live(id)) throw new Rejected('session inactive', 401);
      this.decisions.delete(id);
      if (!this.owner) this.reset(id);
      else if (this.owner !== id && !this.requests.includes(id)) {this.requests.push(id);this.requestedAt.set(id,this.now());}
    });
  }
  owns(id) { this.expire(); if (this.owner !== id || !this.live(id)) throw new Rejected('control required', 403); }
  reject(id,target){
    this.checkMaintenance();
    return this.serial(()=>{
      this.checkMaintenance();this.owns(id);
      if(this.transitionPending)throw new Rejected('control transfer in progress');
      if(!this.requests.includes(target)||!this.live(target))throw new Rejected('live requester required');
      this.requests=this.requests.filter(value=>value!==target);
      this.requestedAt.delete(target);
      this.decisions.set(target,{status:'rejected',byName:this.clients.get(id).name,at:this.now()});
    });
  }
  async transfer(id, target) {
    this.checkMaintenance();
    this.owns(id);
    if (this.transitionPending) throw new Rejected('control transfer in progress');
    if (!this.requests.includes(target) || !this.live(target)) throw new Rejected('live requester required');
    this.transitionPending = true;
    this.epoch = randomUUID(); this.seq = 0;
    try { return await this.serial(() => {
      this.owns(id);
      if (!this.requests.includes(target) || !this.live(target)) throw new Rejected('live requester required');
      this.requests = this.requests.filter(v => v !== target); this.reset(target);
    }); } finally { this.transitionPending = false; }
  }
  async release(id) {
    this.checkMaintenance();
    this.owns(id);
    if (this.transitionPending) throw new Rejected('control transfer in progress');
    this.transitionPending = true; this.epoch = randomUUID(); this.seq = 0;
    try { return await this.serial(() => { this.owns(id); this.reset(); }); }
    finally { this.transitionPending = false; }
  }
  input(id, packet) {
    this.checkMaintenance();
    return this.serial(async () => {
      this.checkMaintenance();
      if (this.transitionPending) throw new Rejected('control transfer in progress');
      this.owns(id);
      if (!this.online) throw new Rejected('device offline');
      if (packet?.epoch !== this.epoch) throw new Rejected('stale lease');
      if (!Number.isSafeInteger(packet.seq) || packet.seq <= this.seq) throw new Rejected('invalid sequence');
      const age = this.now() - packet.sentAt;
      if (!Number.isFinite(age) || age > 3000 || age < -1000) throw new Rejected('expired input');
      const action = validateAction(packet.action);
      this.seq = packet.seq;
      try { await this.sendInput(action, packet); }
      catch { this.online = false; this.reset(); this.requests = []; throw new Rejected('device input failed; control revoked', 503); }
    });
  }
  beginMaintenance(id,epoch) {
    this.checkMaintenance(); this.owns(id);
    if(!this.online || this.transitionPending || epoch !== this.epoch) throw new Rejected('제어권 또는 연결 상태가 바뀌었습니다',409);
    this.maintenance=true; this.reset(id);
    return this.tail;
  }
  endMaintenance() { this.maintenance=false; this.reset(this.live(this.owner)?this.owner:null); }
  state(id) {
    return {
      requestResult:this.decisions.get(id)||null, maintenance: this.maintenance, you: id, owner: this.owner, epoch: this.epoch, online: this.online, now: this.now(),
      lastSeq: this.owner === id ? this.seq : 0,
      peers: [...this.clients].filter(([key]) => this.live(key)).map(([key, v]) => ({ id: key, name: v.name })),
      requests: this.requests.filter(key => this.live(key)),
      requestDeadline:this.requests[0]?(this.requestedAt.get(this.requests[0])+this.responseMs):null,
    };
  }
}
