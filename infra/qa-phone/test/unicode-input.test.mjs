import {test} from 'node:test';import assert from 'node:assert/strict';import {mkdtemp,writeFile,readFile,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import {createHash} from 'node:crypto';import {EventEmitter} from 'node:events';import {PassThrough,Writable} from 'node:stream';import {pasteUnicode} from '../unicode-input.mjs';
const sha=b=>createHash('sha256').update(b).digest('hex');
test('Korean and shell punctuation reach clipboard via stdin only, then fixed paste key',async t=>{
 const root=await mkdtemp(join(tmpdir(),'qa290-text-'));t.after(()=>rm(root,{recursive:true,force:true}));
 const file=join(root,'bright-screen.jar'),bytes=Buffer.from('fixture'),source=await readFile(new URL('../android/UnicodeText.java',import.meta.url));
 await writeFile(file,bytes);await writeFile(join(root,'bright-screen.json'),JSON.stringify({sha256:sha(bytes),textSourceSha256:sha(source)}));
 const calls=[],argv=[];let payload='';const phone={adb:'fixture',serial:'fixture',port:5037,async run(args){calls.push(args);return '';}};
 const text='한글 입력; $(id) & "기호"';
 await pasteUnicode(phone,file,text,{launch(...args){argv.push(args);const child=new EventEmitter();child.stdout=new PassThrough();child.stderr=new PassThrough();child.kill=()=>{};child.stdin=new Writable({write(chunk,encoding,done){payload+=chunk.toString();child.stdout.write('READY\n');child.emit('exit',0);done();}});return child;}});
 assert.equal(Buffer.from(payload.trim(),'base64').toString('utf8'),text);assert.equal(JSON.stringify(argv).includes(text),false);
 assert.deepEqual(calls[1],['shell','input','keyevent','KEYCODE_PASTE']);assert.deepEqual(calls.at(-1).slice(0,3),['shell','rm','-f']);
});
