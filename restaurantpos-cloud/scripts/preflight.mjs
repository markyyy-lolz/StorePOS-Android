#!/usr/bin/env node
import '../config.js';
const issues=[],warnings=[],success=[];
const fail=(x)=>issues.push(x), warn=(x)=>warnings.push(x), ok=(x)=>success.push(x);
const prod=process.env.NODE_ENV==='production';
prod?ok('NODE_ENV=production'):warn('NODE_ENV is not production; do not serve live transactions');
try {
 const db=new URL(process.env.DATABASE_URL);
 if(!['postgres:','postgresql:'].includes(db.protocol))fail('DATABASE_URL must be a PostgreSQL URI');
 else if(!db.username||!db.password||!db.hostname)fail('DATABASE_URL missing hostname or credentials');
 else if(db.searchParams.get('sslmode')!=='require'&&db.searchParams.get('sslmode')!=='verify-full')warn('Set PostgreSQL sslmode=require or verify-full');
 else ok('PostgreSQL URL format and TLS parameter');
 if(db.hostname.endsWith('.neon.tech'))ok('Neon database host');
 else warn('Non-Neon PostgreSQL endpoint; check database isolation');
} catch { fail('DATABASE_URL invalid'); }
const base=process.env.PUBLIC_BASE_URL||'';
if(!base)warn('PUBLIC_BASE_URL not set; gateway checkout disabled');
else {try{const u=new URL(base);if(u.protocol!=='https:'||u.username||u.password||u.search||u.hash||u.pathname!=='/')fail('PUBLIC_BASE_URL must be public HTTPS origin only');else ok('Public HTTPS base URL format');}catch{fail('PUBLIC_BASE_URL invalid');}}
const pay=process.env.PAYMONGO_SECRET_KEY||'',wh=process.env.PAYMONGO_WEBHOOK_SECRET||'';
if(Boolean(pay)!==Boolean(wh))fail('PayMongo key and webhook secret must be configured together');
else if(pay&&wh){
 if(!/^sk_(test|live)_/.test(pay))fail('Unrecognized PayMongo key prefix');
 else if(prod&&pay.startsWith('sk_test_'))warn('PayMongo test mode configured; real money is NOT collected');
 else if(pay.startsWith('sk_live_'))ok('Live PayMongo key present (live payment tests still required)');
 else ok('PayMongo test key present');
 if(!/^whsk_/.test(wh))warn('Verify PayMongo webhook signing secret');
 if(!base)fail('PayMongo requires PUBLIC_BASE_URL');
} else warn('PayMongo keys absent; digital payments unavailable');
if(process.env.OWNER_PASSWORD)warn('OWNER_PASSWORD is set: remove it after one-time owner bootstrap');
for(const s of success)console.log('PASS  '+s);
for(const s of warnings)console.log('WARN  '+s);
for(const s of issues)console.log('FAIL  '+s);
console.log(`Preflight: ${success.length} pass, ${warnings.length} warning(s), ${issues.length} failure(s)`);
process.exitCode=issues.length?1:0;
