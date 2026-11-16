import {DatabaseSync} from 'node:sqlite';
import {mkdtemp,writeFile,unlink,rmdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

export async function decodeAppDatabase(bytes,after=0,previousStream=null) {
  if(!Buffer.isBuffer(bytes)||bytes.length<100||bytes.length>16*1024*1024||
    bytes.subarray(0,16).toString()!=='SQLite format 3\0'||bytes[18]!==1||bytes[19]!==1)throw Error('Unsupported app log database');
  const directory=await mkdtemp(join(tmpdir(),'eolssu-qa-log-')),file=join(directory,'snapshot.db');
  let db;
  try{
    await writeFile(file,bytes,{mode:0o600});
    db=new DatabaseSync(file,{readOnly:true,allowExtension:false});
    if(db.prepare('PRAGMA quick_check(1)').get()?.quick_check!=='ok')throw Error('Inconsistent app log snapshot');
    const tables=db.prepare("SELECT name FROM sqlite_schema WHERE type='table' AND name IN ('state','events')").all();
    if(tables.length!==2)throw Error('Unsupported app log schema');
    const state=db.prepare('SELECT stream_id,ack,last_seq FROM state').all();
    if(state.length!==1)throw Error('Invalid app log state');
    const {stream_id,ack,last_seq:end}=state[0];
    if(!/^[a-f0-9]{32}$/.test(stream_id)||!Number.isSafeInteger(ack)||!Number.isSafeInteger(end)||ack<0||end<ack)throw Error('Invalid app log cursor');
    const max=db.prepare('SELECT MAX(seq) AS seq FROM events').get().seq;
    if((end>ack&&max!==end)||(end===ack&&max!==null))throw Error('Incomplete app log commit');
    const start=Math.max(ack,stream_id===previousStream?after:ack);
    if(start>end)throw Error('App log stream moved backwards');
    const rows=db.prepare('SELECT seq,event FROM events WHERE seq>? AND seq<=? ORDER BY seq LIMIT 1600').all(start,end);
    let expected=start;
    const events=rows.map(row=>{
      if(row.seq!==++expected||typeof row.event!=='string'||Buffer.byteLength(row.event)>2048)throw Error('App log sequence gap');
      const event=JSON.parse(row.event);
      if(!event||typeof event!=='object'||Array.isArray(event))throw Error('Invalid app log event');
      return {seq:row.seq,event};
    });
    return {stream_id,ack,end,events,after:start};
  }finally{
    db?.close();
    await unlink(file).catch(()=>{});
    await rmdir(directory);
  }
}

// Two equal byte copies are required after a change. Inconsistent in-flight writes
// are retried by the next poll; no live application database is opened for writing.
export function appDatabaseReader(phone) {
  let confirmed=null;
  return async(after,stream)=>{
    const first=await phone.readPerformanceLog();
    if(!confirmed?.equals(first)){
      const second=await phone.readPerformanceLog();
      if(!first.equals(second))throw Error('App log database is changing');
      confirmed=second;
    }
    return decodeAppDatabase(confirmed,after,stream);
  };
}
