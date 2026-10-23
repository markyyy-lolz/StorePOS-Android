import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {readRawBody} from '../request-body.js';
import {cashSettlementDecision} from '../payment-policy.js';

test('byte-exact PayMongo webhook body survives split multibyte UTF-8 chunks',async()=>{
 const body=Buffer.from(JSON.stringify({memo:'Pusô à la carte — 你好',centavos:25000}),'utf8');
 const incoming=Readable.from(Array.from(body).map(x=>Buffer.from([x])));
 assert.equal(await readRawBody(incoming,10000),body.toString('utf8'));
});
test('request body limit enforced in bytes and returns HTTP 413',async()=>{
 await assert.rejects(()=>readRawBody(Readable.from([Buffer.alloc(40000),Buffer.alloc(40001)]),80000),e=>e.status===413);
});
test('cash settlement blocked while checkout session pending',()=>{
 assert.equal(cashSettlementDecision('open','pending'),'gateway_review');
 assert.equal(cashSettlementDecision('open','conflict'),'gateway_review');
 assert.equal(cashSettlementDecision('open','paid'),'gateway_review');
 assert.equal(cashSettlementDecision('open',null),'allowed');
 assert.equal(cashSettlementDecision('paid',null),'already_paid');
 assert.equal(cashSettlementDecision('cancelled',null),'not_open');
});
