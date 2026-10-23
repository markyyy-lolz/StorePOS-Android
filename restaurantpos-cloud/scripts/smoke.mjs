#!/usr/bin/env node
// Read-only HTTP smoke tests: safe to run against an existing deployment.
const base=(process.env.TARGET_URL||process.env.PUBLIC_BASE_URL||'').replace(/\/$/,'');
if(!/^https:\/\/[^\s]+$/.test(base)){
 console.error('Set TARGET_URL=https://your-service.example'); process.exit(2);
}
const results=[];
async function check(name,path,expected,validate=()=>true){
 try{
  const response=await fetch(new URL(path,base),{signal:AbortSignal.timeout(12000),redirect:'manual'});
  const text=await response.text();let json;try{json=JSON.parse(text);}catch{json={};}
  const ok=response.status===expected && validate(response,json);
  results.push({name,ok,status:response.status});
 }catch(e){results.push({name,ok:false,status:String(e.message)});}
}
await check('Database health','/api/health',200,(_r,data)=>data.status==='ok'&&data.database==='connected');
await check('Unauthorized session blocked','/api/me',401,(_r,data)=>Boolean(data.error));
await check('Unauthorized menu blocked','/api/menu',401,(_r,data)=>Boolean(data.error));
await check('Secure response headers','/',200,(r)=>r.headers.get('x-content-type-options')==='nosniff'&&r.headers.get('x-frame-options')==='DENY');
for(const x of results)console.log(`${x.ok?'PASS':'FAIL'} ${x.name} (${x.status})`);
if(results.some(x=>!x.ok))process.exitCode=1;
