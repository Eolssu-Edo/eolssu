// scrcpy 5: private preamble, H.264 codec id, session and media packets.
export class VideoPackets {
  constructor({deviceHeader=false,onSession=()=>{},onPacket=()=>{}}={}) {
    this.skip=deviceHeader?65:0;this.codec=false;this.bytes=new Uint8Array();this.onSession=onSession;this.onPacket=onPacket;
  }
  push(chunk) {
    if(this.skip){const n=Math.min(this.skip,chunk.length);this.skip-=n;chunk=chunk.subarray(n);}
    if(!chunk.length)return;
    if(this.bytes.length+chunk.length>8*1024*1024)throw Error('video buffer limit');
    const bytes=new Uint8Array(this.bytes.length+chunk.length);bytes.set(this.bytes);bytes.set(chunk,this.bytes.length);this.bytes=bytes;
    let at=0;const view=new DataView(bytes.buffer);
    if(!this.codec){if(bytes.length<4)return;if(view.getUint32(0)!==0x68323634)throw Error('unsupported video codec');this.codec=true;at=4;}
    while(bytes.length-at>=12){
      const flags=view.getBigUint64(at);
      if(flags&(1n<<63n)){
        const width=view.getUint32(at+4),height=view.getUint32(at+8);
        if(!width||!height||width>8192||height>8192)throw Error('invalid video geometry');
        this.onSession({width,height,raw:bytes.slice(at,at+12)});at+=12;continue;
      }
      const size=view.getUint32(at+8);if(size>4*1024*1024)throw Error('video packet limit');
      if(bytes.length-at<12+size)break;
      const raw=bytes.slice(at,at+12+size);
      this.onPacket({raw,payload:raw.subarray(12),timestamp:Number(flags&((1n<<61n)-1n)),config:!!(flags&(1n<<62n)),key:!!(flags&(1n<<61n))});at+=12+size;
    }
    this.bytes=bytes.slice(at);
  }
}
export function avcCodec(bytes){
  for(let i=0;i+6<bytes.length;i++){
    let n=0;if(bytes[i]===0&&bytes[i+1]===0&&bytes[i+2]===1)n=i+3;
    else if(bytes[i]===0&&bytes[i+1]===0&&bytes[i+2]===0&&bytes[i+3]===1)n=i+4;
    if(n&&(bytes[n]&31)===7&&n+3<bytes.length)return 'avc1.'+Array.from(bytes.slice(n+1,n+4),x=>x.toString(16).padStart(2,'0')).join('');
  }
  throw Error('missing AVC configuration');
}
