import {createHmac, timingSafeEqual} from 'node:crypto';

export function verifyWebhook(raw,signature,secret,liveMode,now=Date.now()) {
  if (!secret || !signature || typeof raw !== 'string') return false;
  const parts=Object.fromEntries(signature.split(',').map(p=>p.trim().split('=',2)));
  const t=Number(parts.t);
  if (!Number.isSafeInteger(t) || Math.abs(now-t*1000)>5*60*1000) return false;
  const actual=liveMode?parts.li:parts.te;
  if (!actual || !/^[0-9a-f]{64}$/i.test(actual)) return false;
  const expected=createHmac('sha256',secret).update(parts.t+'.'+raw).digest('hex');
  return timingSafeEqual(Buffer.from(actual,'hex'),Buffer.from(expected,'hex'));
}

export async function paymongo(method,path,data) {
  if(!process.env.PAYMONGO_SECRET_KEY)throw Object.assign(new Error('PayMongo secret key not configured.'),{status:503});
  const controller=new AbortController();
  const timeout=setTimeout(()=>controller.abort(),12000);
  try {
    const res=await fetch('https://api.paymongo.com'+path,{
      method,signal:controller.signal,
      headers:{'Authorization':'Basic '+Buffer.from(process.env.PAYMONGO_SECRET_KEY+':').toString('base64'), 'Content-Type':'application/json','Accept':'application/json'},
      ...(data?{body:JSON.stringify(data)}:{})
    });
    const result=await res.json();
    if(!res.ok || !result.data)throw Object.assign(new Error('Payment gateway unavailable.'),{status:502});
    return result.data;
  }finally{clearTimeout(timeout);}
}

export function paidPayment(session, expected) {
  const payments=session?.attributes?.payments;
  if(!Array.isArray(payments)) return null;
  return payments.find(p=> {
    const a=p.attributes||p;
    return a.status==='paid' && a.currency==='PHP' && Number(a.amount)===expected;
  })||null;
}
