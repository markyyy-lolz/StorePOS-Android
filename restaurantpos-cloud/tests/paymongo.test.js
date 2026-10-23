import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHmac} from 'node:crypto';
import {paidPayment,verifyWebhook} from '../paymongo.js';

test('webhook rejects unsigned and replayed events',()=>{
  const raw='{"data":{"attributes":{"type":"checkout_session.payment.paid"}}}';
  const t=Math.floor(Date.now()/1000);
  const hash=createHmac('sha256','whsk_test').update(`${t}.${raw}`).digest('hex');
  assert.equal(verifyWebhook(raw,`t=${t},te=${hash},li=`, 'whsk_test',false),true);
  assert.equal(verifyWebhook(raw,`t=${t},te=${hash},li=`, 'bad_secret',false),false);
  assert.equal(verifyWebhook(raw,`t=${t-7200},te=${hash},li=`, 'whsk_test',false),false);
  assert.equal(verifyWebhook(raw,`t=${t},te=${hash},li=`, 'whsk_test',true),false);
});

test('only matching paid PHP payment qualifies',()=>{
  const session={attributes:{payments:[
    {id:'p1',attributes:{status:'failed',currency:'PHP',amount:10000}},
    {id:'p2',attributes:{status:'paid',currency:'PHP',amount:10001}},
    {id:'p3',attributes:{status:'paid',currency:'PHP',amount:10000}}]}};
  assert.equal(paidPayment(session,10000).id,'p3');
  assert.equal(paidPayment(session,30000),null);
});
