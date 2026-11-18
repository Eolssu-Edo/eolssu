import {createHmac,randomBytes,timingSafeEqual} from 'node:crypto';
import {createSocket} from 'node:dgram';

const PROTOCOL = 'qa-adb-port-v1';
const PORT = 31483;

const sign = (key,message) => createHmac('sha256',key).update(message).digest('hex');

export function createWirelessPortHint(key,{port=PORT,timeout=350}={}) {
  if(!/^[0-9a-f]{64}$/.test(key))throw Error('Invalid wireless port hint key');
  return async host => {
    if(!/^(?:10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.)/.test(host))return null;
    const nonce=randomBytes(16).toString('hex');
    const request=`${PROTOCOL} ${nonce}`;
    return new Promise(resolve=>{
      const socket=createSocket('udp4');
      const finish=value=>{clearTimeout(timer);socket.close();resolve(value);};
      const timer=setTimeout(()=>finish(null),timeout);
      socket.once('error',()=>finish(null));
      socket.on('message',(response,sender)=>{
        if(sender.address!==host||sender.port!==port||response.length>220)return;
        const fields=response.toString('ascii').split(' ');
        if(fields.length!==4||fields[0]!==PROTOCOL||fields[1]!==nonce||!/^[0-9a-f]{64}$/.test(fields[3]))return;
        const message=fields.slice(0,3).join(' ');
        if(!timingSafeEqual(Buffer.from(fields[3],'hex'),Buffer.from(sign(key,message),'hex')))return;
        if(fields[2]===''){finish([]);return;}
        const values=fields[2].split(',');
        if(values.length>16||values.some(value=>!/^[1-9]\d{0,4}$/.test(value)||Number(value)>65535))return;
        finish([...new Set(values.map(Number))]);
      });
      socket.send(`${request} ${sign(key,request)}`,port,host);
    });
  };
}
